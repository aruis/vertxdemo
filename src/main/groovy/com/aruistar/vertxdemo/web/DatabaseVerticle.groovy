package com.aruistar.vertxdemo.web


import groovy.util.logging.Slf4j
import io.reactiverse.pgclient.PgClient
import io.reactiverse.pgclient.PgPool
import io.reactiverse.pgclient.PgPoolOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.eventbus.EventBus

@Slf4j
class DatabaseVerticle extends AbstractVerticle {

    PgPool pgPool
    EventBus eb

    def channelName = 'flunk'

    @Override
    void start() throws Exception {
        eb = vertx.eventBus()

        pgPool = PgClient.pool(vertx, buildPgPoolOptions())
        brigeDB2Eventbus(pgPool, channelName)
    }

    PgPoolOptions buildPgPoolOptions() {
        def port = config().getInteger("port", 5432)
        def host = config().getString("host")
        def database = config().getString("database")
        def user = config().getString("user")
        def password = config().getString("password")
        def maxSize = config().getInteger("maxsize")

        new PgPoolOptions()
                .setPort(port)
                .setHost(host)
                .setDatabase(database)
                .setUser(user)
                .setPassword(password)
                .setMaxSize(maxSize)
                .setCachePreparedStatements(true)
    }

    def brigeDB2Eventbus(PgClient dbClient, String channel) {
        dbClient.getConnection {
            def conn = it.result()

            conn.notificationHandler({ notification ->
                log.info("Received ${notification.payload} on channel ${notification.channel}")
                eb.publish(channel, notification.payload)
            })

            conn.preparedQuery("LISTEN $channel", { ar ->
                log.info("Subscribed to channel @ $channel")
            })

        }
    }
}
