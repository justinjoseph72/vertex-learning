package com.justin.app.auction;

import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
public class AuctionRepository {

    private SharedData sharedData;

    public AuctionRepository(final SharedData sharedData) {
        this.sharedData = sharedData;
    }

    public Optional<Auction> getById(String auctionId) {
        log.info("inside repository getById");
        log.info("The auction id to fetch {}", auctionId);
        LocalMap<String, String> auctionSharedData = this.sharedData.getLocalMap(auctionId);
        return Optional.of(auctionSharedData)
                .filter(m -> !m.isEmpty())
                .map(this::convertToAuction);
    }

    public void save(Auction auction) {
        log.info("inside repository save");
        log.info("the auction to save is {}", auction);
        if (auction != null && auction.getId() != null) {
            LocalMap<String, String> auctionSharedData = this.sharedData.getLocalMap(auction.getId());
            auctionSharedData.put("id", auction.getId());
            auctionSharedData.put("price", auction.getPrice().toString());
        }
        log.info("repo save exit");
    }

    private Auction convertToAuction(final LocalMap<String, String> auctionMap) {
        String id = auctionMap.get("id");
        String price = auctionMap.get("price");
        log.info("The id is {} and price is {} while converting ", id, price);
        if (price == null) {
            log.info("returning auction object when price is null");
            return new Auction(id);
        }
        log.info("returning auction object when price is present");
        return new Auction(id, new BigDecimal(price));
    }
}
