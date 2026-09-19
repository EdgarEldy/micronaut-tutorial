package com.edgareldy.micronauttutorial.dto.rbac;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of permission creation and update: an upper case RESOURCE and ACTION pair.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record PermissionRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "must be upper case letters, digits or underscores, starting with a letter")
        String resource,
        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "must be upper case letters, digits or underscores, starting with a letter")
        String action
) {
}
