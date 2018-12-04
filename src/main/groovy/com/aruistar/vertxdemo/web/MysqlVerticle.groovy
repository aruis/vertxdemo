package com.aruistar.vertxdemo.web

import com.alibaba.druid.pool.DruidDataSource
import groovy.sql.Sql
import io.vertx.core.AbstractVerticle
import io.vertx.core.json.JsonArray
import io.vertx.ext.asyncsql.AsyncSQLClient
import io.vertx.ext.asyncsql.MySQLClient

class MysqlVerticle extends AbstractVerticle {

    Sql db
    AsyncSQLClient client

    @Override
    void start() throws Exception {


        def port = config().getInteger("port")
        def host = config().getString("host")
        def database = config().getString("database")
        def username = config().getString("username")
        def password = config().getString("password")
        def maxSize = config().getInteger("maxPoolSize")


        client = MySQLClient.createShared(vertx, config())


        def dbSource = new DruidDataSource()
        dbSource.setUrl("jdbc:mysql://${host}:${port}/${database}")
        dbSource.setDriverClassName("com.mysql.cj.jdbc.Driver")
        dbSource.setUsername(username)
        dbSource.setPassword(password)
        dbSource.setInitialSize(maxSize)
        dbSource.setMaxActive(maxSize)

        db = new Sql(dbSource)


        def eb = vertx.eventBus()

        eb.consumer("clean", { msg ->

            client.query("delete  from orders;update commodity set b_sell = false where b_sell = true ;update commodity2 set i_quantity_sell = 0 ", {
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

            try {
                db.execute("truncate commodity;")
                db.execute("truncate orders;")
                db.execute("update commodity2 set i_quantity_sell = 0 ,  i_quantity = ?", size)
                db.withBatch(1000, "insert into commodity (v_name, n_price, v_info) values (?,?,?)", { st ->
                    size.toInteger().times {
                        def price = it
                        def name = "name_" + price
                        st.addBatch(
                                name,
                                price,
                                name.md5()
                        )
                    }
                })

                msg.reply("ok")
            }
            catch (e) {
                msg.fail(500, 'reset err')
            }
        })

        eb.consumer('miaosha_one_row', { msg ->


            client.getConnection({ res ->
                if (res.succeeded()) {

                    // Transaction must use a connection
                    def conn = res.result()

                    conn.setAutoCommit(false, {
                        conn.update("update commodity2 set i_quantity_sell = i_quantity_sell+1 where id = 1 and i_quantity_sell < i_quantity", { ar ->
                            // Works fine of course
                            if (ar.succeeded()) {
                                def rowCount = ar.result().getUpdated()
                                if (rowCount == 1) {

                                    conn.update('insert into orders (id_at_commodity) values (1) ', {
                                        if (it.succeeded()) {
                                            def insertRow = it.result()
                                            def insertid = insertRow.getKeys()

                                            conn.commit {
                                                if (it.succeeded()) {
                                                    conn.close()
                                                    println(insertid)
                                                    msg.reply(insertid)
                                                }
                                            }

                                        } else {
                                            conn.rollback({
                                                conn.close()
                                            })

                                        }
                                    })

                                } else {
                                    conn.rollback({
                                        conn.close()
                                        println("sold out")
                                        msg.reply("sold out")
                                    })

                                }

                            } else {
                                conn.rollback({
                                    conn.close()
                                })

                            }
                        })
                    })
                }
            })

        })

        eb.consumer("miaosha", { msg ->
            client.getConnection({ res ->
                if (res.succeeded()) {

                    // Transaction must use a connection
                    def conn = res.result()

                    conn.setAutoCommit(false, {

                        conn.query("select id from commodity where b_sell = false limit 1 for update skip locked;", { ar ->
                            // Works fine of course
                            if (ar.succeeded()) {
                                def row = ar.result()
                                if (row.getNumRows() > 0) {
                                    def id = row.getRows()[0].getInteger('id')
                                    conn.updateWithParams('update commodity set b_sell = true where id = ?', new JsonArray().add(id), {
                                        if (it.succeeded()) {
                                            conn.updateWithParams('insert into orders (id_at_commodity) values (?) ', new JsonArray().add(id), {
                                                if (it.succeeded()) {
                                                    def insertRow = it.result()
                                                    def insertid = insertRow.getKeys()[0]

                                                    conn.commit {
                                                        if (it.succeeded()) {
                                                            conn.close()
                                                            println(insertid)
                                                            msg.reply(insertid)
                                                        }
                                                    }

                                                } else {
                                                    conn.rollback({
                                                        conn.close()
                                                    })

                                                }
                                            })
                                        } else {
                                            conn.rollback({
                                                conn.close()
                                            })

                                        }
                                    })


                                } else {
                                    conn.rollback({
                                        conn.close()
                                        println("sold out")
                                        msg.reply("sold out")
                                    })

                                }
                            } else {
                                conn.rollback({
                                    conn.close()
                                })

                            }
                        })
                    })
                }
            })

        })

    }
}
