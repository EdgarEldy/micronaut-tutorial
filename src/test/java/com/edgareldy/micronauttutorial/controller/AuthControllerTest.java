package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.support.AuthTestSupport;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static com.edgareldy.micronauttutorial.support.AuthTestSupport.ACTIVATE;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.FORGOT;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.LOGOUT;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.ME;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.PASSWORD;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.RESET;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.bearer;
import static com.edgareldy.micronauttutorial.support.AuthTestSupport.uniqueEmail;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End to end tests of the auth endpoints over real HTTP: register, activate, login, /me, logout, password
 * reset, and every rejection asserted in the ApiResponse error format.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// transactional = false: by default @MicronautTest wraps each test in a transaction rolled back at the end,
// and rows written from the test thread (the JDBC fixtures here) would stay invisible to the HTTP server
// threads. The database is shared by all test classes, so every test uses its own unique email.
@MicronautTest(transactional = false)
class AuthControllerTest {

    private static final String ACTIVATION_ERROR = "Invalid or expired activation token";
    private static final String RESET_ERROR = "Invalid or expired password reset token";

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

    private static ValidatableResponse assertError(ValidatableResponse response, int status) {
        return response.statusCode(status)
                .body("success", equalTo(false))
                .body("message", notNullValue())
                .body("data", nullValue())
                .body("containsKey('data')", equalTo(true))
                .body("timestamp", notNullValue());
    }

    private static String message(io.restassured.response.Response response) {
        return response.then().extract().path("message");
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void registerActivateLoginAndReadProfile() {
        String email = uniqueEmail();

        long id = support.register(email).then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("timestamp", notNullValue())
                .body("data.email", equalTo(email))
                .body("data.enabled", equalTo(false))
                .body("data.permissions", hasSize(0))
                .body("data", not(org.hamcrest.Matchers.hasKey("password")))
                .extract().jsonPath().getLong("data.id");

        given().queryParam("token", support.activationToken(id)).get(ACTIVATE).then()
                .statusCode(200).body("success", equalTo(true)).body("data", nullValue());

        String token = support.login(email, PASSWORD).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.tokenType", equalTo("Bearer"))
                .body("data.expiresIn", equalTo(3600))
                .body("data.accessToken", notNullValue())
                .extract().path("data.accessToken");

        given().header("Authorization", bearer(token)).get(ME).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.id", equalTo((int) id))
                .body("data.email", equalTo(email))
                .body("data.firstName", equalTo("Ada"))
                .body("data.enabled", equalTo(true))
                .body("data.permissions", equalTo(List.of()))
                .body("data", not(org.hamcrest.Matchers.hasKey("password")));
    }

    @Test
    void logoutRevokesOnlyTheTokenUsed() {
        String email = uniqueEmail();
        support.registerAndActivate(email);
        String first = support.loginToken(email, PASSWORD);
        String second = support.loginToken(email, PASSWORD);

        given().header("Authorization", bearer(first)).post(LOGOUT).then()
                .statusCode(200).body("success", equalTo(true));

        assertError(given().header("Authorization", bearer(first)).get(ME).then(), 401);
        assertError(given().header("Authorization", bearer(first)).post(LOGOUT).then(), 401);
        // The other session of the same user is untouched.
        given().header("Authorization", bearer(second)).get(ME).then().statusCode(200);
    }

    /**
     * Non-regression: logout takes no body, so it must not require a Content-Type. REST Assured adds a form
     * Content-Type to every POST, so the JDK client is used to send a request without any.
     */
    @Test
    void logoutWithoutContentTypeWorks() throws Exception {
        String email = uniqueEmail();
        support.registerAndActivate(email);
        String token = support.loginToken(email, PASSWORD);

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + LOGOUT))
                .header("Authorization", bearer(token)).POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        given().header("Authorization", bearer(token)).get(ME).then().statusCode(401);
    }

    @Test
    void forgotThenResetChangesThePassword() {
        String email = uniqueEmail();
        long id = support.registerAndActivate(email);

        given().contentType(ContentType.JSON).body(Map.of("email", email)).post(FORGOT).then().statusCode(200);
        String raw = support.resetToken(id);

        given().contentType(ContentType.JSON).body(Map.of("token", raw, "newPassword", "NewPassword456!"))
                .post(RESET).then().statusCode(200).body("success", equalTo(true));

        support.login(email, "NewPassword456!").then().statusCode(200);
        assertError(support.login(email, PASSWORD).then(), 401);

        // One-shot: the same token cannot be replayed.
        assertError(given().contentType(ContentType.JSON).body(Map.of("token", raw, "newPassword", "Another789!"))
                .post(RESET).then(), 422).body("message", equalTo(RESET_ERROR));
    }

    @Test
    void resetWithUnknownOrExpiredTokenIs422() {
        String email = uniqueEmail();
        long id = support.registerAndActivate(email);
        given().contentType(ContentType.JSON).body(Map.of("email", email)).post(FORGOT).then().statusCode(200);
        String raw = support.resetToken(id);
        support.database().execute(
                "UPDATE password_reset_tokens SET expiry_date = now() - interval '1 hour' WHERE user_id = ?", id);

        assertError(given().contentType(ContentType.JSON).body(Map.of("token", raw, "newPassword", "NewPassword456!"))
                .post(RESET).then(), 422).body("message", equalTo(RESET_ERROR));
        assertError(given().contentType(ContentType.JSON).body(Map.of("token", "nope", "newPassword", "NewPassword456!"))
                .post(RESET).then(), 422).body("message", equalTo(RESET_ERROR));
        // The password did not change.
        support.login(email, PASSWORD).then().statusCode(200);
    }

    // ---------------------------------------------------------------- registration

    @Test
    void duplicateEmailIs422EvenWithAnotherCase() {
        String email = uniqueEmail();
        support.register(email).then().statusCode(201);

        assertError(support.register(email).then(), 422).body("message", equalTo("Email is already registered"));
        assertError(support.register(AuthTestSupport.shout(email)).then(), 422)
                .body("message", equalTo("Email is already registered"));
    }

    @Test
    void emailsAreCaseInsensitiveAtLogin() {
        String email = uniqueEmail();
        support.registerAndActivate(email);

        support.login(AuthTestSupport.shout(email), PASSWORD).then().statusCode(200);
    }

    @Test
    void registerValidationListsEachField() {
        assertError(given().contentType(ContentType.JSON)
                .body(Map.of("firstName", " ", "lastName", "", "email", "not-an-email", "password", "short"))
                .post(AuthTestSupport.REGISTER).then(), 400)
                .body("message", containsString("firstName: must not be blank"))
                .body("message", containsString("lastName: must not be blank"))
                .body("message", containsString("email: must be a well-formed email address"))
                .body("message", containsString("password: size must be between 8 and 72"));
    }

    @Test
    void registerRejectsAPasswordLongerThan72Characters() {
        assertError(support.register(uniqueEmail(), "a".repeat(73)).then(), 400)
                .body("message", containsString("password: size must be between 8 and 72"));
    }

    // ---------------------------------------------------------------- activation

    @Test
    void activationFailuresAllShareTheSame422() {
        String email = uniqueEmail();
        long id = support.register(email).then().statusCode(201).extract().jsonPath().getLong("data.id");
        String raw = support.activationToken(id);

        assertError(given().queryParam("token", "unknown-token").get(ACTIVATE).then(), 422)
                .body("message", equalTo(ACTIVATION_ERROR));

        given().queryParam("token", raw).get(ACTIVATE).then().statusCode(200);
        // Reused token.
        assertError(given().queryParam("token", raw).get(ACTIVATE).then(), 422)
                .body("message", equalTo(ACTIVATION_ERROR));

        // Expired token: age it directly in the database.
        String other = uniqueEmail();
        long otherId = support.register(other).then().statusCode(201).extract().jsonPath().getLong("data.id");
        support.database().execute(
                "UPDATE activation_tokens SET expires_at = now() - interval '1 hour' WHERE user_id = ?", otherId);
        assertError(given().queryParam("token", support.activationToken(otherId)).get(ACTIVATE).then(), 422)
                .body("message", equalTo(ACTIVATION_ERROR));
        // And the account stays disabled.
        assertError(support.login(other, PASSWORD).then(), 403);
    }

    @Test
    void activationWithBlankOrMissingTokenIs400() {
        assertError(given().get(ACTIVATE).then(), 400);
        assertError(given().queryParam("token", " ").get(ACTIVATE).then(), 400)
                .body("message", containsString("token: must not be blank"));
    }

    // ---------------------------------------------------------------- login

    @Test
    void unknownEmailAndWrongPasswordAreIndistinguishable() {
        String email = uniqueEmail();
        support.registerAndActivate(email);

        var wrongPassword = support.login(email, "WrongPassword1!");
        var unknownEmail = support.login(uniqueEmail(), "WrongPassword1!");

        assertError(wrongPassword.then(), 401);
        assertError(unknownEmail.then(), 401);
        assertEquals(message(wrongPassword), message(unknownEmail));
        assertEquals("Invalid email or password", message(unknownEmail));
    }

    @Test
    void loginBeforeActivationIs403OnlyWithTheCorrectPassword() {
        String email = uniqueEmail();
        support.register(email).then().statusCode(201);

        assertError(support.login(email, PASSWORD).then(), 403).body("message", equalTo("Account is not activated"));
        // Wrong password: same 401 as any bad credentials, the account state is not revealed.
        assertError(support.login(email, "WrongPassword1!").then(), 401)
                .body("message", equalTo("Invalid email or password"));
    }

    @Test
    void lockedAccountIs403() {
        String email = uniqueEmail();
        long id = support.createEnabledUser(email, PASSWORD);
        support.database().execute("UPDATE users SET account_locked = TRUE WHERE id = ?", id);

        assertError(support.login(email, PASSWORD).then(), 403).body("message", equalTo("Account is locked"));
    }

    @Test
    void loginValidationIs400() {
        assertError(given().contentType(ContentType.JSON).body(Map.of("email", "bad", "password", " "))
                .post(AuthTestSupport.LOGIN).then(), 400)
                .body("message", containsString("email: must be a well-formed email address"))
                .body("message", containsString("password: must not be blank"));
    }

    // ---------------------------------------------------------------- authentication

    @Test
    void meWithoutTokenIs401() {
        assertError(given().get(ME).then(), 401).body("message", equalTo("Authentication required"));
    }

    @Test
    void logoutWithoutTokenIs401() {
        assertError(given().post(LOGOUT).then(), 401);
    }

    @Test
    void garbageBearerTokenIs401() {
        assertError(given().header("Authorization", bearer("not.a.jwt")).get(ME).then(), 401);
    }

    // ---------------------------------------------------------------- forgot password

    @Test
    void registerRefusesAMultibytePasswordOver72BytesInsteadOfFailing() {
        // 40 characters pass @Size(max = 72) but are 80 bytes in UTF-8, beyond what bcrypt accepts.
        assertError(support.register(uniqueEmail(), "\u00e9".repeat(40)).then(), 422)
                .body("message", containsString("72 bytes"));
    }

    @Test
    void loginWithAnOverLongPasswordIsAPlainMismatch() {
        String email = uniqueEmail();
        support.registerAndActivate(email);
        var wrong = support.login(email, "WrongPassword1!");
        var tooLong = support.login(email, "\u00e9".repeat(40));
        assertError(tooLong.then(), 401);
        assertEquals(message(wrong), message(tooLong));
    }

    @Test
    void repeatedForgotPasswordDoesNotInvalidateThePendingToken() {
        String email = uniqueEmail();
        long id = support.registerAndActivate(email);

        given().contentType(ContentType.JSON).body(Map.of("email", email)).post(FORGOT).then().statusCode(200);
        String first = support.resetToken(id);
        // An anonymous caller repeating the request must not wipe the victim's valid token.
        given().contentType(ContentType.JSON).body(Map.of("email", email)).post(FORGOT).then().statusCode(200);

        given().contentType(ContentType.JSON).body(Map.of("token", first, "newPassword", "NewPassword456!"))
                .post(RESET).then().statusCode(200);
    }

    @Test
    void forgotPasswordIsIdenticalForKnownAndUnknownEmails() {
        String known = uniqueEmail();
        support.registerAndActivate(known);

        var existing = given().contentType(ContentType.JSON).body(Map.of("email", known)).post(FORGOT);
        var missing = given().contentType(ContentType.JSON).body(Map.of("email", uniqueEmail())).post(FORGOT);

        existing.then().statusCode(200).body("success", equalTo(true)).body("data", nullValue());
        missing.then().statusCode(200).body("success", equalTo(true)).body("data", nullValue());
        assertEquals(message(existing), message(missing));
    }

    @Test
    void forgotPasswordValidatesTheEmail() {
        assertError(given().contentType(ContentType.JSON).body(Map.of("email", "bad")).post(FORGOT).then(), 400)
                .body("message", containsString("email: must be a well-formed email address"));
    }
}
