package com.edgareldy.micronauttutorial.exception;

/**
 * Thrown when a request is well formed but violates a business rule. Mapped to HTTP 422 by GlobalExceptionHandler.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
