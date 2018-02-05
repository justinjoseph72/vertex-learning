package com.justin.app.auction;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Launcher;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuctionService extends AbstractVerticle {

    /*public static void main(String... args){
        Launcher.main(new String[] {"run",AuctionService.class.getName(),"-ha"});
    }*/

    @Override
    public void start() {
        Router router = Router.router(vertx);
        router.route("/eventbus/*").handler(eventBusHandler());
        router.mountSubRouter("/api", auctionApiRouter());
        router.route().failureHandler(errorHandler());
        router.route().handler(staticHandler());

        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
    }

    private StaticHandler staticHandler() {
        return StaticHandler.create().setCachingEnabled(false);
    }

    private ErrorHandler errorHandler() {
        return ErrorHandler.create();
    }

    private Router auctionApiRouter() {
        AuctionRepository repository = new AuctionRepository(vertx.sharedData());
        AuctionValidator validator = new AuctionValidator(repository);
        AuctionHandler handler = new AuctionHandler(repository,validator);
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route().handler(CorsHandler.create("*")
                .allowedMethod(HttpMethod.PATCH)
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedHeader("Access-Control-Allow-Method")
                .allowedHeader("Access-Control-Allow-Origin")
                .allowedHeader("Access-Control-Allow-Credentials")
                .allowedHeader("Content-Type")
                );
        router.route().consumes("application/json");
        router.route().produces("application/json");
        router.route("/auctions/:id").handler(handler::initAuctionInSharedData);
        router.get("/auctions/:id").handler(handler::handleGetAuction);
        router.get("/auctions/:id").handler(handler::handleChangeAuctionPrice);
        return router;
    }

    private SockJSHandler eventBusHandler() {
        BridgeOptions options = new BridgeOptions()
                .addOutboundPermitted(new PermittedOptions().setAddressRegex("auction\\.[0-9]+"));
        return SockJSHandler.create(vertx).bridge(options, event -> {
            if (event.type() == BridgeEventType.SOCKET_CREATED) {
                log.info("A socket was created");
            }
            event.complete(true);
        });
    }
}
