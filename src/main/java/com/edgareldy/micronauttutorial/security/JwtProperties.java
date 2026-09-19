package com.edgareldy.micronauttutorial.security;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

/**
 * Typed binding of the app.jwt.* properties (key locations, issuer, token lifespan in seconds).
 * Locations are nullable so that JwtKeyFactory can fail with an explicit message when they are missing.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @ConfigurationProperties binds a property prefix onto a bean at startup, so the settings are typed
// and injectable instead of scattered @Value lookups.
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @Nullable String privateKeyLocation,
        @Nullable String publicKeyLocation,
        String issuer,
        long lifespan
) {
}
