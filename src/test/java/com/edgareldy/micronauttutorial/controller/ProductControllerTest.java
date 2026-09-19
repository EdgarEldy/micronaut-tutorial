package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.support.RbacTestSupport;
import com.edgareldy.micronauttutorial.support.RbacTestSupport.Actor;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.HashMap;
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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End to end tests of /api/v1/products over real HTTP: CRUD, the categoryId filter, pagination bounds, the unknown
 * category rule, the permission checks (401 and 403) and the category delete refusal, in the ApiResponse format.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Same mechanism as CategoryControllerTest: transactional = false and tokens from a real login. Categories and
// products created here carry the QA_PRD_ prefix and are swept after each test (products first, foreign key).
@MicronautTest(transactional = false)
class ProductControllerTest {

    private static final String PRODUCTS = "/api/v1/products";
    private static final String CATEGORIES = "/api/v1/categories";
    private static final String PREFIX = "QA_PRD_";

    @Inject
    EmbeddedServer server;

    @Inject
    ConnectionOperations<Connection> connections;

    RbacTestSupport rbac;
    Actor writer;
    Actor reader;

    @BeforeEach
    void setUp() {
        rbac = new RbacTestSupport(server, connections);
        writer = rbac.actor("PRODUCT:READ", "PRODUCT:WRITE");
        reader = rbac.actor("PRODUCT:READ");
    }

    @AfterEach
    void tearDown() {
        rbac.db().execute("DELETE FROM products WHERE category_id IN "
                + "(SELECT id FROM categories WHERE category_name LIKE 'QA\\_PRD\\_%')");
        rbac.db().execute("DELETE FROM categories WHERE category_name LIKE 'QA\\_PRD\\_%'");
        rbac.close();
    }

    private static String uniqueName() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private long category() {
        return rbac.db().queryLong("INSERT INTO categories (category_name) VALUES (?) RETURNING id", uniqueName());
    }

    private static Map<String, Object> body(Object categoryId, Object name, Object price) {
        Map<String, Object> map = new HashMap<>();
        map.put("categoryId", categoryId);
        map.put("productName", name);
        map.put("unitPrice", price);
        return map;
    }

    private long create(long categoryId, String name) {
        return asJson(writer.token()).body(body(categoryId, name, 10.5)).post(PRODUCTS).then().statusCode(201)
                .extract().jsonPath().getLong("data.id");
    }

    @Test
    void createReturns201TrimsTheNameAndHasNoCategoryNameField() {
        long categoryId = category();
        String name = uniqueName();

        asJson(writer.token()).body(body(categoryId, "  " + name + "  ", 12.5)).post(PRODUCTS).then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("message", notNullValue())
                .body("timestamp", notNullValue())
                .body("data.id", notNullValue())
                .body("data.categoryId", equalTo((int) categoryId))
                .body("data.productName", equalTo(name))
                .body("data.unitPrice", equalTo(12.5f))
                .body("data.containsKey('categoryName')", equalTo(false));
    }

    @Test
    void createWithAnUnknownCategoryIsRefusedWith422() {
        assertError(asJson(writer.token()).body(body(MISSING, uniqueName(), 5)).post(PRODUCTS).then(), 422)
                .body("message", containsString(String.valueOf(MISSING)));
    }

    @Test
    void createRejectsInvalidBodiesWith400() {
        long categoryId = category();
        String token = writer.token();

        assertError(asJson(token).body(body(categoryId, "   ", 5)).post(PRODUCTS).then(), 400)
                .body("message", containsString("must not be blank"));
        assertError(asJson(token).body(body(null, uniqueName(), 5)).post(PRODUCTS).then(), 400)
                .body("message", containsString("categoryId"));
        assertError(asJson(token).body(body(categoryId, uniqueName(), 0)).post(PRODUCTS).then(), 400)
                .body("message", containsString("greater than 0"));
        assertError(asJson(token).body(body(categoryId, uniqueName(), -3)).post(PRODUCTS).then(), 400);
        assertError(asJson(token).body(body(categoryId, uniqueName(), null)).post(PRODUCTS).then(), 400);
        // Raw JSON keeps the exact decimal text 1.005 (three fraction digits).
        assertError(asJson(token).body("{\"categoryId\":" + categoryId + ",\"productName\":\"" + uniqueName()
                + "\",\"unitPrice\":1.005}").post(PRODUCTS).then(), 400)
                .body("message", containsString("2 fraction digits"));
        assertError(asJson(token).body(body(categoryId, "x".repeat(151), 5)).post(PRODUCTS).then(), 400)
                .body("message", containsString("at most 150"));
    }

    @Test
    void listFiltersByCategoryAndReportsTotalsAndPages() {
        long a = category();
        long b = category();
        for (int i = 0; i < 3; i++) {
            create(a, uniqueName());
        }
        create(b, uniqueName());

        as(reader.token()).queryParam("categoryId", a).queryParam("size", 2).get(PRODUCTS).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.content", hasSize(2))
                .body("data.content.categoryId", everyCategory(a))
                .body("data.page", equalTo(0))
                .body("data.size", equalTo(2))
                .body("data.totalElements", equalTo(3))
                .body("data.totalPages", equalTo(2));
        as(reader.token()).queryParam("categoryId", a).queryParam("size", 2).queryParam("page", 1).get(PRODUCTS).then()
                .statusCode(200).body("data.content", hasSize(1));
        as(reader.token()).queryParam("categoryId", b).get(PRODUCTS).then()
                .statusCode(200).body("data.content", hasSize(1)).body("data.totalElements", equalTo(1));

        // Unknown category: an empty page, not an error, and the content array is still serialized.
        as(reader.token()).queryParam("categoryId", MISSING).get(PRODUCTS).then()
                .statusCode(200).body("data.content", hasSize(0)).body("data.totalElements", equalTo(0))
                .body("data.totalPages", equalTo(0));

        // Without a filter the list is sorted by id and uses the default size.
        var ids = as(reader.token()).queryParam("size", 100).get(PRODUCTS).then().statusCode(200)
                .body("data.size", equalTo(100)).extract().jsonPath().getList("data.content.id", Long.class);
        assertEquals(ids.stream().sorted().toList(), ids);
        as(reader.token()).get(PRODUCTS).then().statusCode(200).body("data.size", equalTo(20));
    }

    private static org.hamcrest.Matcher<Iterable<? extends Integer>> everyCategory(long categoryId) {
        return org.hamcrest.Matchers.everyItem(equalTo((int) categoryId));
    }

    @Test
    void listAcceptsTheSizeBoundsAndRejectsOutOfRangeValuesWith400() {
        as(reader.token()).queryParam("size", 1).get(PRODUCTS).then().statusCode(200);
        as(reader.token()).queryParam("size", 100).get(PRODUCTS).then().statusCode(200);
        assertError(as(reader.token()).queryParam("size", 0).get(PRODUCTS).then(), 400);
        assertError(as(reader.token()).queryParam("size", 101).get(PRODUCTS).then(), 400);
        assertError(as(reader.token()).queryParam("page", -1).get(PRODUCTS).then(), 400);
    }

    @Test
    void detailReturns200Or404() {
        long categoryId = category();
        String name = uniqueName();
        long id = create(categoryId, name);

        as(reader.token()).get(PRODUCTS + "/" + id).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.id", equalTo((int) id))
                .body("data.productName", equalTo(name))
                .body("data.unitPrice", equalTo(10.5f))
                .body("data.containsKey('categoryName')", equalTo(false));
        assertError(as(reader.token()).get(PRODUCTS + "/" + MISSING).then(), 404)
                .body("message", containsString("not found"));
    }

    @Test
    void updateChangesFieldsAndCategoryAndRejectsUnknownOnes() {
        long first = category();
        long second = category();
        long id = create(first, uniqueName());
        String renamed = uniqueName();

        asJson(writer.token()).body(body(second, " " + renamed + " ", 99.99)).put(PRODUCTS + "/" + id).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.categoryId", equalTo((int) second))
                .body("data.productName", equalTo(renamed))
                .body("data.unitPrice", equalTo(99.99f));
        as(reader.token()).queryParam("categoryId", second).get(PRODUCTS).then().body("data.totalElements", equalTo(1));
        as(reader.token()).queryParam("categoryId", first).get(PRODUCTS).then().body("data.totalElements", equalTo(0));

        assertError(asJson(writer.token()).body(body(MISSING, renamed, 5)).put(PRODUCTS + "/" + id).then(), 422);
        assertError(asJson(writer.token()).body(body(second, renamed, 5)).put(PRODUCTS + "/" + MISSING).then(), 404);
        assertError(asJson(writer.token()).body(body(second, " ", 5)).put(PRODUCTS + "/" + id).then(), 400);
        as(reader.token()).get(PRODUCTS + "/" + id).then().body("data.categoryId", equalTo((int) second));
    }

    @Test
    void deleteRemovesTheProductAndReturns404WhenMissing() {
        long id = create(category(), uniqueName());

        as(writer.token()).delete(PRODUCTS + "/" + id).then()
                .statusCode(200).body("success", equalTo(true)).body("data", nullValue());
        assertError(as(writer.token()).get(PRODUCTS + "/" + id).then(), 404);
        assertError(as(writer.token()).delete(PRODUCTS + "/" + id).then(), 404);
        assertError(as(writer.token()).delete(PRODUCTS + "/" + MISSING).then(), 404);
    }

    // Non-regression: an endpoint without a body must not require a Content-Type, and must also accept the form
    // Content-Type that HTTP clients often add by default.
    @Test
    void deleteWithoutBodyWorksWithAFormContentType() {
        long categoryId = category();
        long id = create(categoryId, uniqueName());
        as(writer.token()).contentType(ContentType.URLENC).delete(PRODUCTS + "/" + id).then().statusCode(200);
        long id2 = create(categoryId, uniqueName());
        as(writer.token()).delete(PRODUCTS + "/" + id2).then().statusCode(200);
    }

    // Regression of the category rule, now counted through ProductRepository.countByCategoryId.
    @Test
    void aCategoryStillHoldingAProductCannotBeDeleted() {
        long categoryId = category();
        long productId = create(categoryId, uniqueName());
        Actor categoryWriter = rbac.actor("CATEGORY:READ", "CATEGORY:WRITE");

        assertError(as(categoryWriter.token()).delete(CATEGORIES + "/" + categoryId).then(), 422)
                .body("message", containsString("1 product(s)"));

        as(writer.token()).delete(PRODUCTS + "/" + productId).then().statusCode(200);
        as(categoryWriter.token()).delete(CATEGORIES + "/" + categoryId).then().statusCode(200);
    }

    @Test
    void requestsWithoutATokenAreRejectedWith401() {
        given().get(PRODUCTS).then().statusCode(401);
        given().contentType(ContentType.JSON).body(body(1, uniqueName(), 5)).post(PRODUCTS).then().statusCode(401);
        given().delete(PRODUCTS + "/1").then().statusCode(401);
    }

    @Test
    void aReadTokenCannotWriteAndAWriteOnlyTokenCannotRead() {
        long categoryId = category();
        long id = create(categoryId, uniqueName());
        Actor writeOnly = rbac.actor("PRODUCT:WRITE");

        assertError(asJson(reader.token()).body(body(categoryId, uniqueName(), 5)).post(PRODUCTS).then(), 403);
        assertError(asJson(reader.token()).body(body(categoryId, uniqueName(), 5)).put(PRODUCTS + "/" + id).then(), 403);
        assertError(as(reader.token()).delete(PRODUCTS + "/" + id).then(), 403);
        assertEquals(1, rbac.db().queryLong("SELECT COUNT(*) FROM products WHERE id = ?", id));

        assertError(as(writeOnly.token()).get(PRODUCTS).then(), 403);
        assertError(as(writeOnly.token()).get(PRODUCTS + "/" + id).then(), 403);
    }
}
