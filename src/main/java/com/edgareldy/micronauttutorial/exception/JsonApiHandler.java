package com.edgareldy.micronauttutorial.exception;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.http.server.exceptions.JsonExceptionHandler;
import jakarta.inject.Singleton;

/**
 * Malformed JSON request bodies (400) as ApiResponse; replaces JsonExceptionHandler.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Produces
@Singleton
@Replaces(JsonExceptionHandler.class)
public class JsonApiHandler implements ExceptionHandler<JsonSyntaxException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, JsonSyntaxException exception) {
        return GlobalExceptionHandler.toResponse(exception);
    }
}
