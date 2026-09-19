package com.edgareldy.micronauttutorial.security;

import io.micronaut.security.authentication.Authentication;

import java.time.Instant;
import java.util.Date;

/**
 * Reads the expiry (exp claim) of the validated JWT behind an {@link Authentication}, whatever numeric or date type
 * the security layer exposes it as.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public final class TokenExpiry {

    private TokenExpiry() {
    }

    /**
     * Expiry instant of the token the caller authenticated with.
     *
     * @param authentication the authenticated caller
     * @return the instant after which the token is no longer valid
     */
    public static Instant of(Authentication authentication) {
        Object exp = authentication.getAttributes().get("exp");
        if (exp instanceof Instant instant) {
            return instant;
        }
        if (exp instanceof Date date) {
            return date.toInstant();
        }
        return Instant.ofEpochSecond(((Number) exp).longValue());
    }
}
