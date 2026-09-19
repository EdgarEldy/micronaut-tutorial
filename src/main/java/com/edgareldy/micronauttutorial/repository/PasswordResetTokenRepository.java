package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.PasswordResetToken;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Data access for password reset tokens (lookup by the SHA-256 hex of the raw token).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    /** Atomically consumes (deletes) the token: 1 for the single winner, 0 otherwise. */
    long deleteByToken(String token);

    long deleteByUserId(Long userId);

    long deleteByExpiryDateBefore(Instant threshold);

    /** Purges only the expired tokens of one user, so an anonymous caller cannot wipe a valid pending token. */
    long deleteByUserIdAndExpiryDateBefore(Long userId, Instant threshold);
}
