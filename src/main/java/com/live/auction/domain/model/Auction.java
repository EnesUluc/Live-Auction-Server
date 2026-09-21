package com.live.auction.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "auctions")
public class Auction {
    @Id
    @UuidGenerator
    private String id;

    private String title;
    private String description;
    private BigDecimal startPrice;
    private BigDecimal currentHighestBid;
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    private Status status;
}
