package com.live.auction.service;

import com.live.auction.api.mapper.AuctionMapper;
import com.live.auction.domain.model.Auction;
import com.live.auction.domain.model.Bid;
import com.live.auction.domain.repository.AuctionRepo;
import com.live.auction.domain.repository.BidRepo;
import com.live.auction.grpc.*;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Stream;


@RequiredArgsConstructor
@GrpcService
public class AuctionService extends LiveAuctionServiceGrpc.LiveAuctionServiceImplBase {
    private final AuctionRepo auctionRepo;
    private final BidRepo bidRepo;

    private final Map<String, Set<StreamObserver<LiveAuctionUpdate>>> activeRooms = new ConcurrentHashMap<>();

    @Override
    public void createAuction(CreateAuctionRequest auctionRequest, StreamObserver<AuctionResponse> responseObserver){
        Auction auction = auctionRepo.save(AuctionMapper.buildAuction(auctionRequest));
        responseObserver.onNext(AuctionMapper.buildAuctionResponse(auction));
        responseObserver.onCompleted();

    }

    @Override
    public void getAuctionDetails(AuctionRequest request, StreamObserver<AuctionDetailResponse> responseObserver) {
        auctionRepo.findById(request.getAuctionId()).ifPresentOrElse(
                auction -> {
                    responseObserver.onNext(AuctionMapper.buildAuctionDetailResponse(auction));
                    responseObserver.onCompleted();
                }, () -> {
                    responseObserver.onError(
                            Status.NOT_FOUND.withDescription("Auction not found: " + request.getAuctionId()).asRuntimeException()
                    );
                }
        );
    }

    @Override
    public void getLiveAuctions(Empty request, StreamObserver<LiveAuctionList> responseObserver) {
        try{
            com.live.auction.domain.model.Status status = com.live.auction.domain.model.Status.ACTIVE;

            List<AuctionDetailResponse> liveAuctions = auctionRepo.findAllByStatus(status).stream().map(AuctionMapper::buildAuctionDetailResponse).toList();

            LiveAuctionList response = LiveAuctionList.newBuilder().addAllAuctionDetail(liveAuctions).build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }catch (Exception e){
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getAuctionHistory(AuctionRequest request, StreamObserver<BidHistoryResponse> responseObserver) {
        // try-with-resources: When the loop finished, closes the DB cursor automatically
        try(Stream<Bid> bidStream = bidRepo.streamByAuctionIdOrderByCreatedAtAsc(request.getAuctionId())) {
            bidStream.forEach(bid -> {
                // The row read from the database is immediately sent over the network.
                BidHistoryResponse response = AuctionMapper.buildBidHistoryResponse(bid);
                responseObserver.onNext(response);

                // // Note: Once processed, the 'bid' object is eligible for garbage collection and is not retained in RAM.
            });
            responseObserver.onCompleted();
        }catch (Exception e){
            responseObserver.onError(
                    Status.INTERNAL.withDescription("Internal server error during retrieving the history.").asRuntimeException()
            );
        }
    }

    @Override
    public void watchAuctionRoom(AuctionRequest request, StreamObserver<LiveAuctionUpdate> responseObserver) {
        String auctionId = request.getAuctionId();

        // Add user to the room
        activeRooms.computeIfAbsent(auctionId, k-> ConcurrentHashMap.newKeySet()).add(responseObserver);

        // If the client network is failed, the mechanism that will clean this on the map
        // Prevents memory leaks
        Context.current().addListener(
                context -> removeObservers(auctionId, responseObserver),
                Executors.newSingleThreadExecutor()
        );

    }

    @Override
    @Transactional
    public void placeBid(CreateBidRequest request, StreamObserver<PlaceBidResponse> responseObserver) {
        String auctionId = request.getAuctionId();
        BigDecimal incomingAmount = BigDecimal.valueOf(request.getAmount());

        Auction auction = auctionRepo.findById(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != com.live.auction.domain.model.Status.ACTIVE) {
            sendBidResult(responseObserver, false, "Auction is not found or not active.");
            return;
        }

        if(incomingAmount.compareTo(auction.getCurrentHighestBid()) <= 0){
            sendBidResult(responseObserver, false, "Incoming amount should be greater than the highest request.");
            return;
        }

        // Update the auction's current highest bid then save
        auction.setCurrentHighestBid(incomingAmount);
        auctionRepo.save(auction);

        // Create a bid then save
        Bid newBid = createBid(auction, request, incomingAmount);
        bidRepo.save(newBid);

        // Broadcast the new state to the room
        broadcastNewBid(auction, newBid);

        sendBidResult(responseObserver, true, "Auction successfully placed.");
    }

    private void broadcastNewBid(Auction auction, Bid bid){
        Set<StreamObserver<LiveAuctionUpdate>> roomObservers = activeRooms.get(auction.getId());

        if(roomObservers != null && !roomObservers.isEmpty()){
            LiveAuctionUpdate update = AuctionMapper.buildLiveUpdate(auction, bid);
            for(StreamObserver<LiveAuctionUpdate> observer : roomObservers){
                try{
                    observer.onNext(update);
                }catch (Exception e){}
            }
        }
    }

    private void removeObservers(String auctionId, StreamObserver<LiveAuctionUpdate> responseObserver){
        Set<StreamObserver<LiveAuctionUpdate>> room = activeRooms.get(auctionId);
        if (room != null){
            room.remove(responseObserver);
            if(room.isEmpty()){
                activeRooms.remove(auctionId);
            }
        }
    }

    private void sendBidResult(StreamObserver<PlaceBidResponse> responseObserver, boolean success, String message) {
        responseObserver.onNext(PlaceBidResponse.newBuilder().setIsSuccessful(success).setMessage(message).build());
        responseObserver.onCompleted();
    }

    private Bid createBid(Auction auction, CreateBidRequest request, BigDecimal incomingAmount){
        return Bid.builder()
                .auction(auction)
                .username(request.getUserId())
                .amount(incomingAmount)
                .createdAt(Instant.now())
                .build();
    }
}
