package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.support.RbacTestSupport;
import com.edgareldy.micronauttutorial.support.RbacTestSupport.Actor;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.edgareldy.micronauttutorial.support.RbacTestSupport.as;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.asJson;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.assertError;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the last-admin rule over HTTP: the database must always keep at least one enabled, unlocked user
 * holding ROLE:WRITE. Covers both removal directions, the permission rename, the allowed cases, holders that
 * do not count, the delete bypass attempts, and genuinely concurrent removals.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// The rule is global (all ROLE:WRITE holders of the shared database), so each test first locks every other
// holder through RbacTestSupport, then builds exactly the holders its scenario needs. The callers are created
// (and log in) before the isolation: they get locked too, so they never count as admins, but their tokens
// keep working.
@MicronautTest(transactional = false)
class LastAdminRuleTest {

    @Inject
    EmbeddedServer server;

    @Inject
    ConnectionOperations<Connection> connections;

    RbacTestSupport rbac;
    Actor caller;
    long roleWriteId;

    @BeforeEach
    void setUp() {
        rbac = new RbacTestSupport(server, connections);
        caller = rbac.admin();
        roleWriteId = rbac.permissionId("ROLE:WRITE");
        rbac.lockOtherRoleWriteHolders();
        assertEquals(0, rbac.roleWriteHolders(), "isolation must leave no other holder");
    }

    @AfterEach
    void tearDown() {
        // Safety net: if the rule were broken, do not leave the shared permission renamed for other tests.
        rbac.db().execute("UPDATE permissions SET resource = 'ROLE', action = 'WRITE' WHERE id = ?", roleWriteId);
        rbac.close();
    }

    private String removeLink(long roleId) {
        return "/api/v1/roles/" + roleId + "/permissions/" + roleWriteId;
    }

    // ---------------------------------------------------------------- refusals, both directions

    @Test
    void removingRoleWriteFromTheRoleOfTheOnlyHolderIsRefused() {
        long role = rbac.createRoleWriteRole();
        rbac.holder(role, true, false);

        assertError(as(caller.token()).delete(removeLink(role)).then(), 422)
                .body("message", containsString("ROLE:WRITE"));

        assertTrue(rbac.roleHasPermission(role, roleWriteId), "the business change must be rolled back");
        assertEquals(1, rbac.roleWriteHolders());
        // The refusal audit row is written in its own transaction, so it survives the rollback.
        assertEquals(1, rbac.auditCount("REJECTED_ROLE_PERMISSION_REMOVE", "ROLE", role));
        assertEquals(0, rbac.auditCount("ROLE_PERMISSION_REMOVE", "ROLE", role));
    }

    @Test
    void removingTheRoleFromTheOnlyHolderIsRefused() {
        long role = rbac.createRoleWriteRole();
        long holder = rbac.holder(role, true, false);

        assertError(as(caller.token()).delete("/api/v1/users/" + holder + "/roles/" + role).then(), 422)
                .body("message", containsString("ROLE:WRITE"));

        assertTrue(rbac.userHasRole(holder, role));
        assertEquals(1, rbac.auditCount("REJECTED_USER_ROLE_REMOVE", "USER", holder));
        assertEquals(0, rbac.auditCount("USER_ROLE_REMOVE", "USER", holder));
    }

    @Test
    void renamingRoleWriteWhenItWouldLeaveNoHolderIsRefused() {
        rbac.holder(rbac.createRoleWriteRole(), true, false);

        assertError(asJson(caller.token()).body(Map.of("resource", "ROLE", "action", "WRITEX"))
                .put("/api/v1/permissions/" + roleWriteId).then(), 422).body("message", containsString("ROLE:WRITE"));

        assertEquals("WRITE", rbac.db().queryString("SELECT action FROM permissions WHERE id = ?", roleWriteId));
        assertEquals(1, rbac.auditCount("REJECTED_PERMISSION_UPDATE", "PERMISSION", roleWriteId));
    }

    // ---------------------------------------------------------------- allowed while another holder exists

    @Test
    void removingRoleWriteFromARoleIsAllowedWhenAnotherHolderRemains() {
        long first = rbac.createRoleWriteRole();
        long second = rbac.createRoleWriteRole();
        rbac.holder(first, true, false);
        rbac.holder(second, true, false);

        as(caller.token()).delete(removeLink(first)).then().statusCode(200);

        assertFalse(rbac.roleHasPermission(first, roleWriteId));
        assertEquals(1, rbac.roleWriteHolders());
        assertEquals(1, rbac.auditCount("ROLE_PERMISSION_REMOVE", "ROLE", first));
    }

    @Test
    void removingTheRoleFromAUserIsAllowedWhenAnotherHolderRemains() {
        long first = rbac.createRoleWriteRole();
        long second = rbac.createRoleWriteRole();
        long firstHolder = rbac.holder(first, true, false);
        rbac.holder(second, true, false);

        as(caller.token()).delete("/api/v1/users/" + firstHolder + "/roles/" + first).then().statusCode(200);

        assertFalse(rbac.userHasRole(firstHolder, first));
        assertEquals(1, rbac.roleWriteHolders());
        assertEquals(1, rbac.auditCount("USER_ROLE_REMOVE", "USER", firstHolder));
    }

    // ---------------------------------------------------------------- who counts as an admin

    @Test
    void aLockedHolderDoesNotCountAsAnAdmin() {
        long role = rbac.createRoleWriteRole();
        rbac.holder(role, true, false);
        rbac.holder(rbac.createRoleWriteRole(), true, true);
        assertEquals(1, rbac.roleWriteHolders());

        assertError(as(caller.token()).delete(removeLink(role)).then(), 422);
        assertTrue(rbac.roleHasPermission(role, roleWriteId));
    }

    @Test
    void aNotEnabledHolderDoesNotCountAsAnAdmin() {
        long role = rbac.createRoleWriteRole();
        rbac.holder(role, true, false);
        rbac.holder(rbac.createRoleWriteRole(), false, false);
        assertEquals(1, rbac.roleWriteHolders());

        assertError(as(caller.token()).delete(removeLink(role)).then(), 422);
        assertTrue(rbac.roleHasPermission(role, roleWriteId));
    }

    // ---------------------------------------------------------------- no bypass through deletion

    @Test
    void deletingTheRoleOrThePermissionCannotBypassTheRule() {
        long role = rbac.createRoleWriteRole();
        rbac.holder(role, true, false);

        assertError(as(caller.token()).delete("/api/v1/roles/" + role).then(), 422);
        assertError(as(caller.token()).delete("/api/v1/permissions/" + roleWriteId).then(), 422);

        assertTrue(rbac.exists("roles", role));
        assertTrue(rbac.exists("permissions", roleWriteId));
        assertEquals(1, rbac.roleWriteHolders());
    }

    // ---------------------------------------------------------------- concurrency

    // Two admins A (role R1) and B (role R2), each request removes the ROLE:WRITE link of the other's only role.
    // Without the advisory lock both would see "two holders" and both would succeed, leaving zero. With it the
    // second request waits for the first, recounts, and is refused.
    @Test
    void twoConcurrentRemovalsOfTheLastTwoHoldersYieldExactlyOneSuccess() throws Exception {
        long first = rbac.createRoleWriteRole();
        long second = rbac.createRoleWriteRole();
        rbac.holder(first, true, false);
        rbac.holder(second, true, false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 1; round <= 8; round++) {
                rbac.grantToRole(first, roleWriteId);
                rbac.grantToRole(second, roleWriteId);
                CountDownLatch start = new CountDownLatch(1);
                CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> remove(start, first), pool);
                CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> remove(start, second), pool);
                start.countDown();
                List<Integer> statuses = new ArrayList<>(List.of(a.get(), b.get()));
                Collections.sort(statuses);

                assertEquals(List.of(200, 422), statuses, "round " + round);
                assertEquals(1, rbac.roleWriteHolders(), "round " + round);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private int remove(CountDownLatch start, long roleId) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return as(caller.token()).delete(removeLink(roleId)).then().extract().statusCode();
    }
}
