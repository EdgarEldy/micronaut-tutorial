package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.security.TokenHasher;
import com.edgareldy.micronauttutorial.support.AuthTestSupport;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.edgareldy.micronauttutorial.support.AuthTestSupport.FORGOT;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.LOGOUT;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.ME;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.PASSWORD;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.bearer;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.uniqueEmail;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Security properties checked against the database and the JWT itself: tokens stored only as SHA-256, the
 * claims of an issued JWT, and permissions resolved from the role tables into the token.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@MicronautTest(transactional = false)
class AuthTokenSecurityTest {

    @Inject
    EmbeddedServer server;

    @Inject
    ConnectionOperations<Connection> connections;

    AuthTestSupport support;

    @BeforeEach
    void setUp() {
        support = new AuthTestSupport(server, connections);
    }

    @AfterEach
    void tearDown() {
        support.close();
    }

    private static JsonPath claimsOf(String jwt) {
        String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
        return JsonPath.from(payload);
    }

    @Test
    void activationTokenIsStoredOnlyAsSha256() {
        long id = support.register(uniqueEmail()).then().statusCode(201).extract().jsonPath().getLong("data.id");
        String raw = support.activationToken(id);

        String stored = support.database().queryString("SELECT token FROM activation_tokens WHERE user_id = ?", id);

        assertEquals(TokenHasher.sha256Hex(raw), stored);
        assertNotEquals(raw, stored);
    }

    @Test
    void resetTokenIsStoredOnlyAsSha256() {
        String email = uniqueEmail();
        long id = support.registerAndActivate(email);
        given().contentType(ContentType.JSON).body(Map.of("email", email)).post(FORGOT).then().statusCode(200);
        String raw = support.resetToken(id);

        String stored = support.database().queryString("SELECT token FROM password_reset_tokens WHERE user_id = ?", id);

        assertEquals(TokenHasher.sha256Hex(raw), stored);
        assertNotEquals(raw, stored);
    }

    @Test
    void blacklistedTokenColumnHoldsTheHashOfTheJwtNotTheJwt() {
        String email = uniqueEmail();
        long id = support.registerAndActivate(email);
        String jwt = support.loginToken(email, PASSWORD);
        String jti = claimsOf(jwt).getString("jti");

        given().header("Authorization", bearer(jwt)).post(LOGOUT).then().statusCode(200);

        String stored = support.database().queryString("SELECT token FROM blacklisted_tokens WHERE jti = ?", jti);
        assertEquals(TokenHasher.sha256Hex(jwt), stored);
        assertNotEquals(jwt, stored);
        assertEquals(id, support.database().queryLong("SELECT user_id FROM blacklisted_tokens WHERE jti = ?", jti));
    }

    @Test
    void passwordIsStoredAsBcryptHash() {
        String email = uniqueEmail();
        support.register(email).then().statusCode(201);

        String stored = support.database().queryString("SELECT password FROM users WHERE email = ?", email);

        assertNotEquals(PASSWORD, stored);
        assertEquals(true, stored.startsWith("$2"));
    }

    @Test
    void jwtCarriesSubjectEmailPermissionsAndAUniqueJti() {
        String email = uniqueEmail();
        long id = support.registerAndActivate(email);

        String first = support.loginToken(email, PASSWORD);
        String second = support.loginToken(email, PASSWORD);

        JsonPath claims = claimsOf(first);
        assertEquals(String.valueOf(id), claims.getString("sub"));
        assertEquals(email, claims.getString("email"));
        assertNotNull(claims.getString("jti"));
        assertNotNull(claims.get("exp"));
        assertEquals(List.of(), claims.getList("permissions"));
        assertNotEquals(claims.getString("jti"), claimsOf(second).getString("jti"));
    }

    @Test
    void permissionsFromTheRoleTablesAreEmbeddedInTheTokenAndReturnedByMe() {
        String email = uniqueEmail();
        long id = support.registerAndActivate(email);
        String action = "ACT" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        long roleId = support.database().grantPermission(id, "AUTHTEST", action);
        try {
            String token = support.loginToken(email, PASSWORD);

            assertEquals(List.of("AUTHTEST:" + action), claimsOf(token).getList("permissions"));
            given().header("Authorization", bearer(token)).get(ME).then().statusCode(200)
                    .body("data.permissions", equalTo(List.of("AUTHTEST:" + action)));
        } finally {
            support.database().removeRole(roleId);
            support.database().removePermission("AUTHTEST", action);
        }
    }
}
