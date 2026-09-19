package com.edgareldy.micronauttutorial.security;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the permission (RESOURCE and ACTION) a caller must hold to invoke the annotated method or every method of the annotated class.
 * Enforced by {@link PermissionInterceptor}.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Compile-time AOP: @Around marks this annotation as advice and @Type binds it to PermissionInterceptor.
// While compiling, the Micronaut annotation processor sees a bean method carrying it and generates a
// subclass of the bean ("Xxx$Intercepted") that routes the call through the interceptor. There is no
// runtime proxy, no bytecode generation at startup and no reflection, which keeps startup fast and the
// native image simple. That is the Micronaut answer to Spring's runtime proxies.
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Around
@Type(PermissionInterceptor.class)
public @interface RequiresPermission {

    /** Resource part of the permission, for example ROLE. */
    String resource();

    /** Action part of the permission, for example WRITE. */
    String action();
}
