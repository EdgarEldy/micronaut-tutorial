package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.rbac.PermissionRequest;
import com.edgareldy.micronauttutorial.dto.rbac.PermissionResponse;
import com.edgareldy.micronauttutorial.dto.rbac.RoleRequest;
import com.edgareldy.micronauttutorial.dto.rbac.RoleResponse;
import com.edgareldy.micronauttutorial.dto.rbac.UserDetailResponse;
import com.edgareldy.micronauttutorial.dto.rbac.UserSummaryResponse;

import java.util.List;

/**
 * Identity and access management use cases: users (read and role assignment), roles and permissions (CRUD),
 * with the assignments always flowing permission to role and role to user. Every mutation is audited.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public interface RbacService {

    /** Users page (0 based) ordered by id. */
    PageResponse<UserSummaryResponse> listUsers(int page, int size);

    /** @throws com.edgareldy.micronauttutorial.exception.ResourceNotFoundException if the user does not exist */
    UserDetailResponse getUser(Long userId);

    /** Assigns a role to an existing user. Refused (422) if the user already holds it. */
    UserDetailResponse assignRoleToUser(Long userId, Long roleId);

    /** Removes a role from a user. Refused (422) if it would leave no enabled, unlocked ROLE:WRITE holder. */
    UserDetailResponse removeRoleFromUser(Long userId, Long roleId);

    /** Roles ordered by id, with their permissions. */
    List<RoleResponse> listRoles();

    /** Refused (422) if the name is already taken. */
    RoleResponse createRole(RoleRequest request);

    /** Renames the role (name only). Refused (422) if the name is taken by another role. */
    RoleResponse updateRole(Long roleId, RoleRequest request);

    /** Refused (422) if any user still holds the role. */
    void deleteRole(Long roleId);

    /** Assigns a permission onto a role. Refused (422) if the role already holds it. */
    RoleResponse assignPermissionToRole(Long roleId, Long permissionId);

    /** Removes a permission from a role. Refused (422) if it removes the last ROLE:WRITE holder. */
    RoleResponse removePermissionFromRole(Long roleId, Long permissionId);

    /** Permissions ordered by id. */
    List<PermissionResponse> listPermissions();

    /** Refused (422) if the RESOURCE and ACTION pair already exists. */
    PermissionResponse createPermission(PermissionRequest request);

    /** Refused (422) on a duplicate pair or if it alters ROLE:WRITE and removes the last holder. */
    PermissionResponse updatePermission(Long permissionId, PermissionRequest request);

    /** Refused (422) if any role still holds the permission. */
    void deletePermission(Long permissionId);
}
