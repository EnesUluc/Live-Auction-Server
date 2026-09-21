package com.live.auction.domain.repository;

import com.live.auction.domain.model.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.stream.Stream;

public interface BidRepo extends JpaRepository<Bid, String> {
    Stream<Bid> streamByAuctionIdOrderByCreatedAtAsc(String auctionId);
}
