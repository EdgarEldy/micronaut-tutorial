package com.edgareldy.micronauttutorial.exception;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import io.micronaut.http.server.exceptions.UnsatisfiedRouteHandler;
import jakarta.inject.Singleton;

/**
 * Missing required parameter or body (400) as ApiResponse; replaces UnsatisfiedRouteHandler.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Produces
@Singleton
@Replaces(UnsatisfiedRouteHandler.class)
public class UnsatisfiedRouteApiHandler implements ExceptionHandler<UnsatisfiedRouteException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, UnsatisfiedRouteException exception) {
        return GlobalExceptionHandler.toResponse(exception);
    }
}
