package com.edgareldy.micronauttutorial.service.impl;

import com.edgareldy.micronauttutorial.dto.auth.ForgotPasswordRequest;
import com.edgareldy.micronauttutorial.dto.auth.LoginRequest;
import com.edgareldy.micronauttutorial.dto.auth.LoginResponse;
import com.edgareldy.micronauttutorial.dto.auth.RegisterRequest;
import com.edgareldy.micronauttutorial.dto.auth.ResetPasswordRequest;
import com.edgareldy.micronauttutorial.dto.auth.UserResponse;
import com.edgareldy.micronauttutorial.entity.ActivationToken;
import com.edgareldy.micronauttutorial.entity.BlacklistedToken;
import com.edgareldy.micronauttutorial.entity.PasswordResetToken;
import com.edgareldy.micronauttutorial.entity.User;
import com.edgareldy.micronauttutorial.exception.AuthenticationFailedException;
import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.exception.ForbiddenException;
import com.edgareldy.micronauttutorial.repository.ActivationTokenRepository;
import com.edgareldy.micronauttutorial.repository.BlacklistedTokenRepository;
import com.edgareldy.micronauttutorial.repository.PasswordResetTokenRepository;
import com.edgareldy.micronauttutorial.repository.UserRepository;
import com.edgareldy.micronauttutorial.security.JwtIssuer;
import com.edgareldy.micronauttutorial.security.TokenExpiry;
import com.edgareldy.micronauttutorial.security.TokenHasher;
import com.edgareldy.micronauttutorial.security.TokenProperties;
import com.edgareldy.micronauttutorial.service.AuthService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Default AuthService. Raw activation and reset tokens are random, logged once at INFO (no mailer) and
 * stored only as SHA-256 hex. Login gives one identical error for an unknown email and a wrong password.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @Transactional (Micronaut's own, from micronaut-data-tx) is compile-time AOP: an interceptor woven into
// this bean opens a transaction around each public method and rolls back on a RuntimeException, so a
// multi-step write (user + token) is atomic. Repository calls inside join that same transaction.
@Singleton
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthServiceImpl.class);

    static final String INVALID_CREDENTIALS = "Invalid email or password";
    static final String INVALID_ACTIVATION_TOKEN = "Invalid or expired activation token";
    static final String INVALID_RESET_TOKEN = "Invalid or expired password reset token";
    private static final long NO_USER_ID = -1L;
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final ActivationTokenRepository activationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final BlacklistedTokenRepository blacklistedTokens;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final TokenProperties tokenProperties;

    // Instance field on purpose: a static SecureRandom would be initialised at native-image build time.
    private final SecureRandom secureRandom = new SecureRandom();

    // Hash of a random password, checked when the email is unknown so that a login for an unknown account
    // costs a real bcrypt verification like a known one (timing equalisation).
    private final String dummyHash;

    public AuthServiceImpl(UserRepository users, ActivationTokenRepository activationTokens,
                           PasswordResetTokenRepository resetTokens, BlacklistedTokenRepository blacklistedTokens,
                           BCryptPasswordEncoder passwordEncoder, JwtIssuer jwtIssuer, TokenProperties tokenProperties) {
        this.users = users;
        this.activationTokens = activationTokens;
        this.resetTokens = resetTokens;
        this.blacklistedTokens = blacklistedTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.tokenProperties = tokenProperties;
        this.dummyHash = passwordEncoder.encode(newRawToken());
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        if (users.existsByEmail(email)) {
            throw new BusinessRuleException("Email is already registered");
        }
        requireBcryptCompatible(request.password());
        User user;
        try {
            user = users.save(new User(request.firstName().trim(), request.lastName().trim(), email,
                    passwordEncoder.encode(request.password())));
        } catch (DataAccessException e) {
            // Two concurrent registrations of the same email both pass existsByEmail: the unique
            // constraint decides, and the loser gets the same 422 as a sequential duplicate.
            throw new BusinessRuleException("Email is already registered");
        }
        String raw = newRawToken();
        Instant now = Instant.now();
        activationTokens.save(new ActivationToken(user.getId(), TokenHasher.sha256Hex(raw), now,
                now.plus(tokenProperties.activationTtl())));
        // No mailer in this tutorial: the raw token is only available in this log line.
        LOG.info("Activation token for user {}: {}", user.getId(), raw);
        return toResponse(user, List.of());
    }

    @Override
    public void activateAccount(String token) {
        ActivationToken stored = activationTokens.findByToken(TokenHasher.sha256Hex(token)).orElse(null);
        Instant now = Instant.now();
        if (stored == null || stored.getValidatedAt() != null || !stored.getExpiresAt().isAfter(now)
                || activationTokens.markValidated(stored.getId(), now) != 1) {
            throw new BusinessRuleException(INVALID_ACTIVATION_TOKEN);
        }
        User user = users.findById(stored.getUserId()).orElseThrow(() -> new BusinessRuleException(INVALID_ACTIVATION_TOKEN));
        user.setEnabled(true);
        users.update(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmail(normalise(request.email())).orElse(null);
        // Always one bcrypt verification, against a dummy hash when the email is unknown.
        boolean passwordOk = matchesSafely(request.password(), user != null ? user.getPassword() : dummyHash);
        if (user == null || !passwordOk) {
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        // Account state is only revealed to someone who proved knowing the password.
        if (!user.isEnabled()) {
            throw new ForbiddenException("Account is not activated");
        }
        if (user.isAccountLocked()) {
            throw new ForbiddenException("Account is locked");
        }
        JwtIssuer.IssuedToken issued = jwtIssuer.issue(user, users.findPermissionCodesByUserId(user.getId()));
        return LoginResponse.bearer(issued.token(), issued.expiresInSeconds());
    }

    @Override
    public void logout(Authentication authentication, String rawToken) {
        String jti = String.valueOf(authentication.getAttributes().get("jti"));
        if (blacklistedTokens.existsByJti(jti)) {
            return;
        }
        Instant now = Instant.now();
        blacklistedTokens.save(new BlacklistedToken(Long.valueOf(authentication.getName()),
                TokenHasher.sha256Hex(rawToken), jti, now, TokenExpiry.of(authentication)));
        // Housekeeping: entries of tokens that have expired anyway are useless.
        blacklistedTokens.deleteByExpiresAtBefore(now);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse me(Authentication authentication) {
        User user = users.findById(Long.valueOf(authentication.getName()))
                .orElseThrow(() -> new AuthenticationFailedException("Authentication required"));
        // Permissions come from the token, like every other permission decision: role changes apply at the next login.
        return toResponse(user, permissionsOf(authentication));
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        // Same work whether or not the account exists: lookup, token generation + hashing, purge query.
        // Only the final insert is skipped for an unknown email, so the timing is approximately (not exactly)
        // equal: one INSERT of difference remains, negligible next to the HTTP round trip.
        User user = users.findByEmail(normalise(request.email())).orElse(null);
        String raw = newRawToken();
        String hash = TokenHasher.sha256Hex(raw);
        // Only expired tokens are purged: deleting the pending ones would let an anonymous caller keep
        // invalidating a victim's valid reset token by calling this endpoint repeatedly.
        resetTokens.deleteByUserIdAndExpiryDateBefore(user != null ? user.getId() : NO_USER_ID, Instant.now());
        if (user != null) {
            resetTokens.save(new PasswordResetToken(user.getId(), hash, Instant.now().plus(tokenProperties.resetTtl())));
            LOG.info("Password reset token for user {}: {}", user.getId(), raw);
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken stored = resetTokens.findByToken(TokenHasher.sha256Hex(request.token())).orElse(null);
        // deleteByToken consumes the token atomically: only one concurrent caller gets 1.
        if (stored == null || !stored.getExpiryDate().isAfter(Instant.now())
                || resetTokens.deleteByToken(stored.getToken()) != 1) {
            throw new BusinessRuleException(INVALID_RESET_TOKEN);
        }
        User user = users.findById(stored.getUserId()).orElseThrow(() -> new BusinessRuleException(INVALID_RESET_TOKEN));
        requireBcryptCompatible(request.newPassword());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        users.update(user);
        // Any other outstanding reset token of this user is now obsolete.
        resetTokens.deleteByUserId(user.getId());
    }

    /**
     * BCrypt only reads the first 72 BYTES of a password and the encoder refuses longer ones, while Bean
     * Validation counts characters: 40 multibyte characters can exceed 72 bytes. Refuse them cleanly.
     */
    private static void requireBcryptCompatible(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BusinessRuleException("Password must not exceed " + MAX_PASSWORD_BYTES + " bytes in UTF-8");
        }
    }

    /**
     * Password check that treats an over-long password as a plain mismatch, still paying for one bcrypt
     * verification so the response time stays close to a normal failed login.
     */
    private boolean matchesSafely(String rawPassword, String hash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            passwordEncoder.matches("x", hash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    /** 32 random bytes, base64url without padding (256 bits of entropy). */
    private String newRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static List<String> permissionsOf(Authentication authentication) {
        Object value = authentication.getAttributes().get("permissions");
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    private static UserResponse toResponse(User user, List<String> permissions) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.isEnabled(), permissions);
    }
}
