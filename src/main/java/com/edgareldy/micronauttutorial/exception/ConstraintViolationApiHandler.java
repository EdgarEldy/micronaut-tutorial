package com.edgareldy.micronauttutorial.exception;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import io.micronaut.validation.exceptions.ConstraintExceptionHandler;
import jakarta.inject.Singleton;

/**
 * Bean Validation failures (body or parameters) as a 400 ApiResponse; replaces the built-in ConstraintExceptionHandler.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @Replaces swaps a built-in bean for this one. Micronaut ships specific ExceptionHandlers (validation,
// HTTP status, parameter conversion, JSON parsing...) that are closer to their exception type than
// GlobalExceptionHandler, so they would win and answer in the framework's own error format. Each
// replacement below only delegates to GlobalExceptionHandler.toResponse, which keeps ONE mapping table
// and makes every error an ApiResponse with success = false.
@Produces
@Singleton
@Replaces(ConstraintExceptionHandler.class)
public class ConstraintViolationApiHandler implements ExceptionHandler<ConstraintViolationException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, ConstraintViolationException exception) {
        return GlobalExceptionHandler.toResponse(exception);
    }
}
