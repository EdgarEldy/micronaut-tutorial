package com.edgareldy.micronauttutorial.security;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

/**
 * Typed binding of app.bootstrap-admin.*: optional email and password of an administrator created at startup.
 * Both must be set for the bootstrap to run; toString masks the password.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@ConfigurationProperties("app.bootstrap-admin")
public record BootstrapAdminProperties(@Nullable String email, @Nullable String password) {

    /** True when both values are present and not blank. */
    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "BootstrapAdminProperties[email=" + email + ", password=***]";
    }
}
