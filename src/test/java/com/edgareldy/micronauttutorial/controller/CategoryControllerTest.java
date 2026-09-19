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
import java.util.Map;
import java.util.UUID;

import static com.edgareldy.micronauttutorial.support.RbacTestSupport.MISSING;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.as;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.asJson;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.assertError;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End to end tests of /api/v1/categories over real HTTP: every endpoint, pagination bounds, the permission checks
 * (401 and 403) and the refusal to delete a category that still has products, all in the ApiResponse format.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Same mechanism as RoleControllerTest: @MicronautTest(transactional = false) boots the app on the Test Resources
// PostgreSQL and the callers get a bearer token from a real login. Every category created here carries the QA_CAT_
// prefix and is swept (with its products) after each test, since the database is shared by all test classes.
@MicronautTest(transactional = false)
class CategoryControllerTest {

    private static final String CATEGORIES = "/api/v1/categories";
    private static final String PREFIX = "QA_CAT_";

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
        writer = rbac.actor("CATEGORY:READ", "CATEGORY:WRITE");
        reader = rbac.actor("CATEGORY:READ");
    }

    @AfterEach
    void tearDown() {
        rbac.db().execute("DELETE FROM products WHERE category_id IN "
                + "(SELECT id FROM categories WHERE category_name LIKE 'QA\\_CAT\\_%')");
        rbac.db().execute("DELETE FROM categories WHERE category_name LIKE 'QA\\_CAT\\_%'");
        rbac.close();
    }

    private static String uniqueName() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private long create(String name) {
        return asJson(writer.token()).body(Map.of("categoryName", name)).post(CATEGORIES).then().statusCode(201)
                .extract().jsonPath().getLong("data.id");
    }

    private void insertProduct(long categoryId) {
        rbac.db().execute("INSERT INTO products (category_id, product_name, unit_price) VALUES (?, ?, 9.99)",
                categoryId, uniqueName());
    }

    @Test
    void createReturns201TrimsTheNameAndUsesTheApiResponseShape() {
        String name = uniqueName();

        asJson(writer.token()).body(Map.of("categoryName", "  " + name + "  ")).post(CATEGORIES).then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("message", notNullValue())
                .body("timestamp", notNullValue())
                .body("data.id", notNullValue())
                .body("data.categoryName", equalTo(name));
    }

    @Test
    void createRejectsInvalidNamesWith400() {
        assertError(asJson(writer.token()).body(Map.of("categoryName", "   ")).post(CATEGORIES).then(), 400)
                .body("message", containsString("must not be blank"));
        assertError(asJson(writer.token()).body(Map.of()).post(CATEGORIES).then(), 400);
        assertError(asJson(writer.token()).body(Map.of("categoryName", "x".repeat(151))).post(CATEGORIES).then(), 400)
                .body("message", containsString("at most 150"));
    }

    @Test
    void listUsesDefaultsAndReportsTotalsAndPages() {
        create(uniqueName());
        create(uniqueName());
        create(uniqueName());

        as(reader.token()).get(CATEGORIES).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.page", equalTo(0))
                .body("data.size", equalTo(20))
                .body("data.totalElements", greaterThanOrEqualTo(3))
                .body("data.content.size()", lessThanOrEqualTo(20));

        // size=1 makes every category its own page, so totalPages equals totalElements.
        var page = as(reader.token()).queryParam("page", 0).queryParam("size", 1).get(CATEGORIES).then()
                .statusCode(200).body("data.content", hasSize(1)).extract().jsonPath();
        assertEquals(page.getInt("data.totalElements"), page.getInt("data.totalPages"));
    }

    @Test
    void listIsSortedByIdAndEmptyBeyondTheEndStillSerializesAnArray() {
        create(uniqueName());
        create(uniqueName());

        var ids = as(reader.token()).queryParam("size", 100).get(CATEGORIES).then().statusCode(200)
                .extract().jsonPath().getList("data.content.id", Long.class);
        assertEquals(ids.stream().sorted().toList(), ids);

        as(reader.token()).queryParam("page", 100000).queryParam("size", 100).get(CATEGORIES).then()
                .statusCode(200)
                .body("data.content", hasSize(0))
                .body("data.page", equalTo(100000));
    }

    @Test
    void listAcceptsTheSizeBoundsAndRejectsOutOfRangeValuesWith400() {
        as(reader.token()).queryParam("size", 1).get(CATEGORIES).then().statusCode(200);
        as(reader.token()).queryParam("size", 100).get(CATEGORIES).then().statusCode(200);
        assertError(as(reader.token()).queryParam("size", 0).get(CATEGORIES).then(), 400);
        assertError(as(reader.token()).queryParam("size", 101).get(CATEGORIES).then(), 400);
        assertError(as(reader.token()).queryParam("page", -1).get(CATEGORIES).then(), 400);
    }

    @Test
    void detailReturns200Or404() {
        String name = uniqueName();
        long id = create(name);

        as(reader.token()).get(CATEGORIES + "/" + id).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.id", equalTo((int) id))
                .body("data.categoryName", equalTo(name));
        assertError(as(reader.token()).get(CATEGORIES + "/" + MISSING).then(), 404)
                .body("message", containsString("not found"));
    }

    @Test
    void updateRenamesAndRejectsUnknownIdsAndInvalidNames() {
        long id = create(uniqueName());
        String renamed = uniqueName();

        asJson(writer.token()).body(Map.of("categoryName", " " + renamed + " ")).put(CATEGORIES + "/" + id).then()
                .statusCode(200).body("success", equalTo(true)).body("data.categoryName", equalTo(renamed));
        as(reader.token()).get(CATEGORIES + "/" + id).then().body("data.categoryName", equalTo(renamed));

        assertError(asJson(writer.token()).body(Map.of("categoryName", uniqueName())).put(CATEGORIES + "/" + MISSING).then(), 404);
        assertError(asJson(writer.token()).body(Map.of("categoryName", " ")).put(CATEGORIES + "/" + id).then(), 400);
        assertError(asJson(writer.token()).body(Map.of("categoryName", "x".repeat(151))).put(CATEGORIES + "/" + id).then(), 400);
        as(reader.token()).get(CATEGORIES + "/" + id).then().body("data.categoryName", equalTo(renamed));
    }

    @Test
    void deleteRemovesTheCategoryAndReturns404WhenMissing() {
        long id = create(uniqueName());

        as(writer.token()).delete(CATEGORIES + "/" + id).then()
                .statusCode(200).body("success", equalTo(true)).body("data", org.hamcrest.Matchers.nullValue());
        assertError(as(writer.token()).get(CATEGORIES + "/" + id).then(), 404);
        assertError(as(writer.token()).delete(CATEGORIES + "/" + id).then(), 404);
    }

    // Non-regression: an endpoint without a body must not require a Content-Type, and must also accept the form
    // Content-Type that HTTP clients often add by default.
    @Test
    void deleteWithoutBodyWorksWithAFormContentType() {
        long id = create(uniqueName());
        as(writer.token()).contentType(ContentType.URLENC).delete(CATEGORIES + "/" + id).then().statusCode(200);
        long id2 = create(uniqueName());
        as(writer.token()).delete(CATEGORIES + "/" + id2).then().statusCode(200);
    }

    @Test
    void deleteIsRefusedWith422WhileAProductRemainsThenSucceeds() {
        String name = uniqueName();
        long id = create(name);
        insertProduct(id);
        insertProduct(id);

        assertError(as(writer.token()).delete(CATEGORIES + "/" + id).then(), 422)
                .body("message", containsString("2 product(s)"))
                .body("message", containsString(String.valueOf(id)));
        as(reader.token()).get(CATEGORIES + "/" + id).then().statusCode(200).body("data.categoryName", equalTo(name));

        rbac.db().execute("DELETE FROM products WHERE category_id = ?", id);
        as(writer.token()).delete(CATEGORIES + "/" + id).then().statusCode(200);
        assertEquals(0, rbac.db().queryLong("SELECT COUNT(*) FROM categories WHERE id = ?", id));
    }

    @Test
    void requestsWithoutATokenAreRejectedWith401() {
        given().get(CATEGORIES).then().statusCode(401);
        given().contentType(ContentType.JSON).body(Map.of("categoryName", uniqueName())).post(CATEGORIES).then().statusCode(401);
        given().delete(CATEGORIES + "/1").then().statusCode(401);
    }

    @Test
    void aReadTokenCannotWriteAndAWriteOnlyTokenCannotRead() {
        long id = create(uniqueName());
        Actor writeOnly = rbac.actor("CATEGORY:WRITE");

        assertError(asJson(reader.token()).body(Map.of("categoryName", uniqueName())).post(CATEGORIES).then(), 403);
        assertError(asJson(reader.token()).body(Map.of("categoryName", uniqueName())).put(CATEGORIES + "/" + id).then(), 403);
        assertError(as(reader.token()).delete(CATEGORIES + "/" + id).then(), 403);
        assertEquals(1, rbac.db().queryLong("SELECT COUNT(*) FROM categories WHERE id = ?", id));

        assertError(as(writeOnly.token()).get(CATEGORIES).then(), 403);
        assertError(as(writeOnly.token()).get(CATEGORIES + "/" + id).then(), 403);
    }
}
