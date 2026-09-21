package com.live.auction.domain.repository;

import com.live.auction.domain.model.Auction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionRepo extends JpaRepository<Auction, String> {
}
