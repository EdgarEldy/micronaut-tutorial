package com.edgareldy.micronauttutorial.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Public view of an account (never the password hash), with the permission codes carried by its roles.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        boolean enabled,
        // ALWAYS: an empty permission list is written as [] instead of being omitted.
        @JsonInclude(JsonInclude.Include.ALWAYS) List<String> permissions
) {
}
