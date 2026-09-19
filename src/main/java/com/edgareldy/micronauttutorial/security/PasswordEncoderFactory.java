package com.edgareldy.micronauttutorial.security;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Produces the BCrypt password encoder bean used to hash and verify account passwords.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Micronaut Security 4 ships no password encoder (the README's BCryptPasswordEncoder does not exist there),
// so the spring-security-crypto implementation is exposed as a bean through a @Factory (library class,
// cannot be annotated). Strength 10 (2^10 rounds) is the library default.
@Factory
public class PasswordEncoderFactory {

    @Singleton
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
