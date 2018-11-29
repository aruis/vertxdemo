package com.aruistar.vertxdemo.web

import io.reactiverse.pgclient.PgClient
import io.reactiverse.pgclient.PgPool
import io.reactiverse.pgclient.PgPoolOptions
import io.reactiverse.pgclient.Tuple
import io.vertx.core.AbstractVerticle

class DatabaseVerticle extends AbstractVerticle {

    PgPoolOptions pgOptions
    PgPool pgPool

    @Override
    void start() throws Exception {

        pgOptions = new PgPoolOptions()
                .setPort(config().getInteger("port"))
                .setHost(config().getString("host"))
//                .setHost("192.168.0.88")
                .setDatabase(config().getString("database"))
                .setUser(config().getString("user"))
                .setPassword(config().getString("password"))
                .setCachePreparedStatements(true)
                .setMaxSize(config().getInteger("maxsize"))

        pgPool = PgClient.pool(vertx, pgOptions)


        def eb = vertx.eventBus()

        eb.consumer("clean", { msg ->
            pgPool.query('delete  from orders;update commodity set b_sell = false;', {
                if (it.succeeded()) {
                    msg.reply("ok")
                } else {
                    msg.fail(500, 'delete err')
                }
            })
        })

        eb.consumer('miaosha_pl', { msg ->

            pgPool.getConnection({ res ->
                if (res.succeeded()) {
                    def conn = res.result()
                    conn.query("select miao()", {
                        if (it.succeeded()) {
                            def row = it.result()
                            def id = row[0].getString("miao")
                            if (id?.length() > 0) {
                                msg.reply(id)
                            } else {
                                msg.reply("sold out")
                            }

                        } else {
                            msg.fail(500, 'miao err')
                        }
                        conn.close()
                    })
                } else {
                    msg.fail(500, 'miao err')
                }

            })

        })

        eb.consumer("miaosha", { msg ->
            pgPool.getConnection({ res ->
                if (res.succeeded()) {

                    // Transaction must use a connection
                    def conn = res.result()

                    // Begin the transaction
                    def tx = conn.begin().abortHandler({ v ->
                        println("tx error")
                        conn.close()
                        msg.reply("tx error")
                    })

                    conn.preparedQuery("select id from commodity where b_sell = false for update skip locked limit 1;", { ar ->
                        // Works fine of course
                        if (ar.succeeded()) {
                            def row = ar.result()
                            if (row.size() > 0) {
                                def id = row[0].getString("id")
                                conn.preparedQuery('update commodity set b_sell = true where id = $1', Tuple.of(id), {
                                    if (it.succeeded()) {
                                        conn.preparedQuery('insert into orders (id_at_commodity) values ($1) RETURNING id', Tuple.of(id), {
                                            if (it.succeeded()) {
                                                def insertRow = it.result()
                                                def insertid = insertRow[0].getString("id")

                                                tx.commit {
                                                    if (it.succeeded()) {
                                                        conn.close()
                                                        println(insertid)
                                                        msg.reply(insertid)
                                                    }
                                                }

                                            } else {
                                                tx.rollback()
                                                conn.close()
                                            }
                                        })
                                    } else {
                                        tx.rollback()
                                        conn.close()
                                    }
                                })


                            } else {
                                tx.rollback()
                                conn.close()
                                println("sold out")
                                msg.reply("sold out")
                            }
                        } else {
                            tx.rollback()
                            conn.close()
                        }
                    })

                }
            })

        })

    }
}
