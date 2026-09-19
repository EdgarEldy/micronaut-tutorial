package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.support.RbacTestSupport;
import com.edgareldy.micronauttutorial.support.RbacTestSupport.Actor;
import com.edgareldy.micronauttutorial.support.SseTestClient;
import com.edgareldy.micronauttutorial.support.SseTestClient.Frame;
import io.micronaut.context.annotation.Property;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.edgareldy.micronauttutorial.support.RbacTestSupport.asJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end test of the expiry of GET /api/v1/orders/stream: a stream opened with a short lived token delivers its
 * heartbeat and the orders created while the token is valid, then completes by itself when the token expires.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @MicronautTest starts the real server; transactional = false so the order really commits and is broadcast.
// @Property overrides the configuration for this class only: app.jwt.lifespan (seconds) is read by JwtIssuer when a
// token is signed, so every token of this class lives 4 seconds instead of an hour. Micronaut starts a separate
// application context for a test class with different properties.
@MicronautTest(transactional = false)
@Property(name = "app.jwt.lifespan", value = "4")
class OrderStreamExpiryTest {

    private static final String ORDERS = "/api/v1/orders";
    private static final long LIFESPAN_SECONDS = 4;
    private static final long WAIT_SECONDS = 10;

    @Inject
    EmbeddedServer server;

    @Inject
    ConnectionOperations<Connection> connections;

    RbacTestSupport rbac;
    long customerId;
    long productId;
    SseTestClient client;

    @BeforeEach
    void setUp() {
        rbac = new RbacTestSupport(server, connections);
        long categoryId = rbac.db().queryLong("INSERT INTO categories (category_name) VALUES (?) RETURNING id", uniqueName());
        customerId = rbac.db().queryLong("INSERT INTO customers (first_name, last_name) VALUES ('Ada', ?) RETURNING id", uniqueName());
        productId = rbac.db().queryLong("INSERT INTO products (category_id, product_name, unit_price) VALUES (?, ?, 2.00) RETURNING id",
                categoryId, uniqueName());
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        rbac.db().execute("DELETE FROM orders WHERE customer_id IN "
                + "(SELECT id FROM customers WHERE last_name LIKE 'QA\\_EXP\\_%')");
        rbac.db().execute("DELETE FROM products WHERE product_name LIKE 'QA\\_EXP\\_%'");
        rbac.db().execute("DELETE FROM categories WHERE category_name LIKE 'QA\\_EXP\\_%'");
        rbac.db().execute("DELETE FROM customers WHERE last_name LIKE 'QA\\_EXP\\_%'");
        rbac.close();
    }

    private static String uniqueName() {
        return "QA_EXP_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** The exp claim of a JWT (seconds since the epoch), read from its unsigned payload part. */
    private static long expClaim(String jwt) {
        String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
        return JsonPath.from(payload).getLong("exp");
    }

    @Test
    void theStreamCompletesByItselfWhenTheTokenExpiresAfterDeliveringEarlierOrders() throws Exception {
        // Logged in last, so almost the whole 4 seconds are left for the connection.
        Actor actor = rbac.actor("ORDER:READ", "ORDER:WRITE");
        long exp = expClaim(actor.token());
        Instant expiresAt = Instant.ofEpochSecond(exp);
        // Proves the property took effect: an hour long token would be far beyond this bound.
        long remaining = exp - Instant.now().getEpochSecond();
        assertTrue(remaining <= LIFESPAN_SECONDS, "app.jwt.lifespan not applied, remaining " + remaining);

        client = new SseTestClient(server.getURL() + ORDERS + "/stream", actor.token());
        assertEquals(200, client.status.get(WAIT_SECONDS, TimeUnit.SECONDS));
        Frame first = client.next(WAIT_SECONDS * 1000);
        assertNotNull(first, "no first frame");
        assertEquals("heartbeat", first.event());
        assertTrue(Instant.now().isBefore(expiresAt), "the token expired before the stream was established");

        // An order created before the expiry (same 4 s token) is delivered.
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("productId", productId);
        body.put("quantity", 2);
        long orderId = asJson(actor.token()).body(body).post(ORDERS).then().statusCode(201)
                .extract().jsonPath().getLong("data.id");
        Frame order = client.nextOrder(WAIT_SECONDS * 1000);
        assertNotNull(order, "the order created before the expiry was not delivered");
        assertEquals(orderId, order.orderId());

        // The body then ends by itself, bounded: not immediately, and not much after the token lifetime.
        client.ended.get(LIFESPAN_SECONDS + 3, TimeUnit.SECONDS);
        Instant endedAt = Instant.now();
        assertTrue(endedAt.isAfter(expiresAt.minusSeconds(1)), "the stream ended long before the token expiry: " + endedAt);
        assertTrue(endedAt.isBefore(expiresAt.plusSeconds(3)), "the stream outlived its token: " + endedAt);
    }
}
