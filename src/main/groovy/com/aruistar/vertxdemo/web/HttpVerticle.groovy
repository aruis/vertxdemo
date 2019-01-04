package com.aruistar.vertxdemo.web

import groovy.util.logging.Slf4j
import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.sstore.ClusteredSessionStore

@Slf4j
class HttpVerticle extends AbstractVerticle {

    @Override
    void start() throws Exception {

        def httpConfig = config().getJsonObject("http", new JsonObject([port: 8899]))

        Router router = Router.router(vertx)
        def port = httpConfig.getInteger("port", 8899)

        def bodyHandler = BodyHandler.create()

        def opts = [
                inboundPermitteds : [
                        [
                                address: "chat.to.server"
                        ]
                ],
                outboundPermitteds: [
                        [
                                address: "chat.to.client"
                        ]
                ]
        ]

        // Create the event bus bridge and add it to the router.
        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/messagebus/*").handler(ebHandler)

        def store = ClusteredSessionStore.create(vertx)
//        LocalSessionStore.create(vertx)
        def sessionHandler = SessionHandler.create(store)
        def cookieHandler = CookieHandler.create()
        router.route("/nickname")
                .handler(cookieHandler)
                .handler(sessionHandler)

        router.post("/nickname")
                .handler(bodyHandler)
                .handler({ routingContext ->

            def response = routingContext.response()
            def nickname = routingContext.getBodyAsJson().nickname

            routingContext.session().put("nickname", nickname)

            response.end(nickname)
        })

        router.get("/nickname")
                .handler({ routingContext ->

            def response = routingContext.response()
            def nickname = routingContext.session().get("nickname")

            if (nickname)
                response.end(nickname)
            else
                response.end()
        })

        // Create a router endpoint for the static content.
        router.route().handler(StaticHandler.create());

        def eb = vertx.eventBus()

        eb.consumer("chat.to.server").handler({ message ->
            def body = message.body()
            body.date = new Date().format("yyyy-MM-dd HH:mm:ss:SSS")
            eb.publish("chat.to.client", body)
        })

        // Start the web server and tell it to use the router to handle requests.
        vertx.createHttpServer().requestHandler(router)
                .listen(port, { ar ->
            if (ar.succeeded()) {
                log.info("server is running on port " + port)
            } else {
                log.error("Could not start a HTTP server", ar.cause())
            }

        })
    }
}
