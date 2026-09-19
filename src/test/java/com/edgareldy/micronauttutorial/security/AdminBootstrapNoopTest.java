package com.edgareldy.micronauttutorial.security;

import com.edgareldy.micronauttutorial.repository.RoleRepository;
import com.edgareldy.micronauttutorial.repository.UserRepository;
import com.edgareldy.micronauttutorial.service.AuditLogger;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Proves the bootstrap administrator is strictly opt-in: with a missing email or password nothing is read or written.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
class AdminBootstrapNoopTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);

    private AdminBootstrap bootstrapWith(String email, String password) {
        return new AdminBootstrap(new BootstrapAdminProperties(email, password), users, roles,
                new BCryptPasswordEncoder(4), audit);
    }

    @Test
    void doesNothingWhenNothingIsConfigured() {
        assertFalse(bootstrapWith(null, null).bootstrap());
        verifyNoInteractions(users, roles, audit);
    }

    @Test
    void doesNothingWhenOnlyTheEmailIsConfigured() {
        assertFalse(bootstrapWith("admin@example.com", null).bootstrap());
        assertFalse(bootstrapWith("admin@example.com", "  ").bootstrap());
        verifyNoInteractions(users, roles, audit);
    }

    @Test
    void doesNothingWhenOnlyThePasswordIsConfigured() {
        assertFalse(bootstrapWith(null, "some-password-value").bootstrap());
        verifyNoInteractions(users, roles, audit);
    }
}
