package com.justin.app.auction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode
public class Auction {
    private String id;
    private BigDecimal price;

    public Auction(final String id, final BigDecimal price) {
        this.id = id;
        this.price = price;
    }

    public Auction(final String auctionId) {
        this(auctionId, BigDecimal.ZERO);
    }
}
