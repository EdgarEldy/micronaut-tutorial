package com.edgareldy.micronauttutorial.controller;

import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;

/**
 * Checks that /health and the Swagger endpoints are reachable without a token, and that /health
 * exposes nothing but the status.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@MicronautTest
class PublicEndpointsTest {

    @Inject
    EmbeddedServer server;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = server.getPort();
    }

    @Test
    void healthIsPublicAndExposesOnlyTheStatus() {
        Map<String, Object> body = given().get("/health").then().statusCode(200)
                .extract().jsonPath().getMap("$");

        // Exact equality: no details, no null fields leaking from the serializer.
        org.junit.jupiter.api.Assertions.assertEquals(Map.of("status", "UP"), body);
    }

    @Test
    void swaggerUiIsPublic() {
        given().get("/swagger-ui/index.html").then().statusCode(200);
    }

    @Test
    void openApiDescriptionIsPublicAndNotEmpty() {
        given().get("/swagger/micronaut-tutorial-0.1.yml").then()
                .statusCode(200)
                .body(not(emptyString()))
                .body(containsString("openapi"));
    }
}
