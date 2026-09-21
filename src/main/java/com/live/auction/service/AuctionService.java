package com.live.auction.service;

import com.live.auction.api.mapper.AuctionMapper;
import com.live.auction.domain.model.Auction;
import com.live.auction.domain.model.Bid;
import com.live.auction.domain.repository.AuctionRepo;
import com.live.auction.domain.repository.BidRepo;
import com.live.auction.grpc.*;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;


@RequiredArgsConstructor
@GrpcService
public class AuctionService extends LiveAuctionServiceGrpc.LiveAuctionServiceImplBase {
    private final AuctionRepo auctionRepo;
    private final BidRepo bidRepo;

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
                            Status.NOT_FOUND.withDescription("Auction not found. ID: " + request.getAuctionId()).asRuntimeException()
                    );
                }
        );
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
}
