package com.edgareldy.micronauttutorial.exception;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.server.exceptions.ConversionErrorHandler;
import jakarta.inject.Singleton;

/**
 * Path, query or body values that cannot be converted (400) as ApiResponse; replaces ConversionErrorHandler.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Produces
@Singleton
@Replaces(ConversionErrorHandler.class)
public class ConversionErrorApiHandler implements ExceptionHandler<ConversionErrorException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, ConversionErrorException exception) {
        return GlobalExceptionHandler.toResponse(exception);
    }
}
