package com.aruistar.vertxdemo.web

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.SharedMetricRegistries
import groovy.util.logging.Slf4j
import in.yunyul.vertx.console.base.WebConsoleRegistry
import in.yunyul.vertx.console.eventbus.EventBusConsolePage
import in.yunyul.vertx.console.httpclients.HttpClientsConsolePage
import in.yunyul.vertx.console.logging.LoggingConsolePage
import in.yunyul.vertx.console.metrics.MetricsConsolePage
import in.yunyul.vertx.console.pools.PoolsConsolePage
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.ext.bridge.PermittedOptions
import io.vertx.ext.dropwizard.MetricsService
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.BridgeOptions
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.sstore.ClusteredSessionStore
import io.vertx.ext.web.sstore.LocalSessionStore

@Slf4j
class HttpVerticle extends AbstractVerticle {


    static void main(String[] args) {
        Vertx.vertx().deployVerticle(new HttpVerticle(), new DeploymentOptions([config: [
                "http": [
                        "port": 8899
                ],
                "db"  : [
                        "host"    : "192.168.0.88",
                        "port"    : 54328,
                        "database": "miaosha",
                        "user"    : "postgres",
                        "password": "longruan2018",
                        "maxsize" : 6
                ]
        ]]))
    }

    @Override
    void start() throws Exception {

        boolean usingNative = vertx.isNativeTransportEnabled()

        log.info("Running with native: " + usingNative)

        def httpConfig = config().getJsonObject("http")
        def dbConfig = config().getJsonObject("db")

        vertx.deployVerticle(new DatabaseVerticle(), new DeploymentOptions([config: dbConfig]))


        Router router = Router.router(vertx);
        def port = httpConfig.getInteger("port", 8899)

        def store
        if (vertx.isClustered())
            store = ClusteredSessionStore.create(vertx)
        else
            store = LocalSessionStore.create(vertx)

        router.route().handler(CookieHandler.create())
        router.route().handler(SessionHandler.create(store))

        def eb = vertx.eventBus()

        MetricRegistry dropwizardRegistry = SharedMetricRegistries.getOrCreate(
                System.getProperty("vertx.metrics.options.registryName") // or use hardcoded name
        )
        MetricsService metricsService = MetricsService.create(vertx);

        // Set up web console registry
        WebConsoleRegistry.create("/admin")
                .addPage(MetricsConsolePage.create(dropwizardRegistry))
//                .addPage(ServicesConsolePage.create(discovery))
                .addPage(LoggingConsolePage.create())
//                .addPage(CircuitBreakersConsolePage.create())
//                .addPage(ShellConsolePage.create())
//                .addPage(HealthConsolePage.create(healthChecks))
                .addPage(PoolsConsolePage.create(metricsService))
                .addPage(EventBusConsolePage.create(metricsService))
                .addPage(HttpClientsConsolePage.create(metricsService))
                .setCacheBusterEnabled(true) // Adds random query string to scripts
                .mount(vertx, router);

        router.route("/clean").handler { routingContext ->
            def response = routingContext.response()

            eb.send('clean', '', {
                if (it.succeeded()) {
                    response.end("ok")
                } else {
                    response.end(it.cause().toString())
                }
            })

        }

        router.route("/miaosha").handler({ routingContext ->

            def response = routingContext.response()

            eb.send('miaosha', '', {
                if (it.succeeded()) {
                    response.end(it.result().body().toString())
                } else {
                    response.end(it.cause().toString())
                }
            })


        })

        router.route("/miaosha_pl").handler({ routingContext ->

            def response = routingContext.response()

            eb.send('miaosha_pl', '', {
                if (it.succeeded()) {
                    response.end(it.result().body().toString())
                } else {
                    response.end(it.cause().toString())
                }
            })


        })

        router.route("/miaosha_pl_g").handler({ routingContext ->

            def response = routingContext.response()

            eb.send('miaosha_pl_g', '', {
                if (it.succeeded()) {
                    response.end(it.result().body().toString())
                } else {
                    response.end(it.cause().toString())
                }
            })


        })

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


        vertx.setPeriodic(1000l, { t ->
            // Create a timestamp string
            eb.send("feed", vertx.deploymentIDs().toString())
        });
    }
}
