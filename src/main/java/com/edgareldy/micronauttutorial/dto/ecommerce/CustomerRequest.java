package com.edgareldy.micronauttutorial.dto.ecommerce;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of customer creation and update; telephone, email and address are optional (blank means absent).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record CustomerRequest(
        @NotBlank(message = "must not be blank") @Size(max = 100, message = "must be at most 100 characters") String firstName,
        @NotBlank(message = "must not be blank") @Size(max = 100, message = "must be at most 100 characters") String lastName,
        @Size(max = 50, message = "must be at most 50 characters") String telephone,
        // @Email would reject an empty string, but a blank optional value means "absent" here (the service turns it
        // into null), so the pattern accepts either blank text or something shaped like an address.
        @Pattern(regexp = "^\\s*$|^[^@\\s]+@[^@\\s]+$", message = "must be a valid email address") @Size(max = 255, message = "must be at most 255 characters") String email,
        @Size(max = 255, message = "must be at most 255 characters") String address
) {
}
