package com.edgareldy.micronauttutorial.security;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;

/**
 * Typed binding of app.tokens.*: lifetime of activation tokens (default 24h) and reset tokens (default 1h).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@ConfigurationProperties("app.tokens")
public record TokenProperties(
        @Bindable(defaultValue = "24h") Duration activationTtl,
        @Bindable(defaultValue = "1h") Duration resetTtl
) {
}
