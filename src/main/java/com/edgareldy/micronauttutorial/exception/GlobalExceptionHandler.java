package com.edgareldy.micronauttutorial.exception;

import com.edgareldy.micronauttutorial.dto.common.ApiResponse;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.annotation.Body;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Maps every exception to an {@link ApiResponse} with success = false.
 * The mapping lives in {@link #toResponse(Throwable)} so the more specific built-in handlers replaced
 * elsewhere in this package delegate to exactly the same rules.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// An ExceptionHandler turns a thrown exception into an HTTP response. Micronaut picks the handler
// whose exception type is the closest to the thrown one, so this catch-all for Exception only sees
// what no more specific handler claims. That is why the specific ones are replaced (@Replaces) by
// classes that delegate here.
@Produces
@Singleton
@Requires(classes = Exception.class)
public class GlobalExceptionHandler implements ExceptionHandler<Exception, HttpResponse<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    public HttpResponse<?> handle(HttpRequest request, Exception exception) {
        return toResponse(exception);
    }

    /**
     * Single mapping table from exception to HTTP response.
     */
    public static HttpResponse<ApiResponse<Void>> toResponse(Throwable exception) {
        if (exception instanceof ResourceNotFoundException e) {
            return build(HttpStatus.NOT_FOUND, e.getMessage());
        }
        if (exception instanceof ConstraintViolationException e) {
            String message = e.getConstraintViolations().stream()
                    .map(v -> lastNode(v.getPropertyPath().toString()) + ": " + v.getMessage())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.joining("; "));
            return build(HttpStatus.BAD_REQUEST, message);
        }
        if (exception instanceof ConversionErrorException e) {
            return build(HttpStatus.BAD_REQUEST, isBody(e.getArgument())
                    ? "Invalid request body" : "Invalid value for parameter '" + e.getArgument().getName() + "'");
        }
        if (exception instanceof UnsatisfiedRouteException e) {
            return build(HttpStatus.BAD_REQUEST, missing(e.getArgument()));
        }
        if (exception instanceof UnsatisfiedArgumentException e) {
            return build(HttpStatus.BAD_REQUEST, missing(e.getArgument()));
        }
        if (exception instanceof JsonSyntaxException) {
            return build(HttpStatus.BAD_REQUEST, "Malformed JSON request body");
        }
        if (exception instanceof BusinessRuleException e) {
            return build(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
        if (exception instanceof HttpStatusException e) {
            return build(e.getStatus(), e.getMessage() != null ? e.getMessage() : e.getStatus().getReason());
        }
        LOG.error("Unhandled exception", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private static boolean isBody(Argument<?> argument) {
        return argument.getAnnotationMetadata().hasAnnotation(Body.class);
    }

    private static String missing(Argument<?> argument) {
        return isBody(argument) ? "Request body is required" : "Required parameter '" + argument.getName() + "' is missing";
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }

    private static HttpResponse<ApiResponse<Void>> build(HttpStatus status, String message) {
        return HttpResponse.status(status).body(ApiResponse.error(message));
    }
}
