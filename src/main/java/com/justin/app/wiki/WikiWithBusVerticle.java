package com.justin.app.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Launcher;

public class WikiWithBusVerticle extends AbstractVerticle {

    public static void main(String ... args){
        Launcher.main(new String[]{"run",WikiWithBusVerticle.class.getName(),"-ha"});
    }

    @Override
    public void start(final Future<Void> startFuture) throws Exception {
        Future<String> dbVerticleDeployment = Future.future();
        vertx.deployVerticle(new WikiDatabaseVerticle(),dbVerticleDeployment.completer());
        dbVerticleDeployment.compose(id ->{
            Future<String> httpVerticleDeployment = Future.future();
            vertx.deployVerticle(
                    "com.justin.app.wiki.HttpServerVerticle",
                    new DeploymentOptions().setInstances(2),
                    httpVerticleDeployment.completer()
            );
            return httpVerticleDeployment;
        }).setHandler(ar->{
           if(ar.succeeded()){
               startFuture.complete();
           }else {
               startFuture.fail(ar.cause());
           }
        });
    }
}
