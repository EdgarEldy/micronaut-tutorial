package com.edgareldy.micronauttutorial.security;

import com.edgareldy.micronauttutorial.entity.User;
import io.micronaut.security.token.generator.TokenGenerator;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the signed access JWT: sub, jti, iss, iat, exp, email and the user's permission codes.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// TokenGenerator (implemented by JwtTokenGenerator) signs a claims map with the RS256 key produced by
// JwtKeyFactory. The Map variant is used so every claim (notably jti and the custom "permissions" list)
// is set explicitly instead of relying on the default claims generator.
@Singleton
public class JwtIssuer {

    private final TokenGenerator tokenGenerator;
    private final JwtProperties properties;

    public JwtIssuer(TokenGenerator tokenGenerator, JwtProperties properties) {
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
    }

    public IssuedToken issue(User user, List<String> permissions) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plusSeconds(properties.lifespan());
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", String.valueOf(user.getId()));
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("iss", properties.issuer());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("email", user.getEmail());
        claims.put("permissions", permissions);
        String token = tokenGenerator.generateToken(claims)
                .orElseThrow(() -> new IllegalStateException("Could not sign the access token"));
        return new IssuedToken(token, expiresAt, properties.lifespan());
    }

    /**
     * A freshly signed JWT with its expiry. toString masks the token.
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : micronaut-tutorial
     */
    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {

        @Override
        public String toString() {
            return "IssuedToken[token=***, expiresAt=" + expiresAt + "]";
        }
    }
}
