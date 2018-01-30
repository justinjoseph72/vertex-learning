package com.justin.app.wiki;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Launcher;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class WikiVerticle extends AbstractVerticle {

    private JDBCClient jdbcClient;
    private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

    public static void main(String[] args) {
        Launcher.main(new String[]{"run", WikiVerticle.class.getName(), "-ha"});
    }

    @Override
    public void start(final Future<Void> startFuture) throws Exception {
        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.setHandler(startFuture.completer());
    }

    private static final String SQL_CREATE_PAGES_TABLE = " create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
    private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
    private static final String SQL_CREATE_PAGE = " insert into Pages values (NULL,?,?)";
    private static final String SQL_SAVE_PAGE = "update Pages set Content =? where Id =?";
    private static final String SQL_ALL_PAGES = " select Name from Pages";
    private static final String SQL_DELETE_PAGE = "delete from Pages where Id =?";

    private Future<Void> prepareDatabase() {
        Future<Void> future = Future.future();
        jdbcClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", "jdbc:hsqldb:file:db/wiki")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30));

        jdbcClient.getConnection(ar -> {
            if (ar.failed()) {
                log.info("Could not open database connection", ar.cause());
                future.fail(ar.cause());
            } else {
                SQLConnection connection = ar.result();
                connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
                    connection.close();
                    if (create.failed()) {
                        log.error("Database preparation error", create.cause());
                        future.fail(create.cause());
                    } else {
                        future.complete();
                    }
                });
            }
        });
        return future;
    }

    private Future<Void> startHttpServer() {
        Future<Void> future = Future.future();
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);
        router.get("/").handler(this::indexHandler);
        router.get("/wiki/:page").handler(this::pageRenderingHandler);
        router.post().handler(BodyHandler.create());
        router.post("/save").handler(this::pageUpdateHandler);
        router.post("/create").handler(this::pageCreateHandler);
        router.post("/delete").handler(this::pageDeletionHandler);

        server.requestHandler(router::accept)
                .listen(8080, ar -> {
                    if (ar.succeeded()) {
                        log.info("HTTP server running or port 8080");
                        future.complete();
                    } else {
                        log.info("Could not start HTTP server", ar.cause());
                        future.fail(ar.cause());
                    }
                });
        return future;
    }


    private void indexHandler(final RoutingContext routingContext) {
        jdbcClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.query(SQL_ALL_PAGES, res -> {
                    connection.close();
                    if (res.succeeded()) {
                        List<String> pages = res.result()
                                .getResults()
                                .stream()
                                .map(json -> json.getString(0))
                                .sorted()
                                .collect(Collectors.toList());
                        routingContext.put("title", "Wiki home");
                        routingContext.put("pages", pages);
                        templateEngine.render(routingContext, "templates", "/index.ftl", ar -> {
                            if (ar.succeeded()) {
                                routingContext.response().putHeader("Content-Type", "text/html");
                                routingContext.response().end(ar.result());
                            } else {
                                routingContext.fail(ar.cause());
                            }
                        });
                    } else {
                        routingContext.fail(res.cause());
                    }
                });
            } else {
                routingContext.fail(car.cause());
            }
        });
    }

    private static final String EMPTY_PAGE_MARKDOWN = " # A new page \n" + "\n" +
            "Feel free to write in Markdown! \n";

    private void pageRenderingHandler(final RoutingContext routingContext) {
        String page = routingContext.request().getParam("page");
        jdbcClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.queryWithParams(SQL_GET_PAGE, new JsonArray().add(page), fetch -> {
                    connection.close();
                    if (fetch.succeeded()) {
                        JsonArray row = fetch.result().getResults()
                                .stream()
                                .findFirst()
                                .orElseGet(() -> new JsonArray().add(-1).add(EMPTY_PAGE_MARKDOWN));
                        Integer id = row.getInteger(0);
                        String rawContent = row.getString(1);
                        routingContext.put("title", page);
                        routingContext.put("id", id);
                        routingContext.put("newPage", fetch.result().getResults().size() == 0 ? "yes" : "no");
                        routingContext.put("rawContent", rawContent);
                        routingContext.put("content", Processor.process(rawContent));
                        routingContext.put("timestamp", new Date().toString());

                        templateEngine.render(routingContext, "templates", "/page.ftl", ar -> {
                            if (ar.succeeded()) {
                                routingContext.response().putHeader("Content-Type", "text/html");
                                routingContext.response().end(ar.result());
                            } else {
                                routingContext.fail(ar.cause());
                            }
                        });
                    } else {
                        routingContext.fail(fetch.cause());
                    }
                });
            } else {
                routingContext.fail(car.cause());
            }
        });
    }

    private void pageUpdateHandler(final RoutingContext routingContext) {

        String id = routingContext.request().getParam("id");
        String title = routingContext.request().getParam("title");
        String markdown = routingContext.request().getParam("markdown");
        boolean newPage = "yes".equals(routingContext.request().getParam("newPage"));
        jdbcClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                String sql = newPage ? SQL_CREATE_PAGE : SQL_SAVE_PAGE;
                JsonArray params = new JsonArray();
                if (newPage) {
                    params.add(title).add(markdown);
                } else {
                    params.add(markdown).add(id);
                }
                connection.updateWithParams(sql, params, res -> {
                    connection.close();
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(303);
                        routingContext.response().putHeader("Location", "/wiki/" + title);
                        routingContext.response().end();
                    } else {
                        routingContext.fail(res.cause());
                    }
                });
            } else {
                routingContext.fail(car.cause());
            }
        });

    }

    private void pageCreateHandler(final RoutingContext routingContext) {
        String pageName = routingContext.request().getParam("name");
        String location = "/wiki/" + pageName;
        if (pageName == null || pageName.isEmpty()) {
            location = "/";
        }
        routingContext.response().setStatusCode(303);
        routingContext.response().putHeader("Location", location);
        routingContext.response().end();
    }

    private void pageDeletionHandler(final RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        jdbcClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.updateWithParams(SQL_DELETE_PAGE, new JsonArray().add(id), res -> {
                    connection.close();
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(303);
                        routingContext.response().putHeader("Location", "/");
                        routingContext.response().end();
                    } else {
                        routingContext.fail(res.cause());
                    }
                });
            } else {
                routingContext.fail(car.cause());
            }
        });
    }


}
