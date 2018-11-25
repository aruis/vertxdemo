package com.aruistar.vertxdemo


import groovy.util.logging.Slf4j
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions


@Slf4j
class MainVerticle extends AbstractVerticle {
    @Override
    void start() throws Exception {
        vertx.deployVerticle(com.aruistar.vertxdemo.web.HttpVerticle.newInstance(), new DeploymentOptions(config()))
        log.info("verticle is starting")
    }
}