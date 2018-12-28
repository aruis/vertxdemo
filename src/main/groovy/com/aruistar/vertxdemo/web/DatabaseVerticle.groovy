package com.aruistar.vertxdemo.web


import groovy.sql.Sql
import groovy.util.logging.Slf4j
import io.reactiverse.pgclient.PgClient
import io.reactiverse.pgclient.PgPool
import io.reactiverse.pgclient.PgPoolOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.eventbus.EventBus

@Slf4j
class DatabaseVerticle extends AbstractVerticle {

    PgPoolOptions pgOptions
    PgPool pgPool
    Sql db

    def channelName = 'flunk'
    EventBus eb

    @Override
    void start() throws Exception {
        eb = vertx.eventBus()

        def port = config().getInteger("port", 5432)
        def host = config().getString("host")
        def database = config().getString("database")
        def user = config().getString("user")
        def password = config().getString("password")
        def maxSize = config().getInteger("maxsize")

        pgOptions = new PgPoolOptions()
                .setPort(port)
                .setHost(host)
                .setDatabase(database)
                .setUser(user)
                .setPassword(password)
                .setMaxSize(maxSize)
                .setCachePreparedStatements(true)

        pgPool = PgClient.pool(vertx, pgOptions)

        pgPool.getConnection {
            def conn = it.result()

            conn.notificationHandler({ notification ->
                log.info("Received ${notification.payload} on channel ${notification.channel}")
                eb.send("flunk", notification.payload)
            })

            conn.preparedQuery("LISTEN $channelName", { ar ->
                log.info("Subscribed to channel @ $channelName")
            })


        }

    }
}
