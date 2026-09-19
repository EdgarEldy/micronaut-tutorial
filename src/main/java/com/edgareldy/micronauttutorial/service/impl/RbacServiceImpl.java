package com.edgareldy.micronauttutorial.service.impl;

import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.rbac.PermissionRequest;
import com.edgareldy.micronauttutorial.dto.rbac.PermissionResponse;
import com.edgareldy.micronauttutorial.dto.rbac.RoleRequest;
import com.edgareldy.micronauttutorial.dto.rbac.RoleResponse;
import com.edgareldy.micronauttutorial.dto.rbac.UserDetailResponse;
import com.edgareldy.micronauttutorial.dto.rbac.UserSummaryResponse;
import com.edgareldy.micronauttutorial.entity.Permission;
import com.edgareldy.micronauttutorial.entity.Role;
import com.edgareldy.micronauttutorial.entity.User;
import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.exception.ResourceNotFoundException;
import com.edgareldy.micronauttutorial.repository.PermissionRepository;
import com.edgareldy.micronauttutorial.repository.RoleRepository;
import com.edgareldy.micronauttutorial.repository.UserRepository;
import com.edgareldy.micronauttutorial.service.AuditLogger;
import com.edgareldy.micronauttutorial.service.RbacService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;

/**
 * Default RbacService. Every mutation writes an audit row (success joins the transaction, refusal is written
 * independently then the exception is thrown). The last-admin rule keeps at least one enabled, unlocked
 * account holding ROLE:WRITE: permissions travel inside the JWT, so a user stripped of a role keeps its
 * access until the token already issued expires, which is why the rule is about database state and not tokens.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Singleton
@Transactional
public class RbacServiceImpl implements RbacService {

    private static final String ROLE = "ROLE";
    private static final String PERMISSION = "PERMISSION";
    private static final String USER = "USER";
    private static final String LAST_ADMIN_MESSAGE =
            "Refused: this would leave no enabled, unlocked account holding ROLE:WRITE";

    /** Key of the PostgreSQL advisory lock serialising every operation that can remove a ROLE:WRITE holder. */
    private static final long LAST_ADMIN_LOCK_KEY = 0x524F4C4557524954L;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final AuditLogger audit;

    public RbacServiceImpl(UserRepository users, RoleRepository roles, PermissionRepository permissions, AuditLogger audit) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.audit = audit;
    }

    // ---------------------------------------------------------------- users

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> listUsers(int page, int size) {
        Pageable pageable = Pageable.from(page, size, Sort.of(Sort.Order.asc("id")));
        return PageResponse.from(users.findAll(pageable), RbacServiceImpl::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetailResponse getUser(Long userId) {
        return toDetail(users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId)));
    }

    @Override
    public UserDetailResponse assignRoleToUser(Long userId, Long roleId) {
        String action = "USER_ROLE_ASSIGN";
        User user = users.findById(userId).orElseThrow(() -> notFound("User not found: " + userId));
        Role role = roles.findById(roleId).orElseThrow(() -> notFound("Role not found: " + roleId));
        if (!user.getRoles().add(role)) {
            throw rejected(action, USER, userId, "User already holds this role", "roleId=" + roleId);
        }
        users.update(user);
        audit.log(action, USER, userId, "roleId=" + roleId);
        return toDetail(user);
    }

    @Override
    public UserDetailResponse removeRoleFromUser(Long userId, Long roleId) {
        String action = "USER_ROLE_REMOVE";
        lockLastAdminRule();
        User user = users.findById(userId).orElseThrow(() -> notFound("User not found: " + userId));
        Role role = roles.findById(roleId).orElseThrow(() -> notFound("Role not found: " + roleId));
        long before = users.countRoleWriteHolders();
        if (!user.getRoles().remove(role)) {
            throw notFound("User does not hold role " + roleId);
        }
        users.update(user);
        requireRoleWriteHolder(action, USER, userId, before, "roleId=" + roleId);
        audit.log(action, USER, userId, "roleId=" + roleId);
        return toDetail(user);
    }

    // ---------------------------------------------------------------- roles

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roles.findAllOrderById().stream().map(RbacServiceImpl::toResponse).toList();
    }

    @Override
    public RoleResponse createRole(RoleRequest request) {
        String action = "ROLE_CREATE";
        String name = request.roleName().trim();
        if (roles.existsByRoleName(name)) {
            throw rejected(action, ROLE, null, "Role name already exists", "roleName=" + name);
        }
        Role role;
        try {
            role = roles.saveAndFlush(new Role(name));
        } catch (DataAccessException e) {
            // Concurrent creation of the same name: the unique constraint decides.
            throw rejected(action, ROLE, null, "Role name already exists", "roleName=" + name);
        }
        audit.log(action, ROLE, role.getId(), "roleName=" + name);
        return toResponse(role);
    }

    @Override
    public RoleResponse updateRole(Long roleId, RoleRequest request) {
        String action = "ROLE_UPDATE";
        String name = request.roleName().trim();
        Role role = roles.findById(roleId).orElseThrow(() -> notFound("Role not found: " + roleId));
        roles.findByRoleName(name).filter(other -> !other.getId().equals(roleId)).ifPresent(other -> {
            throw rejected(action, ROLE, roleId, "Role name already exists", "roleName=" + name);
        });
        String previous = role.getRoleName();
        role.setRoleName(name);
        try {
            roles.update(role);
            roles.flush();
        } catch (DataAccessException e) {
            throw rejected(action, ROLE, roleId, "Role name already exists", "roleName=" + name);
        }
        audit.log(action, ROLE, roleId, "roleName=" + previous + " -> " + name);
        return toResponse(role);
    }

    @Override
    public void deleteRole(Long roleId) {
        String action = "ROLE_DELETE";
        Role role = roles.findById(roleId).orElseThrow(() -> notFound("Role not found: " + roleId));
        long holders = users.countUsersByRoleId(roleId);
        if (holders > 0) {
            throw rejected(action, ROLE, roleId, "Role is still assigned to " + holders + " user(s)", "roleName=" + role.getRoleName());
        }
        roles.delete(role);
        audit.log(action, ROLE, roleId, "roleName=" + role.getRoleName());
    }

    @Override
    public RoleResponse assignPermissionToRole(Long roleId, Long permissionId) {
        String action = "ROLE_PERMISSION_ASSIGN";
        Role role = roles.findById(roleId).orElseThrow(() -> notFound("Role not found: " + roleId));
        Permission permission = permissions.findById(permissionId)
                .orElseThrow(() -> notFound("Permission not found: " + permissionId));
        if (!role.getPermissions().add(permission)) {
            throw rejected(action, ROLE, roleId, "Role already holds this permission", "permissionId=" + permissionId);
        }
        roles.update(role);
        audit.log(action, ROLE, roleId, "permission=" + permission.code());
        return toResponse(role);
    }

    @Override
    public RoleResponse removePermissionFromRole(Long roleId, Long permissionId) {
        String action = "ROLE_PERMISSION_REMOVE";
        lockLastAdminRule();
        Role role = roles.findById(roleId).orElseThrow(() -> notFound("Role not found: " + roleId));
        Permission permission = permissions.findById(permissionId)
                .orElseThrow(() -> notFound("Permission not found: " + permissionId));
        long before = users.countRoleWriteHolders();
        if (!role.getPermissions().remove(permission)) {
            throw notFound("Role does not hold permission " + permissionId);
        }
        roles.update(role);
        requireRoleWriteHolder(action, ROLE, roleId, before, "permission=" + permission.code());
        audit.log(action, ROLE, roleId, "permission=" + permission.code());
        return toResponse(role);
    }

    // ---------------------------------------------------------------- permissions

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissions.findAllOrderById().stream().map(RbacServiceImpl::toResponse).toList();
    }

    @Override
    public PermissionResponse createPermission(PermissionRequest request) {
        String action = "PERMISSION_CREATE";
        String details = request.resource() + ":" + request.action();
        if (permissions.existsByResourceAndAction(request.resource(), request.action())) {
            throw rejected(action, PERMISSION, null, "Permission already exists", details);
        }
        Permission permission;
        try {
            permission = permissions.saveAndFlush(new Permission(request.resource(), request.action()));
        } catch (DataAccessException e) {
            throw rejected(action, PERMISSION, null, "Permission already exists", details);
        }
        audit.log(action, PERMISSION, permission.getId(), details);
        return toResponse(permission);
    }

    @Override
    public PermissionResponse updatePermission(Long permissionId, PermissionRequest request) {
        String action = "PERMISSION_UPDATE";
        lockLastAdminRule();
        Permission permission = permissions.findById(permissionId)
                .orElseThrow(() -> notFound("Permission not found: " + permissionId));
        String previous = permission.code();
        String details = previous + " -> " + request.resource() + ":" + request.action();
        permissions.findByResourceAndAction(request.resource(), request.action())
                .filter(other -> !other.getId().equals(permissionId)).ifPresent(other -> {
                    throw rejected(action, PERMISSION, permissionId, "Permission already exists", details);
                });
        long before = users.countRoleWriteHolders();
        permission.setResource(request.resource());
        permission.setAction(request.action());
        try {
            permissions.update(permission);
            permissions.flush();
        } catch (DataAccessException e) {
            throw rejected(action, PERMISSION, permissionId, "Permission already exists", details);
        }
        requireRoleWriteHolder(action, PERMISSION, permissionId, before, details);
        audit.log(action, PERMISSION, permissionId, details);
        return toResponse(permission);
    }

    @Override
    public void deletePermission(Long permissionId) {
        String action = "PERMISSION_DELETE";
        Permission permission = permissions.findById(permissionId)
                .orElseThrow(() -> notFound("Permission not found: " + permissionId));
        long holders = roles.countRolesByPermissionId(permissionId);
        if (holders > 0) {
            throw rejected(action, PERMISSION, permissionId, "Permission is still assigned to " + holders + " role(s)", permission.code());
        }
        permissions.delete(permission);
        audit.log(action, PERMISSION, permissionId, permission.code());
    }

    // ---------------------------------------------------------------- last-admin rule and helpers

    // Two concurrent requests could each see "two holders left" and each remove one, leaving zero. A
    // PostgreSQL transaction-level advisory lock serialises them: the second blocks here until the first
    // commits or rolls back, then counts against the committed state. It must be the first statement of
    // the operation so nothing stale has been loaded into the persistence context yet.
    private void lockLastAdminRule() {
        roles.acquireAdvisoryLock(LAST_ADMIN_LOCK_KEY);
    }

    /** Flushes the pending change, recounts the holders and refuses (rolling back) if the last one was lost. */
    private void requireRoleWriteHolder(String action, String entityType, Long entityId, long before, String details) {
        users.flush();
        long after = users.countRoleWriteHolders();
        if (before > 0 && after == 0) {
            throw rejected(action, entityType, entityId, LAST_ADMIN_MESSAGE, details);
        }
    }

    // Writes the refusal in an independent transaction, then returns the exception for the caller to throw
    // (which rolls the business transaction back while the audit row stays).
    private BusinessRuleException rejected(String action, String entityType, Long entityId, String message, String details) {
        audit.logRejected(action, entityType, entityId, message + " (" + details + ")");
        return new BusinessRuleException(message);
    }

    // A missing row is not a mutation nor a business refusal, so it is not audited: otherwise anyone holding a
    // WRITE permission could flood the audit table with lookups of ids that do not exist.
    private static ResourceNotFoundException notFound(String message) {
        return new ResourceNotFoundException(message);
    }

    private static UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.isEnabled(), user.isAccountLocked());
    }

    private static UserDetailResponse toDetail(User user) {
        List<UserDetailResponse.RoleRef> roleRefs = user.getRoles().stream()
                .sorted(Comparator.comparing(Role::getId))
                .map(r -> new UserDetailResponse.RoleRef(r.getId(), r.getRoleName()))
                .toList();
        return new UserDetailResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.isEnabled(), user.isAccountLocked(), roleRefs);
    }

    private static RoleResponse toResponse(Role role) {
        List<PermissionResponse> held = role.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getId))
                .map(RbacServiceImpl::toResponse)
                .toList();
        return new RoleResponse(role.getId(), role.getRoleName(), held);
    }

    private static PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getResource(), permission.getAction());
    }
}
