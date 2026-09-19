package com.edgareldy.micronauttutorial.controller.support;

import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.exception.ResourceNotFoundException;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import com.edgareldy.micronauttutorial.dto.common.ApiResponse;

/**
 * Test-only controller that throws every exception type the GlobalExceptionHandler maps, and exposes validated inputs.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// This class lives under src/test only, so it exists in the test classpath and never in the packaged
// application. Micronaut discovers it at compile time like any other controller. It is open to
// anonymous callers so the tests exercise the error mapping and not authentication.
@Controller("/test-support")
@Secured(SecurityRule.IS_ANONYMOUS)
public class ThrowingTestController {

    /**
     * Body used to exercise Bean Validation on request bodies.
     */
    @Serdeable
    public record Payload(@NotBlank(message = "must not be blank") String name,
                          @Min(value = 1, message = "must be at least 1") int quantity) {
    }

    @Get("/not-found")
    public String notFound() {
        throw new ResourceNotFoundException("Thing 42 not found");
    }

    @Get("/business-rule")
    public String businessRule() {
        throw new BusinessRuleException("Rule violated");
    }

    @Get("/http-status")
    public String httpStatus() {
        throw new HttpStatusException(HttpStatus.CONFLICT, "Already exists");
    }

    @Get("/unexpected")
    public String unexpected() {
        throw new IllegalStateException("secret internal detail");
    }

    @Get("/query")
    public String query(@QueryValue @Min(value = 1, message = "must be at least 1") int page,
                        @QueryValue @NotBlank(message = "must not be blank") String name) {
        return name + page;
    }

    @Get("/required")
    public String required(@QueryValue String name) {
        return name;
    }

    @Get("/items/{id}")
    public String item(int id) {
        return "item" + id;
    }

    @Post("/body")
    public String body(@Valid @Body Payload payload) {
        return payload.name();
    }

    @Post("/no-body")
    public ApiResponse<String> noBody() {
        return ApiResponse.success("ok", "created without body");
    }

    @Get("/success")
    public ApiResponse<String> success() {
        return ApiResponse.success("data", "all good");
    }
}
