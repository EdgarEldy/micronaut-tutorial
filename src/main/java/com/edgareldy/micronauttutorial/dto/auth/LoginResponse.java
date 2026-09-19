package com.edgareldy.micronauttutorial.dto.auth;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Result of a successful login: the signed JWT, its scheme and its lifetime in seconds. toString masks the token.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {

    public static final String BEARER = "Bearer";

    public static LoginResponse bearer(String accessToken, long expiresIn) {
        return new LoginResponse(accessToken, BEARER, expiresIn);
    }

    @Override
    public String toString() {
        return "LoginResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
