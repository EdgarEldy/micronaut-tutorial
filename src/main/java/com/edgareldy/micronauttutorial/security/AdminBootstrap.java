package com.edgareldy.micronauttutorial.security;

import com.edgareldy.micronauttutorial.entity.Role;
import com.edgareldy.micronauttutorial.entity.User;
import com.edgareldy.micronauttutorial.repository.RoleRepository;
import com.edgareldy.micronauttutorial.repository.UserRepository;
import com.edgareldy.micronauttutorial.service.AuditLogger;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Locale;

/**
 * Creates the first administrator at startup when app.bootstrap-admin.email and password are both configured
 * and no account with that email exists. The account is enabled and holds the seeded ADMIN role.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// A startup listener: Micronaut publishes application events and calls every ApplicationEventListener bean
// registered for the event type. ServerStartupEvent fires once the server is up, after Flyway has run, so the
// seeded ADMIN role exists. The feature is opt-in through configuration: nothing is configured outside the dev
// profile, so production never gets a default account.
@Singleton
public class AdminBootstrap implements ApplicationEventListener<ServerStartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);
    static final String ADMIN_ROLE = "ADMIN";

    private final BootstrapAdminProperties properties;
    private final UserRepository users;
    private final RoleRepository roles;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditLogger audit;

    public AdminBootstrap(BootstrapAdminProperties properties, UserRepository users, RoleRepository roles,
                          BCryptPasswordEncoder passwordEncoder, AuditLogger audit) {
        this.properties = properties;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void onApplicationEvent(ServerStartupEvent event) {
        bootstrap();
    }

    /** Creates the administrator when configured and absent. Returns true if an account was created. */
    boolean bootstrap() {
        if (!properties.isConfigured()) {
            return false;
        }
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            return false;
        }
        Role adminRole = roles.findByRoleName(ADMIN_ROLE).orElse(null);
        if (adminRole == null) {
            LOG.warn("Bootstrap admin skipped: role {} not found", ADMIN_ROLE);
            return false;
        }
        User admin = new User("Bootstrap", "Admin", email, passwordEncoder.encode(properties.password()));
        admin.setEnabled(true);
        admin.getRoles().add(adminRole);
        users.save(admin);
        audit.log("BOOTSTRAP_ADMIN_CREATE", "USER", admin.getId(), "email=" + email);
        LOG.info("Bootstrap admin created for {}", email);
        return true;
    }
}
