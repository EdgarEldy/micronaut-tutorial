package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.ActivationToken;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Data access for activation tokens (lookup by the SHA-256 hex of the raw token).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Repository
public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {

    Optional<ActivationToken> findByToken(String token);

    /**
     * Atomically marks the token as used. Returns 1 for the single caller that wins, 0 for a concurrent
     * or repeated call: this is what makes the token one-shot.
     */
    @Query("UPDATE ActivationToken SET validatedAt = :now WHERE id = :id AND validatedAt IS NULL")
    long markValidated(Long id, Instant now);
}
