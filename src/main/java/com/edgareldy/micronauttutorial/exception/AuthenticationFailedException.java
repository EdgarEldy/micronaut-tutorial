package com.edgareldy.micronauttutorial.exception;

/**
 * Thrown when credentials or a token are wrong. Mapped to HTTP 401 by GlobalExceptionHandler.
 * The message must never reveal which part was wrong (unknown email vs wrong password).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
