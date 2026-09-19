package com.edgareldy.micronauttutorial.exception;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.security.authentication.AuthorizationException;
import io.micronaut.security.authentication.DefaultAuthorizationExceptionHandler;
import jakarta.inject.Singleton;

/**
 * Micronaut Security 401/403 rejections as ApiResponse; replaces the built-in DefaultAuthorizationExceptionHandler.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// When SecurityFilter rejects a request (no valid token, or authenticated but not allowed) it throws an
// AuthorizationException. The default handler answers with the framework's own error body, so it is
// replaced by one delegating to the shared mapping, like the other handlers of this package.
@Produces
@Singleton
@Replaces(DefaultAuthorizationExceptionHandler.class)
public class AuthorizationApiHandler implements ExceptionHandler<AuthorizationException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, AuthorizationException exception) {
        return GlobalExceptionHandler.toResponse(exception);
    }
}
