package com.aruistar.vertxdemo.web

import com.alibaba.druid.pool.DruidDataSource
import groovy.sql.Sql
import io.reactiverse.pgclient.PgClient
import io.reactiverse.pgclient.PgPool
import io.reactiverse.pgclient.PgPoolOptions
import io.reactiverse.pgclient.Tuple
import io.vertx.core.AbstractVerticle

class DatabaseVerticle extends AbstractVerticle {

    PgPoolOptions pgOptions
    PgPool pgPool
    Sql db

    @Override
    void start() throws Exception {

        def port = config().getInteger("port")
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

        def dbSource = new DruidDataSource()
        dbSource.setUrl("jdbc:postgresql://${host}:${port}/${database}")
        dbSource.setDriverClassName("org.postgresql.Driver")
        dbSource.setUsername(user)
        dbSource.setPassword(password)
        dbSource.setInitialSize(maxSize)
        dbSource.setMaxActive(maxSize)

        db = new Sql(dbSource)


        def eb = vertx.eventBus()

        eb.consumer("clean", { msg ->
            pgPool.query('delete  from orders;update commodity set b_sell = false where b_sell = true ;update commodity2 set i_quantity_sell = 0 ', {
                if (it.succeeded()) {
                    msg.reply("ok")
                } else {
                    msg.fail(500, 'delete err')
                }
            })
        })

        eb.consumer("reset", { msg ->

            def size = msg.body()

            if (size == null) size = 10000

            pgPool.query("""
truncate commodity;
truncate orders;
insert into commodity (n_price) select (random() * 100)::int from generate_series(1, $size);
update commodity set v_name = 'name_' || n_price;
update commodity set v_info = md5(v_name::text);
update commodity2 set i_quantity_sell = 0 ,  i_quantity = $size
""", {
                if (it.succeeded()) {
                    msg.reply("ok")
                } else {
                    msg.fail(500, 'reset err')
                }
            })
        })

        eb.consumer('miaosha_pl_g', { msg ->
            vertx.executeBlocking({
                String id = db.firstRow("select miao()").miao
                if (id?.length()) {

                } else {
                    id = "sold out"
                }
                it.complete(id)
            }, false, {
                msg.reply(it.result().toString())
            })

        })

        eb.consumer('miaosha_pl', { msg ->

            def sql = "select miao() as miao"
            if (msg.body() == 'one_row')
                sql = "select miao_one_row() as  miao"

            pgPool.getConnection({ res ->
                if (res.succeeded()) {
                    def conn = res.result()
                    conn.query(sql, {
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

        eb.consumer('miaosha_one_row', { msg ->

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

                    conn.preparedQuery("update commodity2 set i_quantity_sell = i_quantity_sell+1 where id = '566e3ceb-e4d9-4e24-82df-b4ab200258a2' and i_quantity_sell < i_quantity", { ar ->
                        // Works fine of course
                        if (ar.succeeded()) {

                            def rowCount = (ar.result() as io.reactiverse.pgclient.PgRowSet).rowCount()
                            if (rowCount == 1) {

                                conn.preparedQuery('insert into orders (id_at_commodity) values ($1) RETURNING id', Tuple.of('566e3ceb-e4d9-4e24-82df-b4ab200258a2'), {
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
