package com.edgareldy.micronauttutorial.dto.rbac;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of role creation and update (name only).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record RoleRequest(
        @NotBlank(message = "must not be blank") @Size(max = 100, message = "must be at most 100 characters") String roleName
) {
}
