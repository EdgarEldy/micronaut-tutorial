package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.auth.ForgotPasswordRequest;
import com.edgareldy.micronauttutorial.dto.auth.LoginRequest;
import com.edgareldy.micronauttutorial.dto.auth.RegisterRequest;
import com.edgareldy.micronauttutorial.entity.User;
import com.edgareldy.micronauttutorial.exception.AuthenticationFailedException;
import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.repository.ActivationTokenRepository;
import com.edgareldy.micronauttutorial.repository.BlacklistedTokenRepository;
import com.edgareldy.micronauttutorial.repository.PasswordResetTokenRepository;
import com.edgareldy.micronauttutorial.repository.UserRepository;
import com.edgareldy.micronauttutorial.security.JwtIssuer;
import com.edgareldy.micronauttutorial.security.TokenProperties;
import com.edgareldy.micronauttutorial.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the security-sensitive branches of AuthServiceImpl with every collaborator mocked.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// No Micronaut context here: the service is built by hand with Mockito mocks, so the test is fast and only
// exercises the logic of the class. The @Transactional AOP is not woven into an instance built with "new".
class AuthServiceImplTest {

    private UserRepository users;
    private ActivationTokenRepository activationTokens;
    private PasswordResetTokenRepository resetTokens;
    private BCryptPasswordEncoder encoder;
    private JwtIssuer jwtIssuer;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        activationTokens = mock(ActivationTokenRepository.class);
        resetTokens = mock(PasswordResetTokenRepository.class);
        encoder = mock(BCryptPasswordEncoder.class);
        jwtIssuer = mock(JwtIssuer.class);
        when(encoder.encode(anyString())).thenReturn("hash");
        service = new AuthServiceImpl(users, activationTokens, resetTokens, mock(BlacklistedTokenRepository.class),
                encoder, jwtIssuer, new TokenProperties(Duration.ofHours(24), Duration.ofHours(1)));
    }

    @Test
    void loginForAnUnknownEmailStillVerifiesABcryptHashOnceAndFailsLikeAWrongPassword() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        AuthenticationFailedException unknown = assertThrows(AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("ghost@example.com", "Password123!")));
        // Timing equalisation: exactly one bcrypt verification even though there is no account.
        verify(encoder, times(1)).matches(eq("Password123!"), any());

        User user = new User("Ada", "Lovelace", "ada@example.com", "stored-hash");
        when(users.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("Wrong1234!", "stored-hash")).thenReturn(false);
        AuthenticationFailedException wrong = assertThrows(AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("ada@example.com", "Wrong1234!")));

        assertEquals(wrong.getMessage(), unknown.getMessage());
        verifyNoInteractions(jwtIssuer);
    }

    @Test
    void forgotPasswordForAnUnknownEmailNeverSavesAToken() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.forgotPassword(new ForgotPasswordRequest("ghost@example.com"));

        verify(resetTokens, never()).save(any());
    }

    @Test
    void registerWithAnExistingEmailIsRejectedWithoutSaving() {
        when(users.existsByEmail("ada@example.com")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.register(
                new RegisterRequest("Ada", "Lovelace", "  ADA@example.com ", "Password123!")));

        verify(users, never()).save(any());
        verify(activationTokens, never()).save(any());
    }
}
