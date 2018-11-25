package com.aruistar.vertxdemo.web

import groovy.util.logging.Slf4j
import io.vertx.core.AbstractVerticle
import io.vertx.core.eventbus.EventBus
import io.vertx.ext.bridge.PermittedOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.BridgeOptions
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.sstore.LocalSessionStore

@Slf4j
class HttpVerticle extends AbstractVerticle {

    @Override
    void start() throws Exception {
        Router router = Router.router(vertx);
        def port = config().getInteger("port", 7788)


        def store = LocalSessionStore.create(vertx)
        router.route().handler(CookieHandler.create())
        router.route().handler(SessionHandler.create(store))
        router.route("/set").handler({ routingContext ->

            def session = routingContext.session()
            session.put("foo", "bar")
            routingContext.response().end()

        })


        router.route("/get").handler({ routingContext ->

            def session = routingContext.session()
            routingContext.response().end(session.get("foo").toString())

        })

        // Allow events for the designated addresses in/out of the event bus bridge
        BridgeOptions opts = new BridgeOptions()
                .addOutboundPermitted(new PermittedOptions().setAddress("feed"));

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

        EventBus eb = vertx.eventBus()

        vertx.setPeriodic(1000l, { t ->
            // Create a timestamp string
            eb.send("feed", Thread.currentThread().name)
        });
    }
}
