package com.edgareldy.micronauttutorial.exception;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.HttpStatusHandler;
import jakarta.inject.Singleton;

/**
 * Framework HttpStatusExceptions (404 unknown route, 415...) as ApiResponse; replaces the built-in HttpStatusHandler.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Produces
@Singleton
@Replaces(HttpStatusHandler.class)
public class HttpStatusApiHandler implements ExceptionHandler<HttpStatusException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, HttpStatusException exception) {
        return GlobalExceptionHandler.toResponse(exception);
    }
}
