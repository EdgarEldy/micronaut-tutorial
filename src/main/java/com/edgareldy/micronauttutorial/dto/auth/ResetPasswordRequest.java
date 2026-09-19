package com.edgareldy.micronauttutorial.dto.auth;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /reset-password. The raw token and the new password are never printed: toString masks them.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record ResetPasswordRequest(
        @NotBlank @Size(max = 255) String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***, newPassword=***]";
    }
}
