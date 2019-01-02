package com.aruistar.vertxdemo.web

import groovy.util.logging.Slf4j
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.bridge.PermittedOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.BridgeOptions
import io.vertx.ext.web.handler.sockjs.SockJSHandler

@Slf4j
class HttpVerticle extends AbstractVerticle {


    static void main(String[] args) {
        Vertx.vertx().deployVerticle(new HttpVerticle(), new DeploymentOptions([config: [
                "http": [
                        "port": 8899
                ],
                "db"  : [
                        "host"    : "127.0.0.1",
                        "database": "studypg",
                        "user"    : "postgres",
                        "password": "",
                        "maxsize" : 6
                ]
        ]]))
    }

    @Override
    void start() throws Exception {

        boolean usingNative = vertx.isNativeTransportEnabled()

        log.info("Running with native: " + usingNative)

        def httpConfig = config().getJsonObject("http", new JsonObject([port: 8899]))
        def dbConfig = config().getJsonObject("db")

        if (!dbConfig) {
            dbConfig = [
                    "host"    : "127.0.0.1",
                    "database": "studypg",
                    "user"    : "postgres",
                    "password": "",
                    "maxsize" : 6
            ]
        }

        vertx.deployVerticle(new DatabaseVerticle(), new DeploymentOptions([config: dbConfig]))


        Router router = Router.router(vertx);
        def port = httpConfig.getInteger("port", 8899)

        // Allow events for the designated addresses in/out of the event bus bridge
        BridgeOptions opts = new BridgeOptions()
                .addOutboundPermitted(new PermittedOptions().setAddress("flunk"));

        // Create the event bus bridge and add it to the router.
        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/messagebus/*").handler(ebHandler)

        // Create a router endpoint for the static content.
        router.route().handler(StaticHandler.create());

        // Start the web server and tell it to use the router to handle requests.
        vertx.createHttpServer().requestHandler(router.&accept)
                .listen(port, { ar ->
            if (ar.succeeded()) {
                log.info("server is running on port " + port)
            } else {
                log.error("Could not start a HTTP server", ar.cause())
            }

        })
    }
}
