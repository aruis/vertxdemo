
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler

def router = Router.router(vertx)

//router.route().handler({ routingContext ->
//    routingContext.response().putHeader("content-type", "text/html").end("Hello 112!")
//})

router.route().handler(StaticHandler.create());

vertx.createHttpServer().requestHandler(router).listen(8080)