package com.edgareldy.micronauttutorial.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.edgareldy.micronauttutorial.repository.RoleRepository;
import com.edgareldy.micronauttutorial.repository.UserRepository;
import com.edgareldy.micronauttutorial.service.AuditLogger;
import com.edgareldy.micronauttutorial.support.AuthTestSupport;
import com.edgareldy.micronauttutorial.support.TestDatabase;
import io.micronaut.context.annotation.Property;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Base64;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of AdminBootstrap: with the properties set the application starts with an enabled ADMIN account able to
 * log in with all 14 permissions in its token, the bootstrap is idempotent, and the password never reaches the logs.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Same package as AdminBootstrap because bootstrap() is package-private. @Property overrides configuration for
// this test class only: Micronaut builds a dedicated application context (and so runs the startup listener with
// these values) while sharing the same Test Resources database as the other test classes.
@MicronautTest(transactional = false)
@Property(name = "app.bootstrap-admin.email", value = AdminBootstrapTest.EMAIL)
@Property(name = "app.bootstrap-admin.password", value = AdminBootstrapTest.PASSWORD)
class AdminBootstrapTest {

    static final String EMAIL = "bootstrap-admin-test@example.com";
    static final String PASSWORD = "Bootstrap-Test-Secret-42";

    @Inject
    EmbeddedServer server;

    @Inject
    ConnectionOperations<Connection> connections;

    @Inject
    AdminBootstrap bootstrap;

    @Inject
    UserRepository users;

    @Inject
    RoleRepository roles;

    @Inject
    BCryptPasswordEncoder encoder;

    @Inject
    AuditLogger audit;

    private void removeRows(String... emails) {
        TestDatabase db = new TestDatabase(connections);
        for (String email : emails) {
            Long id = users.findByEmail(email).map(u -> u.getId()).orElse(null);
            if (id != null) {
                db.execute("DELETE FROM audit_logs WHERE entity_type = 'USER' AND entity_id = ? AND action = 'BOOTSTRAP_ADMIN_CREATE'", id);
                db.removeUser(id);
            }
        }
    }

    @Test
    void theConfiguredAdminExistsCanLogInAndCarriesEveryPermission() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = server.getPort();
        try {
            TestDatabase db = new TestDatabase(connections);
            assertEquals(1, db.queryLong("SELECT COUNT(*) FROM users WHERE email = ? AND enabled AND NOT account_locked", EMAIL));
            assertEquals(1, db.queryLong("SELECT COUNT(*) FROM users u JOIN role_user ru ON ru.user_id = u.id "
                    + "JOIN roles r ON r.id = ru.role_id WHERE u.email = ? AND r.role_name = 'ADMIN'", EMAIL));

            String token = given().contentType("application/json")
                    .body(java.util.Map.of("email", EMAIL, "password", PASSWORD)).post(AuthTestSupport.LOGIN).then()
                    .statusCode(200).extract().path("data.accessToken");

            String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
            List<String> claim = JsonPath.from(payload).getList("permissions");
            assertEquals(14, claim.size());
            given().header("Authorization", AuthTestSupport.bearer(token)).get(AuthTestSupport.ME).then()
                    .statusCode(200).body("data.permissions", hasSize(14)).body("data.email", equalTo(EMAIL));
        } finally {
            removeRows(EMAIL);
        }
    }

    @Test
    void bootstrapIsIdempotentAndNeverLogsThePassword() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level previous = logger.getLevel();
        // Capture every log event (root logger, DEBUG) to prove neither the password nor its hash is written.
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        String email = "bootstrap-second-" + System.nanoTime() + "@example.com";
        String secret = "Second-Secret-" + System.nanoTime();
        AdminBootstrap second = new AdminBootstrap(new BootstrapAdminProperties(email, secret), users, roles, encoder, audit);
        try {
            assertTrue(second.bootstrap());
            assertFalse(second.bootstrap(), "second call must be a no-op");
            assertTrue(users.existsByEmail(email));

            String hash = new TestDatabase(connections).queryString("SELECT password FROM users WHERE email = ?", email);
            for (ILoggingEvent event : List.copyOf(appender.list)) {
                String message = event.getFormattedMessage();
                assertFalse(message.contains(secret), "password leaked: " + message);
                // Only the application's own loggers are held to the hash rule: at DEBUG the Hibernate
                // EntityPrinter (framework code) dumps every entity property, hash included.
                if (event.getLoggerName().startsWith("com.edgareldy")) {
                    assertFalse(message.contains(hash), "hash leaked: " + message);
                }
            }
            assertFalse(new BootstrapAdminProperties(email, secret).toString().contains(secret));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previous);
            removeRows(email);
        }
    }
}
