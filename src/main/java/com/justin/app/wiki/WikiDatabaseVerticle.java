package com.justin.app.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
public class WikiDatabaseVerticle extends AbstractVerticle {

    public static final String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
    public static final String CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver_class";
    public static final String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";
    public static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";

    private static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
    private JDBCClient jdbcClient;

    private enum SqlQuery {
        CREATE_PAGES_TABLE,
        ALL_PAGES,
        GET_PAGE,
        CREATE_PAGE,
        SAVE_PAGE,
        DELETE_PAGE
    }

    public enum ErrorCodes {
        NO_ACTION_SPECIFIED,
        BAD_ACTION,
        DB_ERROR
    }

    private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();

    private void loadSqlQueries() throws IOException {
        String queriesFile = config().getString(CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE);
        InputStream queriesInputStream;
        if (queriesFile != null) {
            queriesInputStream = new FileInputStream(queriesFile);
        } else {
            queriesInputStream = getClass().getResourceAsStream("/db-queries.properties");
        }

        Properties queryProps = new Properties();
        queryProps.load(queriesInputStream);
        queriesInputStream.close();
        sqlQueries.put(SqlQuery.CREATE_PAGES_TABLE, queryProps.getProperty("create-pages-table"));
        sqlQueries.put(SqlQuery.ALL_PAGES, queryProps.getProperty("all-pages"));
        sqlQueries.put(SqlQuery.GET_PAGE, queryProps.getProperty("get-page"));
        sqlQueries.put(SqlQuery.CREATE_PAGE, queryProps.getProperty("create-page"));
        sqlQueries.put(SqlQuery.SAVE_PAGE, queryProps.getProperty("save-page"));
        sqlQueries.put(SqlQuery.DELETE_PAGE, queryProps.getProperty("delete-page"));
    }


    @Override
    public void start(final Future<Void> startFuture) throws Exception {
        loadSqlQueries();
        jdbcClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:file:db/wiki"))
                .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, "org.hsqldb.jdbcDriver"))
                .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30))
        );

        jdbcClient.getConnection(ar -> {
            if (ar.failed()) {
                log.error("Could not open a database connection", ar.cause());
                startFuture.fail(ar.cause());
            } else {
                SQLConnection connection = ar.result();
                connection.execute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), create -> {
                    connection.close();
                    if (create.failed()) {
                        log.error("DAtabase preparation failed ", create.cause());
                        startFuture.fail(ar.cause());
                    } else {
                        vertx.eventBus().consumer(config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue"), this::onMessage);
                        startFuture.complete();
                    }
                });
            }

        });
    }


    private void onMessage(final Message<JsonObject> message) {
        if (!message.headers().contains("action")) {
            log.info("No actions header specified in the request");
            message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
            return;
        }
        String action = message.headers().get("action");
        switch (action) {
            case "all-pages":
                fetchAllPages(message);
                break;
            case "get-page":
                fetchPage(message);
                break;
            case "create-page":
                createPage(message);
                break;
            case "save-page":
                savePage(message);
                break;
            case "delete-page":
                deletePage(message);
                break;
            default:
                message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action:" + action);
        }
    }

    private void fetchAllPages(final Message<JsonObject> message) {
        jdbcClient.query(sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
            if (res.succeeded()) {
                List<String> pages = res.result()
                        .getResults()
                        .stream()
                        .map(json -> json.getString(0))
                        .sorted()
                        .collect(Collectors.toList());
                message.reply(new JsonObject().put("pages", new JsonArray(pages)));
            } else {
                reportQueryError(message, res.cause());
            }
        });
    }


    private void fetchPage(final Message<JsonObject> message) {
        String pageName = message.body().getString("page");
        JsonArray params = new JsonArray().add(pageName);
        jdbcClient.queryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), params, res -> {
            if (res.succeeded()) {
                JsonObject response = new JsonObject();
                ResultSet resultSet = res.result();
                if (resultSet.getNumRows() == 0) {
                    response.put("found", false);
                } else {
                    response.put("found", true);
                    resultSet.getResults().stream().forEach(row -> {
                        response.put("id", row.getInteger(0));
                        response.put("rawContent", row.getString(1));
                    });
                }
                message.reply(response);
            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void createPage(final Message<JsonObject> message) {
        JsonObject request = message.body();
        JsonArray data = new JsonArray()
                .add(request.getString("title"))
                .add(request.getString("markdown"));
        jdbcClient.updateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), data, res -> {
            if (res.succeeded()) {
                message.reply("ok");
            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void savePage(final Message<JsonObject> message) {
        JsonObject request = message.body();
        JsonArray data = new JsonArray()
                .add(request.getString("id"))
                .add(request.getString("markdown"));
        jdbcClient.updateWithParams(sqlQueries.get(SqlQuery.SAVE_PAGE), data, res -> {
            if (res.succeeded()) {
                message.reply("ok");
            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void deletePage(final Message<JsonObject> message) {
        JsonArray data = new JsonArray().add(message.body().getString("id"));
        jdbcClient.updateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), data, res -> {
            if (res.succeeded()) {
                message.reply("ok");
            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void reportQueryError(final Message<JsonObject> message, final Throwable cause) {
        message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
    }

}
