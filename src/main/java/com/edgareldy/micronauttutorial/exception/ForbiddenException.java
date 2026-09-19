package com.edgareldy.micronauttutorial.exception;

/**
 * Thrown when the caller is known but not allowed (not activated or locked account). Mapped to HTTP 403 by GlobalExceptionHandler.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
