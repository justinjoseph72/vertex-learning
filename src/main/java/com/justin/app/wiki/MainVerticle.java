package com.justin.app.wiki;

import com.justin.app.highAvaliability.VertexHighAvailability;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Launcher;

public class MainVerticle extends AbstractVerticle {

    public static void main(String[] args) {
        Launcher.main(new String[]{"run", MainVerticle.class.getName(), "-ha"});
    }

    @Override
    public void start() throws Exception {
        vertx.createHttpServer()
                .requestHandler(req -> req.response().end("Yo YO"))
                .listen(8080);
    }
}
