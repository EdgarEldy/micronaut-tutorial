package com.edgareldy.micronauttutorial.dto.rbac;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Public view of a role with the permissions it holds (ordered by id, [] when none).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record RoleResponse(
        Long id,
        String roleName,
        // ALWAYS: an empty permission list is written as [] instead of being omitted.
        @JsonInclude(JsonInclude.Include.ALWAYS) List<PermissionResponse> permissions
) {
}
