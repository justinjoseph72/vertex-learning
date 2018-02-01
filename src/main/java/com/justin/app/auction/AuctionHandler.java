package com.justin.app.auction;

import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
public class AuctionHandler {
    private final AuctionRepository repository;
    private final AuctionValidator validator;

    public AuctionHandler(final AuctionRepository repository, final AuctionValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public void initAuctionInSharedData(final RoutingContext routingContext) {
        log.info("inside init method");
        String auctionId = routingContext.request().getParam("id");
        Optional<Auction> auctionOptional = this.repository.getById(auctionId);
        if (!auctionOptional.isPresent()) {
            log.info("auction optional is not present so saving to shared data");
            Auction auction = new Auction(auctionId);
            log.info("the new created auction object is {}", auction.toString());
            log.info("going to save the auction with id {} and price {}", auction.getId(), auction.getPrice());
            this.repository.save(auction);

        }
        if (auctionOptional.isPresent()) {
            log.info("The auction optional fetched is {}", auctionOptional.get().toString());
        }
        routingContext.next();

    }

    public void handleGetAuction(final RoutingContext routingContext) {
        log.info("inside the get method ");
        String auctionId = routingContext.request().getParam("id");
        log.info("the auctionId is {} ", auctionId);
        Optional<Auction> auction = repository.getById(auctionId);
        if (auction.isPresent()) {
            log.info("auction object is present");
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(200)
                    .end(Json.encodePrettily(auction.get()));
        } else {
            log.info("auction object is NOT present");
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(400)
                    .end();
        }
    }

    public void handleChangeAuctionPrice(final RoutingContext routingContext) {
        log.info("inside patch handler ");
        String auctionId = routingContext.request().getParam("id");
        log.info("The auction id from the bid method is {}",auctionId);
        String price = routingContext.getBodyAsJson().getString("price");
        log.info("price from bid is {}",price);
        Auction auctionRequest = new Auction(auctionId,
                new BigDecimal(price));
        if (validator.validate(auctionRequest)) {
            this.repository.save(auctionRequest);
            routingContext.vertx().eventBus().publish("auction." + auctionId, routingContext.getBodyAsString());
            routingContext.response()
                    .setStatusCode(200)
                    .end();
        } else {
            routingContext.response().setStatusCode(422).end();
        }
    }
}
