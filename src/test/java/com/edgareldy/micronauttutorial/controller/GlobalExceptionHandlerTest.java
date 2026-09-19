package com.edgareldy.micronauttutorial.controller;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end test of the error mapping: every failure must come back as an ApiResponse with
 * success=false, a message, data=null and a timestamp, with the right HTTP status.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @MicronautTest starts the real application (embedded Netty server on a random port) once for the
// class. The database is provisioned by Micronaut Test Resources (a PostgreSQL container started
// automatically because no datasource URL is configured), so no Testcontainers code is written here.
// REST Assured then talks to the server over real HTTP, using the port of the injected EmbeddedServer.
@MicronautTest
class GlobalExceptionHandlerTest {

    private static final String BASE = "/test-support";

    @Inject
    EmbeddedServer server;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = server.getPort();
    }

    private static ValidatableResponse assertError(ValidatableResponse response, int status) {
        return response.statusCode(status)
                .body("success", equalTo(false))
                .body("message", notNullValue())
                .body("data", nullValue())
                .body("containsKey('data')", equalTo(true))
                .body("timestamp", notNullValue());
    }

    @Test
    void successIsWrappedWithIso8601Timestamp() {
        String timestamp = given().get(BASE + "/success").then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("message", equalTo("all good"))
                .body("data", equalTo("data"))
                .extract().path("timestamp");

        // Instant.parse only accepts ISO-8601 text, so a numeric epoch would fail here.
        assertDoesNotThrow(() -> Instant.parse(timestamp));
    }

    @Test
    void resourceNotFoundIs404() {
        assertError(given().get(BASE + "/not-found").then(), 404)
                .body("message", equalTo("Thing 42 not found"));
    }

    @Test
    void businessRuleIs422() {
        assertError(given().get(BASE + "/business-rule").then(), 422)
                .body("message", equalTo("Rule violated"));
    }

    @Test
    void httpStatusExceptionKeepsItsStatus() {
        assertError(given().get(BASE + "/http-status").then(), 409)
                .body("message", equalTo("Already exists"));
    }

    @Test
    void unexpectedExceptionIs500WithGenericMessage() {
        assertError(given().get(BASE + "/unexpected").then(), 500)
                .body("message", equalTo("Internal server error"))
                .body("message", not(containsString("secret")));
    }

    @Test
    void queryConstraintViolationsListEachField() {
        assertError(given().queryParam("page", 0).queryParam("name", " ").get(BASE + "/query").then(), 400)
                .body("message", containsString("name: must not be blank"))
                .body("message", containsString("page: must be at least 1"));
    }

    @Test
    void bodyConstraintViolationsListEachField() {
        assertError(given().contentType(ContentType.JSON).body("{\"name\":\"\",\"quantity\":0}")
                .post(BASE + "/body").then(), 400)
                .body("message", containsString("name: must not be blank"))
                .body("message", containsString("quantity: must be at least 1"));
    }

    @Test
    void invalidIntPathVariableIs400() {
        assertError(given().get(BASE + "/items/abc").then(), 400)
                .body("message", containsString("id"));
    }

    @Test
    void missingRequiredQueryParamIs400() {
        assertError(given().get(BASE + "/required").then(), 400)
                .body("message", containsString("name"));
    }

    @Test
    void emptyBodyIs400() {
        assertError(given().contentType(ContentType.JSON).post(BASE + "/body").then(), 400)
                .body("message", equalTo("Request body is required"));
    }

    @Test
    void malformedJsonIs400() {
        assertError(given().contentType(ContentType.JSON).body("{not json")
                .post(BASE + "/body").then(), 400);
    }

    @Test
    void unknownRouteIs404() {
        assertError(given().get(BASE + "/does-not-exist").then(), 404);
    }

    @Test
    void wrongMethodIs405() {
        assertError(given().get(BASE + "/no-body").then(), 405);
    }

    @Test
    void unsupportedContentTypeIs415() {
        assertError(given().contentType(ContentType.TEXT).body("hello").post(BASE + "/body").then(), 415);
    }

    /**
     * Non-regression: an endpoint that takes no body must not demand a Content-Type header.
     * REST Assured adds a form Content-Type to every POST by default, so the raw JDK client is used
     * to send a request that really carries no Content-Type.
     */
    @Test
    void postWithoutBodyDoesNotRequireContentType() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + BASE + "/no-body"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"success\":true"), response.body());
    }
}
