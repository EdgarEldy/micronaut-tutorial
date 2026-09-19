package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.auth.ForgotPasswordRequest;
import com.edgareldy.micronauttutorial.dto.auth.LoginRequest;
import com.edgareldy.micronauttutorial.dto.auth.LoginResponse;
import com.edgareldy.micronauttutorial.dto.auth.RegisterRequest;
import com.edgareldy.micronauttutorial.dto.auth.ResetPasswordRequest;
import com.edgareldy.micronauttutorial.dto.auth.UserResponse;
import io.micronaut.security.authentication.Authentication;

/**
 * Contract of the authentication use cases: registration, activation, login, logout, profile and password reset.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public interface AuthService {

    /** Creates a disabled account and its activation token. Duplicate email: BusinessRuleException. */
    UserResponse register(RegisterRequest request);

    /** Consumes an activation token and enables the account. Unknown, expired and used tokens fail identically. */
    void activateAccount(String token);

    /** Verifies the credentials and issues a JWT. */
    LoginResponse login(LoginRequest request);

    /** Blacklists the JWT currently in use. */
    void logout(Authentication authentication, String rawToken);

    /** Profile of the authenticated user. */
    UserResponse me(Authentication authentication);

    /** Issues a reset token when the email is known; observably identical when it is not. */
    void forgotPassword(ForgotPasswordRequest request);

    /** Consumes a reset token and sets the new password. */
    void resetPassword(ResetPasswordRequest request);
}
