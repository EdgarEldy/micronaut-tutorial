package com.edgareldy.micronauttutorial.security;

import com.edgareldy.micronauttutorial.exception.AuthenticationFailedException;
import com.edgareldy.micronauttutorial.exception.ForbiddenException;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Around advice behind {@link RequiresPermission}: the single place where the permissions of the caller are resolved.
 * They come from the "permissions" claim of the validated JWT, exposed through {@link SecurityService}.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// A MethodInterceptor wraps the call: it can inspect the invocation, then either proceed() or throw.
// Throwing from here inside a controller reaches the exception handlers like any controller exception,
// so ForbiddenException becomes the 403 ApiResponse. SecurityService reads the Authentication that
// SecurityFilter stored on the current request, which is available on the (blocking) thread running the
// controller. Nothing else in the code base re-derives permissions.
@Singleton
public class PermissionInterceptor implements MethodInterceptor<Object, Object> {

    static final String PERMISSIONS_CLAIM = "permissions";

    private final SecurityService securityService;

    public PermissionInterceptor(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        String resource = context.stringValue(RequiresPermission.class, "resource").orElseThrow();
        String action = context.stringValue(RequiresPermission.class, "action").orElseThrow();
        Authentication authentication = securityService.getAuthentication()
                .orElseThrow(() -> new AuthenticationFailedException("Authentication required"));
        if (!grantedPermissions(authentication).contains(resource + ":" + action)) {
            throw new ForbiddenException("Access denied");
        }
        return context.proceed();
    }

    private static Set<String> grantedPermissions(Authentication authentication) {
        Object claim = authentication.getAttributes().get(PERMISSIONS_CLAIM);
        if (claim instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        return Set.of();
    }
}
