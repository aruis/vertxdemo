import io.reactiverse.pgclient.PgClient
import io.reactiverse.pgclient.PgPool
import io.reactiverse.pgclient.PgTransaction
import io.reactiverse.pgclient.Tuple
import io.vertx.ext.unit.Async
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(VertxUnitRunner.class)
class ReactivePostgresClientTest {

    def options = [
            port    : 5432,
            host    : "127.0.0.1",
            database: "studypg",
            user    : "postgres",
            password: "aruis",
            maxSize : 5
    ]

    PgPool pgClient

    @Before
    void setUp() throws Exception {
        pgClient = PgClient.pool(options)
    }

    @After
    void tearDown() throws Exception {
        pgClient.close()
    }

    @Test
    void testPostgresIsOK(TestContext context) {
        Async async = context.async();
        pgClient.query("select 1", { ar ->
            if (ar.succeeded()) {
                def result = ar.result().getAt(0).getValue(0)
                println(result)
                context.assertEquals(1, 1)
                async.complete()
            }

        })

    }

    @Test
    void testTransactionUsePoolBegin(TestContext context) {
        Async async = context.async()
        pgClient.begin({ res ->
            if (res.succeeded()) {
                // Begin the transaction
                PgTransaction tx = res.result()

                tx.abortHandler({ v ->
                    println("Transaction failed => rollbacked")
                    context.assertTrue(true)
                    async.complete()
                })

                tx.query('delete from users', {})
                // Various statements
                tx.query("INSERT INTO Users (first_name,last_name) VALUES ('Julien','Viet')", {})
                tx.query("INSERT INTO Users (first_name,last_name) VALUES ('Julien','Viet')", {})

                // Commit the transaction
                tx.commit({ ar ->
                    context.assertFalse(ar.succeeded())
                    if (ar.succeeded()) {
                        System.out.println("Transaction succeeded");
                    } else {
                        System.out.println("Transaction failed " + ar.cause().getMessage());
                    }
                });
            }
        })


    }


    @Test
    void testTransaction(TestContext context) {
        Async async = context.async()
        pgClient.getConnection({ res ->
            if (res.succeeded()) {

                // Transaction must use a connection
                def conn = res.result()

                // Begin the transaction
                def tx = conn.begin().abortHandler({ v ->
                    println("Transaction failed => rollbacked")
                    context.assertTrue(true)
                    async.complete()
                })

                tx.query("delete from users", {})

                tx.query("INSERT INTO Users (first_name,last_name) VALUES ('Julien','Viet')", {})
                tx.query("INSERT INTO Users (first_name,last_name) VALUES ('Julien','Viet')", {})

                // Attempt to commit the transaction
                tx.commit({ ar ->
                    // But transaction abortion fails it
                    println(ar.cause())
                    context.assertFalse(ar.succeeded())
                    // Return the connection to the pool
                    conn.close()
                })
            }
        })


    }

    @Test
    void testPubAndSub(TestContext context) {
        def async = context.async()
        String channelName = "aruis"
        def message = "hello world"
        pgClient.getConnection {
            def conn = it.result()

            conn.notificationHandler({ notification ->
                println("Received ${notification.payload} on channel ${notification.channel}")
                context.assertEquals(notification.payload, message)
                context.assertEquals(notification.channel, channelName)
                async.complete()
            })

            conn.preparedQuery("LISTEN $channelName", { ar ->
                println("Subscribed to channel")

                conn.preparedQuery('''select pg_notify($1,$2)''', Tuple.of(channelName, message), {})
            })


        }
    }
}
