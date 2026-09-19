package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.dto.auth.ForgotPasswordRequest;
import com.edgareldy.micronauttutorial.dto.auth.LoginRequest;
import com.edgareldy.micronauttutorial.dto.auth.LoginResponse;
import com.edgareldy.micronauttutorial.dto.auth.RegisterRequest;
import com.edgareldy.micronauttutorial.dto.auth.ResetPasswordRequest;
import com.edgareldy.micronauttutorial.dto.auth.UserResponse;
import com.edgareldy.micronauttutorial.dto.common.ApiResponse;
import com.edgareldy.micronauttutorial.service.AuthService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.SecurityFilter;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Authentication endpoints under /api/v1/auth. Thin: validation, delegation to AuthService, ApiResponse wrapping.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @Secured(IS_ANONYMOUS) opens a route to unauthenticated callers, IS_AUTHENTICATED requires a valid JWT
// (every other route stays protected by default, see application.yml). @Validated turns on Bean
// Validation for the controller's parameters. Blocking calls are moved off the Netty event loop by
// Micronaut's default thread selection (AUTO), so bcrypt and JDBC do not block it.
@Controller("/api/v1/auth")
@Validated
public class AuthController {

    /** Same body for every forgot-password call, whether or not the email exists. */
    static final String FORGOT_PASSWORD_MESSAGE =
            "If an account exists for this email, a password reset token has been issued";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Post("/register")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<ApiResponse<UserResponse>> register(@Body @Valid RegisterRequest request) {
        return HttpResponse.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.register(request), "Account created, activation required"));
    }

    @Get("/activate-account")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public ApiResponse<Void> activateAccount(@QueryValue @NotBlank String token) {
        authService.activateAccount(token);
        return ApiResponse.success(null, "Account activated");
    }

    @Post("/login")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public ApiResponse<LoginResponse> login(@Body @Valid LoginRequest request) {
        return ApiResponse.success(authService.login(request), "Login successful");
    }

    // No request body: accept any Content-Type (clients often add a default one to a bodiless POST).
    @Post("/logout")
    @Consumes(MediaType.ALL)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public ApiResponse<Void> logout(Authentication authentication, HttpRequest<?> request) {
        // SecurityFilter stores the raw bearer token as a request attribute once it is validated.
        String rawToken = request.getAttribute(SecurityFilter.TOKEN, String.class).orElseThrow();
        authService.logout(authentication, rawToken);
        return ApiResponse.success(null, "Logged out");
    }

    @Get("/me")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public ApiResponse<UserResponse> me(Authentication authentication) {
        return ApiResponse.success(authService.me(authentication), "Current user");
    }

    @Post("/forgot-password")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public ApiResponse<Void> forgotPassword(@Body @Valid ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.success(null, FORGOT_PASSWORD_MESSAGE);
    }

    @Post("/reset-password")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public ApiResponse<Void> resetPassword(@Body @Valid ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success(null, "Password updated");
    }
}
