package com.live.auction.api.mapper;

import com.google.protobuf.Timestamp;
import com.live.auction.domain.model.Auction;
import com.live.auction.domain.model.Bid;
import com.live.auction.domain.model.Status;
import com.live.auction.grpc.*;

import java.math.BigDecimal;
import java.time.Instant;

public class AuctionMapper {
    public static Auction buildAuction(CreateAuctionRequest request){
        BigDecimal startingPrice = BigDecimal.valueOf(request.getStartPrice());

        Instant endTime = request.hasEndTime()
                ? Instant.ofEpochSecond(request.getEndTime().getSeconds(), request.getEndTime().getNanos())
                : Instant.now().plusSeconds(43200);

        return Auction.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .startPrice(startingPrice)
                .endTime(endTime)
                .currentHighestBid(startingPrice)
                .status(Status.ACTIVE)
                .build();
    }
    public static AuctionResponse buildAuctionResponse(Auction auction){
        return AuctionResponse.newBuilder()
                .setAuctionId(auction.getId())
                .setStatus(AuctionStatus.valueOf(auction.getStatus().name()))
                .build();
    }

    public static AuctionDetailResponse buildAuctionDetailResponse(Auction auction) {
        Timestamp endTimeProto = buildProtoTimestamp(
                auction.getEndTime().getEpochSecond(), auction.getEndTime().getNano());


        return AuctionDetailResponse.newBuilder()
                .setAuctionId(auction.getId())
                .setAuctionTitle(auction.getTitle())
                .setAuctionDescription(auction.getDescription())
                .setStatus(AuctionStatus.valueOf(auction.getStatus().name()))
                .setEndTime(endTimeProto)
                .setCurrentHighestBid(auction.getCurrentHighestBid().doubleValue())
                .build();
    }

    public static BidHistoryResponse buildBidHistoryResponse(Bid bid) {
        Timestamp createdTimeProto = buildProtoTimestamp(
                bid.getCreatedAt().getEpochSecond(), bid.getCreatedAt().getNano());

        return BidHistoryResponse.newBuilder()
                .setBidderName(bid.getUsername())
                .setBidAmount(bid.getAmount().doubleValue())
                .setBidCreationTime(createdTimeProto)
                .build();
    }
    public static Timestamp buildProtoTimestamp(long seconds, int nano) {
        return Timestamp.newBuilder()
                .setSeconds(seconds)
                .setNanos(nano)
                .build();
    }

    public static LiveAuctionUpdate buildLiveUpdate(Auction auction, Bid bid) {
        return LiveAuctionUpdate.newBuilder()
                .setAuctionId(auction.getId())
                .setLeaderName(bid.getUsername())
                .setNewHighestBid(bid.getAmount().doubleValue())
                .setTimestamp(buildProtoTimestamp(Instant.now().getEpochSecond(), bid.getCreatedAt().getNano()))
                .build();
    }
}
