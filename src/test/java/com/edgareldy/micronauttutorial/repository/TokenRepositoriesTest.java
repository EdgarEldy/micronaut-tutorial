package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.ActivationToken;
import com.edgareldy.micronauttutorial.entity.BlacklistedToken;
import com.edgareldy.micronauttutorial.entity.PasswordResetToken;
import com.edgareldy.micronauttutorial.entity.User;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.edgareldy.micronauttutorial.support.AuthTestSupport.uniqueEmail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository tests of the three token repositories: the one-shot atomic updates and deletes, and the jti
 * lookup and expiry purge of the blacklist.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// transactional = false: every repository call commits by itself, exactly like in production, which is what
// the "returns 1 for the first caller and 0 for the second" contract of these queries relies on.
@MicronautTest(transactional = false)
class TokenRepositoriesTest {

    @Inject
    UserRepository users;

    @Inject
    ActivationTokenRepository activationTokens;

    @Inject
    PasswordResetTokenRepository resetTokens;

    @Inject
    BlacklistedTokenRepository blacklistedTokens;

    private Long userId;

    @BeforeEach
    void createUser() {
        userId = users.save(new User("Ada", "Lovelace", uniqueEmail(), "hash")).getId();
    }

    @AfterEach
    void deleteUser() {
        // The tokens of the user are removed by ON DELETE CASCADE.
        users.deleteById(userId);
    }

    private static String random() {
        return UUID.randomUUID().toString();
    }

    @Test
    void activationTokenMarkValidatedIsOneShot() {
        Instant now = Instant.now();
        String hash = random();
        ActivationToken saved = activationTokens.save(new ActivationToken(userId, hash, now, now.plus(1, ChronoUnit.HOURS)));

        assertEquals(saved.getId(), activationTokens.findByToken(hash).orElseThrow().getId());
        assertEquals(1, activationTokens.markValidated(saved.getId(), now));
        assertEquals(0, activationTokens.markValidated(saved.getId(), now));
        assertNotNull(activationTokens.findByToken(hash).orElseThrow().getValidatedAt());
    }

    @Test
    void resetTokenDeleteByTokenIsAtomicAndOneShot() {
        String hash = random();
        resetTokens.save(new PasswordResetToken(userId, hash, Instant.now().plus(1, ChronoUnit.HOURS)));

        assertTrue(resetTokens.findByToken(hash).isPresent());
        assertEquals(1, resetTokens.deleteByToken(hash));
        assertEquals(0, resetTokens.deleteByToken(hash));
        assertFalse(resetTokens.findByToken(hash).isPresent());
    }

    @Test
    void blacklistLookupByJtiAndPurgeOfExpiredEntries() {
        Instant now = Instant.now();
        String expiredJti = random();
        String liveJti = random();
        blacklistedTokens.save(new BlacklistedToken(userId, random(), expiredJti, now, now.minus(1, ChronoUnit.HOURS)));
        blacklistedTokens.save(new BlacklistedToken(userId, random(), liveJti, now, now.plus(1, ChronoUnit.HOURS)));

        assertTrue(blacklistedTokens.existsByJti(expiredJti));
        assertTrue(blacklistedTokens.existsByJti(liveJti));
        assertFalse(blacklistedTokens.existsByJti(random()));

        assertTrue(blacklistedTokens.deleteByExpiresAtBefore(now) >= 1);

        assertFalse(blacklistedTokens.existsByJti(expiredJti));
        assertTrue(blacklistedTokens.existsByJti(liveJti));
    }
}
