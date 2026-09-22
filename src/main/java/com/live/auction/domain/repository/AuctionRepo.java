package com.live.auction.domain.repository;

import com.live.auction.domain.model.Auction;
import com.live.auction.domain.model.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuctionRepo extends JpaRepository<Auction, String> {
    List<Auction> findAllByStatus(Status status);
}
