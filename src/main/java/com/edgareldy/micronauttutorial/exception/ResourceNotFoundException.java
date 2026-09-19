package com.edgareldy.micronauttutorial.exception;

/**
 * Thrown when a requested resource does not exist. Mapped to HTTP 404 by GlobalExceptionHandler.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
