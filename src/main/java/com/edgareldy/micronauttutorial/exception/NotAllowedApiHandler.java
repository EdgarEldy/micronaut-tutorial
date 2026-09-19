package com.edgareldy.micronauttutorial.exception;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.NotAllowedException;
import io.micronaut.http.server.exceptions.NotAllowedExceptionHandler;
import jakarta.inject.Singleton;

/**
 * Wrong HTTP method (405) as ApiResponse; replaces the built-in NotAllowedExceptionHandler.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Produces
@Singleton
@Replaces(NotAllowedExceptionHandler.class)
public class NotAllowedApiHandler implements ExceptionHandler<NotAllowedException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, NotAllowedException exception) {
        // RFC 9110 requires an Allow header on a 405, which the built-in handler set and a plain
        // status response would drop.
        return HttpResponse.<Object>notAllowedGeneric(exception.getAllowedMethods())
                .body(GlobalExceptionHandler.toResponse(exception).body());
    }
}
