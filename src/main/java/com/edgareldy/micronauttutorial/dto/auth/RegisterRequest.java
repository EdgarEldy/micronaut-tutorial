package com.edgareldy.micronauttutorial.dto.auth;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /register. The password is never printed: toString masks it.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Bean Validation constraints on the record components are checked by Micronaut Validation (compile-time
// generated) when the controller parameter is annotated @Valid; a violation becomes a 400 ApiResponse.
// Max 72 on the password: bcrypt only reads the first 72 bytes, longer values would silently be truncated.
@Serdeable
public record RegisterRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {

    @Override
    public String toString() {
        return "RegisterRequest[firstName=" + firstName + ", lastName=" + lastName + ", email=" + email
                + ", password=***]";
    }
}
