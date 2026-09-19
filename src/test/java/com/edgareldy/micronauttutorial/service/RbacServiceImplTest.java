package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.rbac.PermissionRequest;
import com.edgareldy.micronauttutorial.dto.rbac.RoleRequest;
import com.edgareldy.micronauttutorial.entity.Permission;
import com.edgareldy.micronauttutorial.entity.Role;
import com.edgareldy.micronauttutorial.entity.User;
import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.exception.ResourceNotFoundException;
import com.edgareldy.micronauttutorial.repository.PermissionRepository;
import com.edgareldy.micronauttutorial.repository.RoleRepository;
import com.edgareldy.micronauttutorial.repository.UserRepository;
import com.edgareldy.micronauttutorial.service.impl.RbacServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the RbacServiceImpl decisions with every collaborator mocked: the last-admin arithmetic, the
 * advisory lock being taken before anything is loaded, and which outcome is sent to the AuditLogger.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Mockito is used as in AuthServiceImplTest: the service is built by hand, so no @Transactional is woven and no
// database is involved. The "holders" count is stubbed with consecutive answers: the value before the change,
// then the value the service reads back after flushing it.
class RbacServiceImplTest {

    private UserRepository users;
    private RoleRepository roles;
    private PermissionRepository permissions;
    private AuditLogger audit;
    private RbacServiceImpl service;

    private Role role;
    private Permission roleWrite;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        permissions = mock(PermissionRepository.class);
        audit = mock(AuditLogger.class);
        service = new RbacServiceImpl(users, roles, permissions, audit);

        role = new Role("R");
        role.setId(10L);
        roleWrite = new Permission("ROLE", "WRITE");
        roleWrite.setId(20L);
        role.getPermissions().add(roleWrite);
        user = new User("A", "B", "a@b.c", "x");
        user.setId(30L);
        user.getRoles().add(role);

        when(roles.findById(10L)).thenReturn(Optional.of(role));
        when(permissions.findById(20L)).thenReturn(Optional.of(roleWrite));
        when(users.findById(30L)).thenReturn(Optional.of(user));
    }

    // ---------------------------------------------------------------- last-admin arithmetic

    @Test
    void removingAPermissionThatEmptiesTheAdminSetIsRefusedAndAuditedAsRejected() {
        when(users.countRoleWriteHolders()).thenReturn(1L, 0L);

        assertThrows(BusinessRuleException.class, () -> service.removePermissionFromRole(10L, 20L));

        verify(audit).logRejected(eq("ROLE_PERMISSION_REMOVE"), eq("ROLE"), eq(10L), contains("ROLE:WRITE"));
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void removingARoleFromTheLastHolderIsRefusedAndAuditedAsRejected() {
        when(users.countRoleWriteHolders()).thenReturn(1L, 0L);

        assertThrows(BusinessRuleException.class, () -> service.removeRoleFromUser(30L, 10L));

        verify(audit).logRejected(eq("USER_ROLE_REMOVE"), eq("USER"), eq(30L), anyString());
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void renamingTheLastRoleWritePermissionIsRefusedAndAuditedAsRejected() {
        when(permissions.findByResourceAndAction("ROLE", "WRITEX")).thenReturn(Optional.empty());
        when(users.countRoleWriteHolders()).thenReturn(1L, 0L);

        assertThrows(BusinessRuleException.class,
                () -> service.updatePermission(20L, new PermissionRequest("ROLE", "WRITEX")));

        verify(audit).logRejected(eq("PERMISSION_UPDATE"), eq("PERMISSION"), eq(20L), anyString());
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void whenNobodyWasAnAdminBeforeTheChangeIsNotRefused() {
        when(users.countRoleWriteHolders()).thenReturn(0L, 0L);

        service.removePermissionFromRole(10L, 20L);

        verify(audit).log(eq("ROLE_PERMISSION_REMOVE"), eq("ROLE"), eq(10L), anyString());
        verify(audit, never()).logRejected(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void whenAnotherHolderRemainsTheRemovalSucceedsAndIsAudited() {
        when(users.countRoleWriteHolders()).thenReturn(2L, 1L);

        service.removeRoleFromUser(30L, 10L);

        verify(audit).log(eq("USER_ROLE_REMOVE"), eq("USER"), eq(30L), anyString());
        verify(audit, never()).logRejected(anyString(), anyString(), anyLong(), anyString());
    }

    // ---------------------------------------------------------------- lock ordering

    @Test
    void removePermissionFromRoleLocksBeforeLoadingAnything() {
        when(users.countRoleWriteHolders()).thenReturn(2L, 1L);

        service.removePermissionFromRole(10L, 20L);

        InOrder order = inOrder(roles, permissions);
        order.verify(roles).acquireAdvisoryLock(anyLong());
        order.verify(roles).findById(10L);
        order.verify(permissions).findById(20L);
    }

    @Test
    void removeRoleFromUserLocksBeforeLoadingAnything() {
        when(users.countRoleWriteHolders()).thenReturn(2L, 1L);

        service.removeRoleFromUser(30L, 10L);

        InOrder order = inOrder(roles, users);
        order.verify(roles).acquireAdvisoryLock(anyLong());
        order.verify(users).findById(30L);
        order.verify(roles).findById(10L);
    }

    @Test
    void updatePermissionLocksBeforeLoadingAnything() {
        when(users.countRoleWriteHolders()).thenReturn(2L, 2L);
        when(permissions.findByResourceAndAction("ROLE", "WRITEX")).thenReturn(Optional.empty());

        service.updatePermission(20L, new PermissionRequest("ROLE", "WRITEX"));

        InOrder order = inOrder(roles, permissions);
        order.verify(roles).acquireAdvisoryLock(anyLong());
        order.verify(permissions).findById(20L);
    }

    // ---------------------------------------------------------------- audit outcome of the other mutations

    @Test
    void successfulMutationsCallAuditLog() {
        when(roles.existsByRoleName("New")).thenReturn(false);
        Role saved = new Role("New");
        saved.setId(11L);
        when(roles.saveAndFlush(org.mockito.ArgumentMatchers.any(Role.class))).thenReturn(saved);

        service.createRole(new RoleRequest("New"));
        verify(audit).log(eq("ROLE_CREATE"), eq("ROLE"), eq(11L), anyString());

        Permission other = new Permission("CATEGORY", "READ");
        other.setId(21L);
        when(permissions.findById(21L)).thenReturn(Optional.of(other));
        service.assignPermissionToRole(10L, 21L);
        verify(audit).log(eq("ROLE_PERMISSION_ASSIGN"), eq("ROLE"), eq(10L), anyString());

        Role free = new Role("Free");
        free.setId(12L);
        when(roles.findById(12L)).thenReturn(Optional.of(free));
        when(users.countUsersByRoleId(12L)).thenReturn(0L);
        service.deleteRole(12L);
        verify(audit).log(eq("ROLE_DELETE"), eq("ROLE"), eq(12L), anyString());
    }

    @Test
    void deletingAReferencedRoleOrPermissionIsRefusedAndAuditedAsRejected() {
        when(users.countUsersByRoleId(10L)).thenReturn(1L);
        when(roles.countRolesByPermissionId(20L)).thenReturn(2L);

        assertThrows(BusinessRuleException.class, () -> service.deleteRole(10L));
        assertThrows(BusinessRuleException.class, () -> service.deletePermission(20L));

        verify(audit).logRejected(eq("ROLE_DELETE"), eq("ROLE"), eq(10L), anyString());
        verify(audit).logRejected(eq("PERMISSION_DELETE"), eq("PERMISSION"), eq(20L), anyString());
        verify(roles, never()).delete(role);
        verify(permissions, never()).delete(roleWrite);
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void unknownIdsAreNotFoundAndNeverAudited() {
        assertThrows(ResourceNotFoundException.class, () -> service.deleteRole(99L));
        assertThrows(ResourceNotFoundException.class, () -> service.assignRoleToUser(99L, 10L));

        verifyNoInteractions(audit);
    }
}
