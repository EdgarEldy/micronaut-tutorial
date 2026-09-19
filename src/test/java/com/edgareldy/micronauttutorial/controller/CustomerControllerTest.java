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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End to end tests of /api/v1/customers over real HTTP: every endpoint, the optional fields (null keys, blank to null), validation, pagination bounds and the permission checks (401 and 403), all in the ApiResponse format.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Same setup as CategoryControllerTest: transactional = false, tokens from a real login. Every customer created here
// has a last name starting with QA_CUS_ and is swept after each test, since the database is shared.
@MicronautTest(transactional = false)
class CustomerControllerTest {

    private static final String CUSTOMERS = "/api/v1/customers";
    private static final String PREFIX = "QA_CUS_";

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
        writer = rbac.actor("CUSTOMER:READ", "CUSTOMER:WRITE");
        reader = rbac.actor("CUSTOMER:READ");
    }

    @AfterEach
    void tearDown() {
        rbac.db().execute("DELETE FROM customers WHERE last_name LIKE 'QA\\_CUS\\_%'");
        rbac.close();
    }

    private static String uniqueLastName() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private static Map<String, Object> body(String lastName) {
        Map<String, Object> m = new HashMap<>();
        m.put("firstName", "Ada");
        m.put("lastName", lastName);
        return m;
    }

    private long create(String lastName) {
        return asJson(writer.token()).body(body(lastName)).post(CUSTOMERS).then().statusCode(201)
                .extract().jsonPath().getLong("data.id");
    }

    @Test
    void createReturns201WithAllFields() {
        String last = uniqueLastName();
        Map<String, Object> m = body(last);
        m.put("telephone", "+250788000000");
        m.put("email", "ada@example.com");
        m.put("address", "1 Kigali Road");

        asJson(writer.token()).body(m).post(CUSTOMERS).then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("message", notNullValue())
                .body("timestamp", notNullValue())
                .body("data.id", notNullValue())
                .body("data.firstName", equalTo("Ada"))
                .body("data.lastName", equalTo(last))
                .body("data.telephone", equalTo("+250788000000"))
                .body("data.email", equalTo("ada@example.com"))
                .body("data.address", equalTo("1 Kigali Road"));
    }

    // The optional fields must be serialized as explicit nulls, not omitted, so clients see a stable shape.
    @Test
    void createWithOnlyTheNamesSerializesTheOptionalKeysAsNull() {
        asJson(writer.token()).body(body(uniqueLastName())).post(CUSTOMERS).then()
                .statusCode(201)
                .body("data", hasKey("telephone"))
                .body("data", hasKey("email"))
                .body("data", hasKey("address"))
                .body("data.telephone", nullValue())
                .body("data.email", nullValue())
                .body("data.address", nullValue());
    }

    @Test
    void createTrimsValuesAndTurnsBlankOptionalsIntoNull() {
        String last = uniqueLastName();
        Map<String, Object> m = new HashMap<>();
        m.put("firstName", "  Ada  ");
        m.put("lastName", "  " + last + "  ");
        m.put("telephone", "   ");
        m.put("address", "  Rue 5  ");

        asJson(writer.token()).body(m).post(CUSTOMERS).then()
                .statusCode(201)
                .body("data.firstName", equalTo("Ada"))
                .body("data.lastName", equalTo(last))
                .body("data", hasKey("telephone"))
                .body("data.telephone", nullValue())
                .body("data.email", nullValue())
                .body("data.address", equalTo("Rue 5"));
    }

    // The DTO documents that a blank optional means absent, so an empty email must be stored as null, not rejected.
    @Test
    void createTurnsAnEmptyEmailIntoNull() {
        Map<String, Object> m = body(uniqueLastName());
        m.put("email", "");

        asJson(writer.token()).body(m).post(CUSTOMERS).then()
                .statusCode(201)
                .body("data", hasKey("email"))
                .body("data.email", nullValue());
    }

    @Test
    void createRejectsInvalidBodiesWith400AndPerFieldMessages() {
        Map<String, Object> blankFirst = body(uniqueLastName());
        blankFirst.put("firstName", "  ");
        assertError(asJson(writer.token()).body(blankFirst).post(CUSTOMERS).then(), 400)
                .body("message", containsString("firstName"))
                .body("message", containsString("must not be blank"));

        assertError(asJson(writer.token()).body(body("   ")).post(CUSTOMERS).then(), 400)
                .body("message", containsString("lastName"))
                .body("message", containsString("must not be blank"));

        Map<String, Object> badEmail = body(uniqueLastName());
        badEmail.put("email", "not-an-email");
        assertError(asJson(writer.token()).body(badEmail).post(CUSTOMERS).then(), 400)
                .body("message", containsString("valid email"));

        Map<String, Object> longFirst = body(uniqueLastName());
        longFirst.put("firstName", "x".repeat(101));
        assertError(asJson(writer.token()).body(longFirst).post(CUSTOMERS).then(), 400)
                .body("message", containsString("at most 100"));

        assertError(asJson(writer.token()).body(body("x".repeat(101))).post(CUSTOMERS).then(), 400)
                .body("message", containsString("at most 100"));

        Map<String, Object> longPhone = body(uniqueLastName());
        longPhone.put("telephone", "1".repeat(51));
        assertError(asJson(writer.token()).body(longPhone).post(CUSTOMERS).then(), 400)
                .body("message", containsString("at most 50"));

        Map<String, Object> longAddress = body(uniqueLastName());
        longAddress.put("address", "a".repeat(256));
        assertError(asJson(writer.token()).body(longAddress).post(CUSTOMERS).then(), 400)
                .body("message", containsString("at most 255"));

        Map<String, Object> longEmail = body(uniqueLastName());
        longEmail.put("email", "a".repeat(60) + "@" + ("b".repeat(60) + ".").repeat(4) + "com");
        assertError(asJson(writer.token()).body(longEmail).post(CUSTOMERS).then(), 400)
                .body("message", containsString("at most 255"));
    }

    @Test
    void listUsesDefaultsAndReportsTotalsAndPages() {
        create(uniqueLastName());
        create(uniqueLastName());
        create(uniqueLastName());

        as(reader.token()).get(CUSTOMERS).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.page", equalTo(0))
                .body("data.size", equalTo(20))
                .body("data.totalElements", greaterThanOrEqualTo(3))
                .body("data.content.size()", lessThanOrEqualTo(20));

        var page = as(reader.token()).queryParam("page", 0).queryParam("size", 1).get(CUSTOMERS).then()
                .statusCode(200).body("data.content", hasSize(1)).extract().jsonPath();
        assertEquals(page.getInt("data.totalElements"), page.getInt("data.totalPages"));
    }

    @Test
    void listIsSortedByIdAndEmptyBeyondTheEndStillSerializesAnArray() {
        create(uniqueLastName());
        create(uniqueLastName());

        var ids = as(reader.token()).queryParam("size", 100).get(CUSTOMERS).then().statusCode(200)
                .extract().jsonPath().getList("data.content.id", Long.class);
        assertEquals(ids.stream().sorted().toList(), ids);

        as(reader.token()).queryParam("page", 100000).queryParam("size", 100).get(CUSTOMERS).then()
                .statusCode(200)
                .body("data.content", hasSize(0))
                .body("data.page", equalTo(100000));
    }

    @Test
    void listAcceptsTheSizeBoundsAndRejectsOutOfRangeValuesWith400() {
        as(reader.token()).queryParam("size", 1).get(CUSTOMERS).then().statusCode(200);
        as(reader.token()).queryParam("size", 100).get(CUSTOMERS).then().statusCode(200);
        assertError(as(reader.token()).queryParam("size", 0).get(CUSTOMERS).then(), 400);
        assertError(as(reader.token()).queryParam("size", 101).get(CUSTOMERS).then(), 400);
        assertError(as(reader.token()).queryParam("page", -1).get(CUSTOMERS).then(), 400);
    }

    @Test
    void detailReturns200Or404() {
        String last = uniqueLastName();
        long id = create(last);

        as(reader.token()).get(CUSTOMERS + "/" + id).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.id", equalTo((int) id))
                .body("data.lastName", equalTo(last));
        assertError(as(reader.token()).get(CUSTOMERS + "/" + MISSING).then(), 404)
                .body("message", containsString("not found"));
    }

    @Test
    void updateReplacesFieldsAndClearingOptionalsSetsThemToNull() {
        String last = uniqueLastName();
        Map<String, Object> full = body(last);
        full.put("telephone", "123");
        full.put("email", "old@example.com");
        full.put("address", "Old street");
        long id = asJson(writer.token()).body(full).post(CUSTOMERS).then().statusCode(201)
                .extract().jsonPath().getLong("data.id");

        String renamed = uniqueLastName();
        Map<String, Object> cleared = body(renamed);
        cleared.put("firstName", " Grace ");
        cleared.put("telephone", " ");
        asJson(writer.token()).body(cleared).put(CUSTOMERS + "/" + id).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.firstName", equalTo("Grace"))
                .body("data.lastName", equalTo(renamed))
                .body("data", hasKey("telephone"))
                .body("data.telephone", nullValue())
                .body("data.email", nullValue())
                .body("data.address", nullValue());
        as(reader.token()).get(CUSTOMERS + "/" + id).then()
                .body("data.firstName", equalTo("Grace"))
                .body("data.email", nullValue());
    }

    @Test
    void updateRejectsUnknownIdsAndInvalidBodies() {
        String last = uniqueLastName();
        long id = create(last);

        assertError(asJson(writer.token()).body(body(uniqueLastName())).put(CUSTOMERS + "/" + MISSING).then(), 404);
        assertError(asJson(writer.token()).body(body(" ")).put(CUSTOMERS + "/" + id).then(), 400);
        Map<String, Object> badEmail = body(uniqueLastName());
        badEmail.put("email", "nope");
        assertError(asJson(writer.token()).body(badEmail).put(CUSTOMERS + "/" + id).then(), 400);
        as(reader.token()).get(CUSTOMERS + "/" + id).then().body("data.lastName", equalTo(last));
    }

    @Test
    void deleteRemovesTheCustomerAndReturns404WhenMissing() {
        long id = create(uniqueLastName());

        as(writer.token()).delete(CUSTOMERS + "/" + id).then()
                .statusCode(200).body("success", equalTo(true)).body("data", nullValue());
        assertError(as(writer.token()).get(CUSTOMERS + "/" + id).then(), 404);
        assertError(as(writer.token()).delete(CUSTOMERS + "/" + id).then(), 404);
    }

    // Non-regression: an endpoint without a body must not require a Content-Type, and must accept a form one.
    @Test
    void deleteWithoutBodyWorksWithAFormContentType() {
        long id = create(uniqueLastName());
        as(writer.token()).contentType(ContentType.URLENC).delete(CUSTOMERS + "/" + id).then().statusCode(200);
        long id2 = create(uniqueLastName());
        as(writer.token()).delete(CUSTOMERS + "/" + id2).then().statusCode(200);
    }

    @Test
    void requestsWithoutATokenAreRejectedWith401() {
        given().get(CUSTOMERS).then().statusCode(401);
        given().contentType(ContentType.JSON).body(body(uniqueLastName())).post(CUSTOMERS).then().statusCode(401);
        given().delete(CUSTOMERS + "/1").then().statusCode(401);
    }

    @Test
    void aReadTokenCannotWriteAndAWriteOnlyTokenCannotRead() {
        long id = create(uniqueLastName());
        Actor writeOnly = rbac.actor("CUSTOMER:WRITE");

        assertError(asJson(reader.token()).body(body(uniqueLastName())).post(CUSTOMERS).then(), 403);
        assertError(asJson(reader.token()).body(body(uniqueLastName())).put(CUSTOMERS + "/" + id).then(), 403);
        assertError(as(reader.token()).delete(CUSTOMERS + "/" + id).then(), 403);
        assertEquals(1, rbac.db().queryLong("SELECT COUNT(*) FROM customers WHERE id = ?", id));

        assertError(as(writeOnly.token()).get(CUSTOMERS).then(), 403);
        assertError(as(writeOnly.token()).get(CUSTOMERS + "/" + id).then(), 403);
    }
}
