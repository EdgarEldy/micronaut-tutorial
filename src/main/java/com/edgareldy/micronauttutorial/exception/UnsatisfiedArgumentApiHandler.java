package com.edgareldy.micronauttutorial.exception;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.http.server.exceptions.UnsatisfiedArgumentHandler;
import jakarta.inject.Singleton;

/**
 * Unsatisfied argument binding (400) as ApiResponse; replaces UnsatisfiedArgumentHandler.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Produces
@Singleton
@Replaces(UnsatisfiedArgumentHandler.class)
public class UnsatisfiedArgumentApiHandler implements ExceptionHandler<UnsatisfiedArgumentException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, UnsatisfiedArgumentException exception) {
        return GlobalExceptionHandler.toResponse(exception);
    }
}
