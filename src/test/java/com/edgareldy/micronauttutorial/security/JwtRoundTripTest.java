package com.edgareldy.micronauttutorial.security;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.generator.TokenGenerator;
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGenerator;
import io.micronaut.security.token.validator.TokenValidator;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Proves the RS256 key pair loads and that a token signed with it validates and keeps its custom claim.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// TokenGenerator signs a JWT and TokenValidator checks its signature and expiry. Both are Micronaut
// Security beans wired to the "generator" RSASignatureGenerator produced by JwtKeyFactory.
@MicronautTest
class JwtRoundTripTest {

    @Inject
    @Named("generator")
    RSASignatureGenerator generator;

    @Inject
    TokenGenerator tokenGenerator;

    @Inject
    TokenValidator<HttpRequest<?>> tokenValidator;

    @Test
    void generatorBeanLoads() {
        assertNotNull(generator);
    }

    @Test
    void generatedTokenValidatesAndCarriesCustomClaim() {
        Authentication auth = Authentication.build("user@example.com", Map.of("permissions", List.of("ROLE:WRITE")));

        String token = tokenGenerator.generateToken(auth, 60).orElseThrow();
        Authentication validated = Mono.from(tokenValidator.validateToken(token, HttpRequest.GET("/")))
                .block();

        assertNotNull(validated);
        assertEquals("user@example.com", validated.getName());
        assertEquals(List.of("ROLE:WRITE"), validated.getAttributes().get("permissions"));
    }
}
