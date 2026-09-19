package com.edgareldy.micronauttutorial.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.edgareldy.micronauttutorial.service.impl.AuthServiceImpl;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Shared fixture for every test that needs an authenticated user: unique emails, register + activate through the real endpoints, login for a bearer token, and a fast direct-to-database user. Create one per test and close it in @AfterEach.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// The raw activation and reset tokens exist nowhere but in an INFO log line (there is no mailer). To read
// them the way a person would, a Logback ListAppender is attached to the AuthServiceImpl logger: it keeps
// every log event in memory so the test can pick the token out of the message. close() detaches it and
// restores the previous level, so the capture never leaks into another test class.
public final class AuthTestSupport implements AutoCloseable {

    /** A valid password (8 to 72 characters) used by every fixture user. */
    public static final String PASSWORD = "Password123!";

    public static final String REGISTER = "/api/v1/auth/register";
    public static final String ACTIVATE = "/api/v1/auth/activate-account";
    public static final String LOGIN = "/api/v1/auth/login";
    public static final String LOGOUT = "/api/v1/auth/logout";
    public static final String ME = "/api/v1/auth/me";
    public static final String FORGOT = "/api/v1/auth/forgot-password";
    public static final String RESET = "/api/v1/auth/reset-password";

    private final TestDatabase database;
    private final Logger authLogger = (Logger) LoggerFactory.getLogger(AuthServiceImpl.class);
    private final Level previousLevel = authLogger.getLevel();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    // Cost 4 (the minimum) instead of the application's 10: only used to seed users quickly.
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    public AuthTestSupport(EmbeddedServer server, ConnectionOperations<Connection> connections) {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = server.getPort();
        this.database = new TestDatabase(connections);
        authLogger.setLevel(Level.INFO);
        appender.start();
        authLogger.addAppender(appender);
    }

    @Override
    public void close() {
        authLogger.detachAppender(appender);
        appender.stop();
        authLogger.setLevel(previousLevel);
    }

    public TestDatabase database() {
        return database;
    }

    /** A lower-case email that never collides with another test (the database is shared). */
    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
    }

    /** POST /register with the fixture password; the caller asserts on the response. */
    public Response register(String email) {
        return register(email, PASSWORD);
    }

    public Response register(String email, String password) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("firstName", "Ada", "lastName", "Lovelace", "email", email, "password", password))
                .post(REGISTER);
    }

    /** Registers and activates through the real endpoints, reading the token from the log. Returns the user id. */
    public long registerAndActivate(String email) {
        return registerAndActivate(email, PASSWORD);
    }

    public long registerAndActivate(String email, String password) {
        long id = register(email, password).then().statusCode(201).extract().jsonPath().getLong("data.id");
        given().queryParam("token", activationToken(id)).get(ACTIVATE).then().statusCode(200);
        return id;
    }

    /** POST /login; the caller asserts on the response. */
    public Response login(String email, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password)).post(LOGIN);
    }

    /** A bearer token obtained through a real login of an already active account. */
    public String loginToken(String email, String password) {
        return login(email, password).then().statusCode(200).extract().path("data.accessToken");
    }

    /** Registers, activates and logs in a brand new user; returns its bearer token. */
    public String newUserToken() {
        String email = uniqueEmail();
        registerAndActivate(email);
        return loginToken(email, PASSWORD);
    }

    /** Inserts an already enabled user directly in the database (bcrypt hash, no HTTP). Returns its id. */
    public long createEnabledUser(String email, String password) {
        return database.queryLong("INSERT INTO users (first_name, last_name, email, password, enabled) "
                + "VALUES ('Direct', 'User', ?, ?, TRUE) RETURNING id", email, encoder.encode(password));
    }

    /** The raw activation token logged when the user registered. */
    public String activationToken(long userId) {
        return loggedToken("Activation token for user " + userId + ": ");
    }

    /** The raw reset token logged by the latest forgot-password of the user. */
    public String resetToken(long userId) {
        return loggedToken("Password reset token for user " + userId + ": ");
    }

    private String loggedToken(String prefix) {
        List<ILoggingEvent> events = List.copyOf(appender.list);
        for (int i = events.size() - 1; i >= 0; i--) {
            String message = events.get(i).getFormattedMessage();
            if (message.startsWith(prefix)) {
                return message.substring(prefix.length());
            }
        }
        throw new IllegalStateException("No log line starting with '" + prefix + "'");
    }

    /** Bearer Authorization header value. */
    public static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Upper-cases an email so tests state the intent: emails are compared case-insensitively. */
    public static String shout(String email) {
        return email.toUpperCase(Locale.ROOT);
    }
}
