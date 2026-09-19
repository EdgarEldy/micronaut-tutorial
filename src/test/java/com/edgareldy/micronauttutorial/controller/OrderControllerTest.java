package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.support.RbacTestSupport;
import com.edgareldy.micronauttutorial.support.RbacTestSupport.Actor;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.edgareldy.micronauttutorial.support.RbacTestSupport.MISSING;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.as;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.asJson;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.assertError;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End to end tests of /api/v1/orders over real HTTP: creation and the total computation, the frozen total, the
 * validation and reference rules, the filtered paginated list, detail, the absence of PUT and DELETE, the permission
 * checks (401 and 403) and the refusals to delete a product or a customer that still has orders, in the ApiResponse
 * format.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Same mechanism as ProductControllerTest. Products, categories and customers are inserted with SQL under the
// QA_ORD_ prefix; orders are created through the API. The sweep removes orders first (foreign keys).
@MicronautTest(transactional = false)
class OrderControllerTest {

    private static final String ORDERS = "/api/v1/orders";
    private static final String PRODUCTS = "/api/v1/products";
    private static final String CUSTOMERS = "/api/v1/customers";

    @Inject
    EmbeddedServer server;

    @Inject
    ConnectionOperations<Connection> connections;

    RbacTestSupport rbac;
    Actor writer;
    Actor reader;
    long categoryId;

    @BeforeEach
    void setUp() {
        rbac = new RbacTestSupport(server, connections);
        writer = rbac.actor("ORDER:READ", "ORDER:WRITE");
        reader = rbac.actor("ORDER:READ");
        categoryId = rbac.db().queryLong("INSERT INTO categories (category_name) VALUES (?) RETURNING id", uniqueName());
    }

    @AfterEach
    void tearDown() {
        rbac.db().execute("DELETE FROM orders WHERE customer_id IN "
                + "(SELECT id FROM customers WHERE last_name LIKE 'QA\\_ORD\\_%')");
        rbac.db().execute("DELETE FROM products WHERE product_name LIKE 'QA\\_ORD\\_%'");
        rbac.db().execute("DELETE FROM categories WHERE category_name LIKE 'QA\\_ORD\\_%'");
        rbac.db().execute("DELETE FROM customers WHERE last_name LIKE 'QA\\_ORD\\_%'");
        rbac.close();
    }

    private static String uniqueName() {
        return "QA_ORD_" + UUID.randomUUID().toString().replace("-", "");
    }

    private long customer() {
        return rbac.db().queryLong("INSERT INTO customers (first_name, last_name) VALUES ('Ada', ?) RETURNING id", uniqueName());
    }

    private long product(String price) {
        return rbac.db().queryLong("INSERT INTO products (category_id, product_name, unit_price) VALUES (?, ?, ?::numeric) RETURNING id",
                categoryId, uniqueName(), price);
    }

    private static Map<String, Object> body(Object customerId, Object productId, Object quantity) {
        Map<String, Object> map = new HashMap<>();
        map.put("customerId", customerId);
        map.put("productId", productId);
        map.put("quantity", quantity);
        return map;
    }

    private ValidatableResponse post(Map<String, Object> body) {
        return asJson(writer.token()).body(body).post(ORDERS).then();
    }

    private long create(long customerId, long productId, int quantity) {
        return post(body(customerId, productId, quantity)).statusCode(201).extract().jsonPath().getLong("data.id");
    }

    private static BigDecimal total(ValidatableResponse response, String path) {
        return new BigDecimal(response.extract().jsonPath().getString(path));
    }

    private long orderCount(long customerId) {
        return rbac.db().queryLong("SELECT COUNT(*) FROM orders WHERE customer_id = ?", customerId);
    }

    @Test
    void createReturns201AndComputesTheTotal() {
        long customerId = customer();
        long productId = product("10.50");

        ValidatableResponse response = post(body(customerId, productId, 3))
                .statusCode(201)
                .body("success", equalTo(true))
                .body("message", notNullValue())
                .body("timestamp", notNullValue())
                .body("data.id", notNullValue())
                .body("data.customerId", equalTo((int) customerId))
                .body("data.productId", equalTo((int) productId))
                .body("data.quantity", equalTo(3));

        assertEquals(0, new BigDecimal("31.50").compareTo(total(response, "data.total")));
    }

    @Test
    void createComputesBigTotalsAndAcceptsTheQuantityBounds() {
        long customerId = customer();
        long productId = product("1234.56");

        assertEquals(0, new BigDecimal("123456000.00")
                .compareTo(total(post(body(customerId, productId, 100000)).statusCode(201), "data.total")));
        assertEquals(0, new BigDecimal("1234.56")
                .compareTo(total(post(body(customerId, productId, 1)).statusCode(201), "data.total")));
    }

    @Test
    void theTotalIsFrozenWhenTheProductPriceChangesLater() {
        long customerId = customer();
        long productId = product("10.00");
        long orderId = create(customerId, productId, 2);

        rbac.db().execute("UPDATE products SET unit_price = 99.00 WHERE id = ?", productId);

        assertEquals(0, new BigDecimal("20.00").compareTo(total(
                as(reader.token()).get(ORDERS + "/" + orderId).then().statusCode(200), "data.total")));
        assertEquals(0, new BigDecimal("20.00")
                .compareTo(new BigDecimal(rbac.db().queryString("SELECT total FROM orders WHERE id = ?", orderId))));
        // A new order uses the new price.
        assertEquals(0, new BigDecimal("99.00")
                .compareTo(total(post(body(customerId, productId, 1)).statusCode(201), "data.total")));
    }

    @Test
    void aTotalThatDoesNotFitIsARefusalWith422NotA500() {
        long customerId = customer();
        long huge = product("9999999999.99");
        long edge = product("10000000.00");
        long fitting = product("9999999.99");

        assertError(post(body(customerId, huge, 100000)), 422)
                .body("message", containsString("too large"));
        assertError(post(body(customerId, edge, 100000)), 422);
        post(body(customerId, fitting, 100000)).statusCode(201);
        assertEquals(1, orderCount(customerId));
    }

    @Test
    void createWithAnUnknownCustomerOrProductIsRefusedWith422() {
        long customerId = customer();
        long productId = product("5.00");

        assertError(post(body(MISSING, productId, 1)), 422).body("message", containsString(String.valueOf(MISSING)));
        assertError(post(body(customerId, MISSING, 1)), 422).body("message", containsString(String.valueOf(MISSING)));
        assertEquals(0, orderCount(customerId));
    }

    @Test
    void createRejectsInvalidBodiesWith400() {
        long customerId = customer();
        long productId = product("5.00");

        assertError(post(body(null, productId, 1)), 400).body("message", containsString("customerId"));
        assertError(post(body(customerId, null, 1)), 400).body("message", containsString("productId"));
        assertError(post(body(customerId, productId, null)), 400).body("message", containsString("quantity"));
        assertError(post(body(customerId, productId, 0)), 400).body("message", containsString("at least 1"));
        assertError(post(body(customerId, productId, -1)), 400);
        assertError(post(body(customerId, productId, 100001)), 400).body("message", containsString("at most 100000"));
        assertError(post(new HashMap<>()), 400);
        assertEquals(0, orderCount(customerId));
    }

    @Test
    void listFiltersByCustomerProductAndBothAndPaginates() {
        long a = customer();
        long b = customer();
        long p1 = product("1.00");
        long p2 = product("2.00");
        create(a, p1, 1);
        create(a, p1, 2);
        create(a, p2, 3);
        create(b, p1, 4);

        as(reader.token()).queryParam("customerId", a).queryParam("size", 2).get(ORDERS).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.content", hasSize(2))
                .body("data.content.customerId", org.hamcrest.Matchers.everyItem(equalTo((int) a)))
                .body("data.page", equalTo(0))
                .body("data.size", equalTo(2))
                .body("data.totalElements", equalTo(3))
                .body("data.totalPages", equalTo(2));
        as(reader.token()).queryParam("customerId", a).queryParam("size", 2).queryParam("page", 1).get(ORDERS).then()
                .statusCode(200).body("data.content", hasSize(1));
        as(reader.token()).queryParam("productId", p1).get(ORDERS).then()
                .statusCode(200).body("data.totalElements", equalTo(3))
                .body("data.content.productId", org.hamcrest.Matchers.everyItem(equalTo((int) p1)));
        as(reader.token()).queryParam("customerId", a).queryParam("productId", p1).get(ORDERS).then()
                .statusCode(200).body("data.totalElements", equalTo(2))
                .body("data.content.quantity", equalTo(List.of(1, 2)));
        as(reader.token()).queryParam("customerId", b).queryParam("productId", p2).get(ORDERS).then()
                .statusCode(200).body("data.totalElements", equalTo(0));

        // Unknown ids: an empty page, not an error, and the content array is still serialized.
        as(reader.token()).queryParam("customerId", MISSING).get(ORDERS).then()
                .statusCode(200).body("data.content", hasSize(0)).body("data.totalElements", equalTo(0))
                .body("data.totalPages", equalTo(0));
        as(reader.token()).queryParam("productId", MISSING).get(ORDERS).then()
                .statusCode(200).body("data.content", hasSize(0));
        as(reader.token()).queryParam("customerId", MISSING).queryParam("productId", MISSING).get(ORDERS).then()
                .statusCode(200).body("data.content", hasSize(0));

        // Without a filter the list is sorted by id and uses the default size.
        var ids = as(reader.token()).queryParam("size", 100).get(ORDERS).then().statusCode(200)
                .body("data.size", equalTo(100)).extract().jsonPath().getList("data.content.id", Long.class);
        assertEquals(ids.stream().sorted().toList(), ids);
        as(reader.token()).get(ORDERS).then().statusCode(200).body("data.size", equalTo(20));
    }

    @Test
    void listAcceptsTheSizeBoundsAndRejectsOutOfRangeValuesWith400() {
        as(reader.token()).queryParam("size", 1).get(ORDERS).then().statusCode(200);
        as(reader.token()).queryParam("size", 100).get(ORDERS).then().statusCode(200);
        assertError(as(reader.token()).queryParam("size", 0).get(ORDERS).then(), 400);
        assertError(as(reader.token()).queryParam("size", 101).get(ORDERS).then(), 400);
        assertError(as(reader.token()).queryParam("page", -1).get(ORDERS).then(), 400);
    }

    @Test
    void detailReturns200Or404() {
        long customerId = customer();
        long productId = product("2.50");
        long id = create(customerId, productId, 4);

        as(reader.token()).get(ORDERS + "/" + id).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.id", equalTo((int) id))
                .body("data.customerId", equalTo((int) customerId))
                .body("data.productId", equalTo((int) productId))
                .body("data.quantity", equalTo(4))
                .body("data.total", equalTo(10.0f));
        assertError(as(reader.token()).get(ORDERS + "/" + MISSING).then(), 404)
                .body("message", containsString("not found"));
    }

    @Test
    void ordersCannotBeUpdatedOrDeleted() {
        long customerId = customer();
        long id = create(customerId, product("2.50"), 1);

        assertError(asJson(writer.token()).body(body(customerId, product("1.00"), 9)).put(ORDERS + "/" + id).then(), 405);
        assertError(as(writer.token()).delete(ORDERS + "/" + id).then(), 405);
        assertError(asJson(writer.token()).body(body(customerId, 1, 9)).put(ORDERS).then(), 405);
        assertEquals(1, orderCount(customerId));
    }

    @Test
    void requestsWithoutATokenAreRejectedWith401() {
        given().get(ORDERS).then().statusCode(401);
        given().get(ORDERS + "/1").then().statusCode(401);
        given().contentType(ContentType.JSON).body(body(1, 1, 1)).post(ORDERS).then().statusCode(401);
    }

    @Test
    void aReadTokenCannotWriteAndAWriteOnlyTokenCannotRead() {
        long customerId = customer();
        long productId = product("2.50");
        long id = create(customerId, productId, 1);
        Actor writeOnly = rbac.actor("ORDER:WRITE");

        assertError(asJson(reader.token()).body(body(customerId, productId, 1)).post(ORDERS).then(), 403);
        assertEquals(1, orderCount(customerId));

        assertError(as(writeOnly.token()).get(ORDERS).then(), 403);
        assertError(as(writeOnly.token()).get(ORDERS + "/" + id).then(), 403);
        asJson(writeOnly.token()).body(body(customerId, productId, 1)).post(ORDERS).then().statusCode(201);
    }

    @Test
    void aProductWithOrdersCannotBeDeletedAndItsCacheEntryIsKept() {
        long customerId = customer();
        long productId = product("10.00");
        Actor productWriter = rbac.actor("PRODUCT:READ", "PRODUCT:WRITE");
        create(customerId, productId, 1);
        create(customerId, productId, 2);

        // Warm the product cache, then change the price behind the application's back: only the cache can still
        // answer with the old one. A refused delete must not evict the entry.
        as(productWriter.token()).get(PRODUCTS + "/" + productId).then().body("data.unitPrice", equalTo(10.0f));
        rbac.db().execute("UPDATE products SET unit_price = 77.00 WHERE id = ?", productId);

        assertError(as(productWriter.token()).delete(PRODUCTS + "/" + productId).then(), 422)
                .body("message", containsString("2 order(s)"));

        as(productWriter.token()).get(PRODUCTS + "/" + productId).then()
                .statusCode(200).body("data.unitPrice", equalTo(10.0f));
        assertEquals(1, rbac.db().queryLong("SELECT COUNT(*) FROM products WHERE id = ?", productId));

        rbac.db().execute("DELETE FROM orders WHERE product_id = ?", productId);
        as(productWriter.token()).delete(PRODUCTS + "/" + productId).then().statusCode(200);
        assertError(as(productWriter.token()).get(PRODUCTS + "/" + productId).then(), 404);
    }

    @Test
    void aCustomerWithOrdersCannotBeDeleted() {
        long customerId = customer();
        Actor customerWriter = rbac.actor("CUSTOMER:READ", "CUSTOMER:WRITE");
        create(customerId, product("3.00"), 1);

        assertError(as(customerWriter.token()).delete(CUSTOMERS + "/" + customerId).then(), 422)
                .body("message", containsString("1 order(s)"));
        as(customerWriter.token()).get(CUSTOMERS + "/" + customerId).then().statusCode(200);

        rbac.db().execute("DELETE FROM orders WHERE customer_id = ?", customerId);
        as(customerWriter.token()).delete(CUSTOMERS + "/" + customerId).then().statusCode(200);
        assertError(as(customerWriter.token()).get(CUSTOMERS + "/" + customerId).then(), 404);
    }
}
