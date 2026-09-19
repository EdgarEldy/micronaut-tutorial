package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.support.AuthTestSupport;
import com.edgareldy.micronauttutorial.support.TestDatabase;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of ExpiredTokenCleanupJob: an expired row is deleted and a still valid one is kept, in each of the three
 * token tables. The job method is called directly instead of waiting for its cron expression.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// transactional = false: the rows must be committed to be visible to the job, which runs its own transaction.
@MicronautTest(transactional = false)
class ExpiredTokenCleanupJobTest {

    @Inject
    ExpiredTokenCleanupJob job;

    @Inject
    ConnectionOperations<Connection> connections;

    TestDatabase db;
    long userId;

    @BeforeEach
    void setUp() {
        db = new TestDatabase(connections);
        userId = db.queryLong("INSERT INTO users (first_name, last_name, email, password) VALUES ('Job', 'Test', ?, 'x') RETURNING id",
                AuthTestSupport.uniqueEmail());
    }

    @AfterEach
    void tearDown() {
        // The token rows of the user go with it (ON DELETE CASCADE).
        db.removeUser(userId);
    }

    private static String random() {
        return UUID.randomUUID().toString();
    }

    @Test
    void deletesExpiredRowsOfTheThreeTablesAndKeepsTheValidOnes() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        String expiredActivation = random();
        String validActivation = random();
        String expiredReset = random();
        String validReset = random();
        String expiredJti = random();
        String validJti = random();
        insertActivation(expiredActivation, past);
        insertActivation(validActivation, future);
        insertReset(expiredReset, past);
        insertReset(validReset, future);
        insertBlacklisted(expiredJti, past);
        insertBlacklisted(validJti, future);

        ExpiredTokenCleanupJob.Result result = job.purgeExpired();

        assertTrue(result.activation() >= 1);
        assertTrue(result.passwordReset() >= 1);
        assertTrue(result.blacklisted() >= 1);
        assertEquals(0, count("activation_tokens", "token", expiredActivation));
        assertEquals(1, count("activation_tokens", "token", validActivation));
        assertEquals(0, count("password_reset_tokens", "token", expiredReset));
        assertEquals(1, count("password_reset_tokens", "token", validReset));
        assertEquals(0, count("blacklisted_tokens", "jti", expiredJti));
        assertEquals(1, count("blacklisted_tokens", "jti", validJti));
    }

    @Test
    void aSecondRunFindsNothingLeftToDeleteForTheSameRows() {
        insertActivation(random(), Instant.now().minus(1, ChronoUnit.DAYS));
        job.purgeExpired();

        ExpiredTokenCleanupJob.Result second = job.purgeExpired();

        assertEquals(0, second.activation());
    }

    private void insertActivation(String token, Instant expiresAt) {
        db.execute("INSERT INTO activation_tokens (user_id, token, expires_at) VALUES (?, ?, ?)", userId, token,
                java.sql.Timestamp.from(expiresAt));
    }

    private void insertReset(String token, Instant expiry) {
        db.execute("INSERT INTO password_reset_tokens (user_id, token, type, expiry_date) VALUES (?, ?, 'PASSWORD_RESET', ?)",
                userId, token, java.sql.Timestamp.from(expiry));
    }

    private void insertBlacklisted(String jti, Instant expiresAt) {
        db.execute("INSERT INTO blacklisted_tokens (user_id, token, jti, expires_at) VALUES (?, ?, ?, ?)", userId,
                "jwt-" + jti, jti, java.sql.Timestamp.from(expiresAt));
    }

    private long count(String table, String column, String value) {
        return db.queryLong("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", value);
    }
}
