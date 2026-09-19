package com.edgareldy.micronauttutorial.dto.rbac;

import io.micronaut.serde.annotation.Serdeable;

/**
 * List item view of a user (never the password hash).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record UserSummaryResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        boolean enabled,
        boolean accountLocked
) {
}
