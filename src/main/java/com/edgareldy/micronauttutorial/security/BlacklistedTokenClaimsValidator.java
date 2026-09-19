package com.edgareldy.micronauttutorial.security;

import com.edgareldy.micronauttutorial.repository.BlacklistedTokenRepository;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.token.Claims;
import io.micronaut.security.token.jwt.validator.GenericJwtClaimsValidator;
import jakarta.inject.Singleton;

/**
 * Rejects a JWT whose jti has been blacklisted by logout (one indexed query per authenticated request).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// A JwtClaimsValidator is called by Micronaut Security's JwtTokenValidator after the signature check,
// with the token's claims; returning false makes the token unauthenticated (401). It is used instead of
// a second TokenValidator because the token validators are consulted independently, so the built-in
// JwtTokenValidator would still accept a blacklisted but correctly signed token. JwtTokenValidator runs
// its validation on the blocking executor (subscribeOn), so this database lookup stays off the Netty event loop.
@Singleton
public class BlacklistedTokenClaimsValidator implements GenericJwtClaimsValidator<HttpRequest<?>> {

    private final BlacklistedTokenRepository blacklistedTokens;

    public BlacklistedTokenClaimsValidator(BlacklistedTokenRepository blacklistedTokens) {
        this.blacklistedTokens = blacklistedTokens;
    }

    @Override
    public boolean validate(Claims claims, HttpRequest<?> request) {
        Object jti = claims.get(Claims.TOKEN_ID);
        // A token without jti cannot be revoked nor was it issued by JwtIssuer: refuse it.
        return jti != null && !blacklistedTokens.existsByJti(jti.toString());
    }
}
