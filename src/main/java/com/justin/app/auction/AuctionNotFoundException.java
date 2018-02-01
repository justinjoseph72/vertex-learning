package com.justin.app.auction;

public class AuctionNotFoundException extends RuntimeException {

    public AuctionNotFoundException(final String id) {
        super("Auction not Found " + id);
    }
}
