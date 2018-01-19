package com.justin.app.highAvaliability;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Launcher;

import java.lang.management.ManagementFactory;

public class VertexHighAvailability extends AbstractVerticle {

    public static void main(String[] args){
        Launcher.main(new String[]{"run",VertexHighAvailability.class.getName(),"-ha"});
    }

    @Override
    public void start() throws Exception {
        vertx.createHttpServer().requestHandler(req->{
            final String name = ManagementFactory.getRuntimeMXBean().getName();
            req.response().end("I am be served by " +  name);
        }).listen(8080);
    }

}
