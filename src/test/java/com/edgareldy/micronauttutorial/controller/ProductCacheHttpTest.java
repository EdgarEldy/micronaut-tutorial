package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.support.RbacTestSupport;
import com.edgareldy.micronauttutorial.support.RbacTestSupport.Actor;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

import static com.edgareldy.micronauttutorial.support.RbacTestSupport.as;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.asJson;
import static org.hamcrest.Matchers.equalTo;

/**
 * End to end proof, through real HTTP and the real database, that GET /api/v1/products/{id} is served from the
 * product cache and that PUT invalidates the entry.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// The price is changed behind the application's back with plain SQL: a read that still returns the old price can
// only come from the cache. Then PUT goes through the service, evicts the entry, and the next GET sees the new price.
@MicronautTest(transactional = false)
class ProductCacheHttpTest {

    private static final String PRODUCTS = "/api/v1/products";

    @Inject
    EmbeddedServer server;

    @Inject
    ConnectionOperations<Connection> connections;

    RbacTestSupport rbac;
    Actor caller;

    @BeforeEach
    void setUp() {
        rbac = new RbacTestSupport(server, connections);
        caller = rbac.actor("PRODUCT:READ", "PRODUCT:WRITE");
    }

    @AfterEach
    void tearDown() {
        rbac.db().execute("DELETE FROM products WHERE category_id IN "
                + "(SELECT id FROM categories WHERE category_name LIKE 'QA\\_PRC\\_%')");
        rbac.db().execute("DELETE FROM categories WHERE category_name LIKE 'QA\\_PRC\\_%'");
        rbac.close();
    }

    @Test
    void aSecondGetIsServedFromTheCacheUntilAPutEvictsTheEntry() {
        String name = "QA_PRC_" + UUID.randomUUID().toString().replace("-", "");
        long categoryId = rbac.db().queryLong("INSERT INTO categories (category_name) VALUES (?) RETURNING id", name);
        long id = asJson(caller.token()).body(Map.of("categoryId", categoryId, "productName", name, "unitPrice", 10.5))
                .post(PRODUCTS).then().statusCode(201).extract().jsonPath().getLong("data.id");

        as(caller.token()).get(PRODUCTS + "/" + id).then().statusCode(200).body("data.unitPrice", equalTo(10.5f));

        rbac.db().execute("UPDATE products SET unit_price = 77.00 WHERE id = ?", id);
        as(caller.token()).get(PRODUCTS + "/" + id).then().statusCode(200).body("data.unitPrice", equalTo(10.5f));

        asJson(caller.token()).body(Map.of("categoryId", categoryId, "productName", name, "unitPrice", 20.25))
                .put(PRODUCTS + "/" + id).then().statusCode(200);
        as(caller.token()).get(PRODUCTS + "/" + id).then().statusCode(200).body("data.unitPrice", equalTo(20.25f));

        // Cached again, then a delete evicts: the next read is a 404 rather than the stale entry.
        rbac.db().execute("UPDATE products SET unit_price = 1.00 WHERE id = ?", id);
        as(caller.token()).get(PRODUCTS + "/" + id).then().body("data.unitPrice", equalTo(20.25f));
        as(caller.token()).delete(PRODUCTS + "/" + id).then().statusCode(200);
        as(caller.token()).get(PRODUCTS + "/" + id).then().statusCode(404);
    }
}
