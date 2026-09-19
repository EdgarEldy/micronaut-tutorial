package com.edgareldy.micronauttutorial.security;

import io.micronaut.security.authentication.Authentication;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test of TokenExpiry: the exp attribute is read whether it is a number of seconds, an Instant or a Date.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Plain Mockito, no application context: Authentication is an interface, so it is mocked to return the attributes.
class TokenExpiryTest {

    private static final Instant EXPIRY = Instant.ofEpochSecond(1_900_000_000L);

    private static Authentication with(Object exp) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getAttributes()).thenReturn(Map.of("exp", exp));
        return authentication;
    }

    @Test
    void readsAnIntegerNumberOfSeconds() {
        assertEquals(EXPIRY, TokenExpiry.of(with(1_900_000_000)));
    }

    @Test
    void readsALongNumberOfSeconds() {
        assertEquals(EXPIRY, TokenExpiry.of(with(1_900_000_000L)));
    }

    @Test
    void readsADoubleNumberOfSeconds() {
        assertEquals(EXPIRY, TokenExpiry.of(with(1_900_000_000.0d)));
    }

    @Test
    void readsAnInstant() {
        assertEquals(EXPIRY, TokenExpiry.of(with(EXPIRY)));
    }

    @Test
    void readsADate() {
        assertEquals(EXPIRY, TokenExpiry.of(with(Date.from(EXPIRY))));
    }
}
