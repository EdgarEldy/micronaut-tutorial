package com.edgareldy.micronauttutorial.dto.rbac;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Detail view of a user: the summary fields plus the roles it holds (ordered by id, [] when none).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record UserDetailResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        boolean enabled,
        boolean accountLocked,
        // ALWAYS: an empty role list is written as [] instead of being omitted.
        @JsonInclude(JsonInclude.Include.ALWAYS) List<RoleRef> roles
) {

    /**
     * Small id and name reference to a role held by a user.
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : micronaut-tutorial
     */
    @Serdeable
    public record RoleRef(Long id, String roleName) {
    }
}
