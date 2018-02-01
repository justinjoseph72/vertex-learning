package com.justin.app.auction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuctionValidator {
    private final AuctionRepository repository;

    public AuctionValidator(final AuctionRepository repository) {
        this.repository = repository;
    }

    public boolean validate(Auction auction) {
        log.info("staring the validation for {}",auction.toString());
        Auction auctionDatabase = repository.getById(auction.getId())
                .orElseThrow(() -> new AuctionNotFoundException(auction.getId()));
        return auctionDatabase.getPrice().compareTo(auction.getPrice()) == -1;
    }
}
