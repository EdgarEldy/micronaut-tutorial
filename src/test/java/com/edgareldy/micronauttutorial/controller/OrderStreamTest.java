package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.dto.ecommerce.OrderRequest;
import com.edgareldy.micronauttutorial.service.OrderService;
import com.edgareldy.micronauttutorial.support.AuthTestSupport;
import com.edgareldy.micronauttutorial.support.RbacTestSupport;
import com.edgareldy.micronauttutorial.support.RbacTestSupport.Actor;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.annotation.Transactional;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.edgareldy.micronauttutorial.support.RbacTestSupport.as;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.asJson;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.assertError;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end tests of the Server-Sent Events stream GET /api/v1/orders/stream over real HTTP: the immediate heartbeat,
 * the delivery of created orders (exactly once, to every subscriber, only the ones created after the subscription),
 * the customerId and productId filters, the survival of the stream when a client disconnects, the permission checks
 * in the ApiResponse format, and the guarantee that a rolled-back order is never broadcast.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @MicronautTest starts the real Netty server on a random port, as in OrderControllerTest. transactional = false is
// required: the order must really COMMIT for the after-commit listener to broadcast it, and the stream is read by
// other threads that could not see an uncommitted test transaction.
//
// SSE testing technique: a Server-Sent Events response never ends, so REST Assured (which waits for the full body)
// cannot read it. SseClient below uses the JDK HttpClient in a background thread, parses the "event:" and "data:"
// lines into frames and puts them in a queue; tests then WAIT on the queue with a timeout (no sleeping and hoping).
// Absence is never asserted by waiting alone: a matching order is created afterwards and the frames received must be
// exactly the expected ones in order, which also proves the subscriber was alive.
@MicronautTest(transactional = false)
class OrderStreamTest {

    private static final String ORDERS = "/api/v1/orders";
    private static final long WAIT_SECONDS = 10;

    /**
     * Test only bean: creates an order through the real service inside a transaction of its own and then fails, so
     * the transaction rolls back. It lives in src/test and is discovered by the test annotation processor.
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : micronaut-tutorial
     */
    @Singleton
    static class RollbackProbe {

        private final OrderService orderService;

        RollbackProbe(OrderService orderService) {
            this.orderService = orderService;
        }

        @Transactional
        void createThenFail(OrderRequest request) {
            orderService.create(request);
            throw new IllegalStateException("forced rollback");
        }
    }

    /**
     * One parsed Server-Sent Events frame: the event name and the JSON data line.
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : micronaut-tutorial
     */
    record Frame(String event, String data) {
        JsonPath json() {
            return JsonPath.from(data);
        }

        long orderId() {
            return json().getLong("data.id");
        }
    }

    /**
     * Minimal SSE client on the JDK HttpClient: a daemon thread sends the request, records the status and content type
     * as soon as the headers arrive, then parses the body line by line into frames queued for the test.
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : micronaut-tutorial
     */
    static final class SseClient implements AutoCloseable {

        final CompletableFuture<Integer> status = new CompletableFuture<>();
        final CompletableFuture<String> contentType = new CompletableFuture<>();
        final LinkedBlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
        final List<Frame> all = new CopyOnWriteArrayList<>();
        private volatile Stream<String> body;

        SseClient(String url, String token) {
            Thread thread = new Thread(() -> run(url, token), "sse-test-client");
            thread.setDaemon(true);
            thread.start();
        }

        private void run(String url, String token) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("Accept", "text/event-stream")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .GET().build();
                HttpResponse<Stream<String>> response = HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.ofLines());
                body = response.body();
                contentType.complete(response.headers().firstValue("Content-Type").orElse(""));
                status.complete(response.statusCode());
                String event = null;
                String data = null;
                for (var it = body.iterator(); it.hasNext(); ) {
                    String line = it.next();
                    if (line.isEmpty()) {
                        if (event != null || data != null) {
                            Frame frame = new Frame(event, data);
                            all.add(frame);
                            frames.add(frame);
                        }
                        event = null;
                        data = null;
                    } else if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        data = line.substring(5).trim();
                    }
                }
            } catch (IOException | InterruptedException | RuntimeException e) {
                // Closing the client ends the read with an exception: nothing to report.
                status.completeExceptionally(e);
            }
        }

        /** The next frame of any kind, or null after the timeout. */
        Frame next(long millis) throws InterruptedException {
            return frames.poll(millis, TimeUnit.MILLISECONDS);
        }

        /** The next "order" frame (heartbeats are skipped), or null when none arrives within the timeout. */
        Frame nextOrder(long millis) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            while (true) {
                long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                Frame frame = left <= 0 ? null : frames.poll(left, TimeUnit.MILLISECONDS);
                if (frame == null || "order".equals(frame.event())) {
                    return frame;
                }
            }
        }

        /** The order ids received so far, in order. */
        List<Long> orderIds() {
            return all.stream().filter(f -> "order".equals(f.event())).map(Frame::orderId).toList();
        }

        @Override
        public void close() {
            Stream<String> stream = body;
            if (stream != null) {
                stream.close();
            }
        }
    }

    @Inject
    EmbeddedServer server;

    @Inject
    ConnectionOperations<Connection> connections;

    @Inject
    RollbackProbe probe;

    RbacTestSupport rbac;
    Actor writer;
    Actor reader;
    long categoryId;
    final List<SseClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        rbac = new RbacTestSupport(server, connections);
        writer = rbac.actor("ORDER:READ", "ORDER:WRITE");
        reader = rbac.actor("ORDER:READ");
        categoryId = rbac.db().queryLong("INSERT INTO categories (category_name) VALUES (?) RETURNING id", uniqueName());
    }

    @AfterEach
    void tearDown() {
        clients.forEach(SseClient::close);
        rbac.db().execute("DELETE FROM orders WHERE customer_id IN "
                + "(SELECT id FROM customers WHERE last_name LIKE 'QA\\_STR\\_%')");
        rbac.db().execute("DELETE FROM products WHERE product_name LIKE 'QA\\_STR\\_%'");
        rbac.db().execute("DELETE FROM categories WHERE category_name LIKE 'QA\\_STR\\_%'");
        rbac.db().execute("DELETE FROM customers WHERE last_name LIKE 'QA\\_STR\\_%'");
        rbac.close();
    }

    private static String uniqueName() {
        return "QA_STR_" + UUID.randomUUID().toString().replace("-", "");
    }

    private long customer() {
        return rbac.db().queryLong("INSERT INTO customers (first_name, last_name) VALUES ('Ada', ?) RETURNING id", uniqueName());
    }

    private long product(String price) {
        return rbac.db().queryLong("INSERT INTO products (category_id, product_name, unit_price) VALUES (?, ?, ?::numeric) RETURNING id",
                categoryId, uniqueName(), price);
    }

    private long create(long customerId, long productId, int quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("productId", productId);
        body.put("quantity", quantity);
        return asJson(writer.token()).body(body).post(ORDERS).then().statusCode(201)
                .extract().jsonPath().getLong("data.id");
    }

    /** Connects a client and waits for its first heartbeat: from then on the subscription is really established. */
    private SseClient subscribe(String query, String token) throws Exception {
        SseClient client = new SseClient(server.getURL() + ORDERS + "/stream" + query, token);
        clients.add(client);
        assertEquals(200, client.status.get(WAIT_SECONDS, TimeUnit.SECONDS));
        Frame first = client.next(WAIT_SECONDS * 1000);
        assertNotNull(first, "no first frame");
        assertEquals("heartbeat", first.event());
        return client;
    }

    @Test
    void theFirstFrameIsAnImmediateHeartbeatOnATextEventStream() throws Exception {
        long start = System.nanoTime();
        SseClient client = new SseClient(server.getURL() + ORDERS + "/stream", reader.token());
        clients.add(client);

        assertEquals(200, client.status.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue(client.contentType.get().startsWith("text/event-stream"), client.contentType.get());
        Frame first = client.next(WAIT_SECONDS * 1000);
        assertNotNull(first);
        // Far below the 15 s heartbeat period: it was sent on subscription, not after the first tick.
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 10);
        assertEquals("heartbeat", first.event());
        assertEquals(true, first.json().getBoolean("success"));
        assertEquals("heartbeat", first.json().getString("message"));
        assertNull(first.json().get("data"));
    }

    @Test
    void aCreatedOrderIsDeliveredExactlyOnceWithItsDetails() throws Exception {
        long customerId = customer();
        long productId = product("10.50");
        SseClient client = subscribe("", reader.token());

        long first = create(customerId, productId, 3);
        Frame frame = client.nextOrder(WAIT_SECONDS * 1000);

        assertNotNull(frame);
        assertEquals("order", frame.event());
        assertEquals(true, frame.json().getBoolean("success"));
        assertEquals(first, frame.orderId());
        assertEquals(customerId, frame.json().getLong("data.customerId"));
        assertEquals(productId, frame.json().getLong("data.productId"));
        assertEquals(3, frame.json().getInt("data.quantity"));
        assertEquals(0, new BigDecimal("31.50").compareTo(new BigDecimal(frame.json().getString("data.total"))));
        assertNotNull(frame.json().getString("timestamp"));

        // A second order proves the first was not duplicated: the next order frame is the new one.
        long second = create(customerId, productId, 1);
        assertEquals(second, client.nextOrder(WAIT_SECONDS * 1000).orderId());
        assertEquals(List.of(first, second), client.orderIds());
    }

    @Test
    void theCustomerFilterOnlyDeliversMatchingOrders() throws Exception {
        long a = customer();
        long b = customer();
        long product = product("1.00");
        SseClient client = subscribe("?customerId=" + a, reader.token());

        create(b, product, 1);
        long matching = create(a, product, 2);
        create(b, product, 3);
        long matching2 = create(a, product, 4);

        assertEquals(matching, client.nextOrder(WAIT_SECONDS * 1000).orderId());
        assertEquals(matching2, client.nextOrder(WAIT_SECONDS * 1000).orderId());
        assertEquals(List.of(matching, matching2), client.orderIds());
    }

    @Test
    void theProductFilterOnlyDeliversMatchingOrders() throws Exception {
        long customerId = customer();
        long p1 = product("1.00");
        long p2 = product("2.00");
        SseClient client = subscribe("?productId=" + p2, reader.token());

        create(customerId, p1, 1);
        long matching = create(customerId, p2, 2);
        create(customerId, p1, 3);
        long matching2 = create(customerId, p2, 4);

        assertEquals(matching, client.nextOrder(WAIT_SECONDS * 1000).orderId());
        assertEquals(matching2, client.nextOrder(WAIT_SECONDS * 1000).orderId());
        assertEquals(List.of(matching, matching2), client.orderIds());
    }

    @Test
    void severalConcurrentSubscribersAllReceiveTheSameOrder() throws Exception {
        long customerId = customer();
        long productId = product("2.00");
        List<SseClient> subscribers = List.of(subscribe("", reader.token()), subscribe("", reader.token()),
                subscribe("", writer.token()));

        long id = create(customerId, productId, 1);

        for (SseClient subscriber : subscribers) {
            assertEquals(id, subscriber.nextOrder(WAIT_SECONDS * 1000).orderId());
            assertEquals(List.of(id), subscriber.orderIds());
        }
    }

    @Test
    void aLateSubscriberOnlyGetsOrdersCreatedAfterItsSubscription() throws Exception {
        long customerId = customer();
        long productId = product("2.00");
        create(customerId, productId, 1);

        SseClient late = subscribe("", reader.token());
        long fresh = create(customerId, productId, 2);

        assertEquals(fresh, late.nextOrder(WAIT_SECONDS * 1000).orderId());
        assertEquals(List.of(fresh), late.orderIds());
    }

    @Test
    void aClientDisconnectDoesNotBreakTheStreamForTheOthers() throws Exception {
        long customerId = customer();
        long productId = product("2.00");
        SseClient leaving = subscribe("", reader.token());
        SseClient staying = subscribe("", reader.token());

        long before = create(customerId, productId, 1);
        assertEquals(before, leaving.nextOrder(WAIT_SECONDS * 1000).orderId());
        assertEquals(before, staying.nextOrder(WAIT_SECONDS * 1000).orderId());

        leaving.close();
        long after = create(customerId, productId, 2);
        long afterAgain = create(customerId, productId, 3);

        assertEquals(after, staying.nextOrder(WAIT_SECONDS * 1000).orderId());
        assertEquals(afterAgain, staying.nextOrder(WAIT_SECONDS * 1000).orderId());
        // A new subscriber after the disconnect works too.
        SseClient fresh = subscribe("", reader.token());
        long last = create(customerId, productId, 4);
        assertEquals(last, fresh.nextOrder(WAIT_SECONDS * 1000).orderId());
    }

    @Test
    void theStreamRequiresAuthenticationAndTheOrderReadPermission() {
        // Errors are ordinary JSON ApiResponse bodies, so REST Assured can read them (Accept asks for the stream).
        assertError(given().accept("text/event-stream").get(ORDERS + "/stream").then(), 401);
        assertError(given().accept("text/event-stream").header("Authorization", AuthTestSupport.bearer("not-a-jwt"))
                .get(ORDERS + "/stream").then(), 401);

        // ORDER:WRITE alone: a synchronous 403 "Access denied", not a 500 from a failing Publisher.
        Actor writeOnly = rbac.actor("ORDER:WRITE");
        assertError(as(writeOnly.token()).accept("text/event-stream").get(ORDERS + "/stream").then(), 403)
                .body("message", equalTo("Access denied"));
        assertError(as(writeOnly.token()).accept("text/event-stream").queryParam("customerId", 1)
                .get(ORDERS + "/stream").then(), 403);
    }

    @Test
    void anOrderReadTokenGetsAnOpenStream() throws Exception {
        SseClient client = new SseClient(server.getURL() + ORDERS + "/stream", reader.token());
        clients.add(client);

        assertEquals(200, client.status.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue(client.contentType.get().startsWith("text/event-stream"));
    }

    // Regression for the "@TransactionalEventListener" design: the event is published INSIDE the transaction, but the
    // listener only runs after a successful commit. Here the transaction rolls back, so nothing may be broadcast.
    @Test
    void aRolledBackOrderIsNeverBroadcast() throws Exception {
        long customerId = customer();
        long productId = product("4.00");
        SseClient client = subscribe("", reader.token());

        assertThrows(IllegalStateException.class,
                () -> probe.createThenFail(new OrderRequest(customerId, productId, 1)));

        assertNull(client.nextOrder(2000), "a rolled back order was broadcast");
        assertEquals(0, rbac.db().queryLong("SELECT COUNT(*) FROM orders WHERE customer_id = ?", customerId));

        // The subscriber was alive all along: a committed order is received, and it is the only one.
        long committed = create(customerId, productId, 2);
        assertEquals(committed, client.nextOrder(WAIT_SECONDS * 1000).orderId());
        assertEquals(List.of(committed), client.orderIds());
    }
}
