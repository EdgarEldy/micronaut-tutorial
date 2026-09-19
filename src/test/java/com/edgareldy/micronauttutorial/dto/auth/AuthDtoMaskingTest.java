package com.edgareldy.micronauttutorial.dto.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * toString of the auth DTOs must never print a password, a reset token or an access token.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
class AuthDtoMaskingTest {

    @Test
    void registerRequestMasksThePassword() {
        String text = new RegisterRequest("Ada", "Lovelace", "ada@example.com", "s3cret-Password").toString();

        assertFalse(text.contains("s3cret-Password"), text);
        assertTrue(text.contains("ada@example.com"), text);
    }

    @Test
    void loginRequestMasksThePassword() {
        String text = new LoginRequest("ada@example.com", "s3cret-Password").toString();

        assertFalse(text.contains("s3cret-Password"), text);
    }

    @Test
    void resetPasswordRequestMasksTokenAndPassword() {
        String text = new ResetPasswordRequest("raw-reset-token", "s3cret-Password").toString();

        assertFalse(text.contains("raw-reset-token"), text);
        assertFalse(text.contains("s3cret-Password"), text);
    }

    @Test
    void loginResponseMasksTheAccessToken() {
        String text = LoginResponse.bearer("eyJ.secret.jwt", 3600).toString();

        assertFalse(text.contains("eyJ.secret.jwt"), text);
        assertTrue(text.contains("3600"), text);
    }
}
