package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.BlacklistedToken;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.Instant;

/**
 * Data access for revoked JWTs, consulted on every authenticated request through the jti.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    boolean existsByJti(String jti);

    /** Purges entries whose JWT has expired anyway (an expired token is rejected regardless). */
    long deleteByExpiresAtBefore(Instant threshold);
}
