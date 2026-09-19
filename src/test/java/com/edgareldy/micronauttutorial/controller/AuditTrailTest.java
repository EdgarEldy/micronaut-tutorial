package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.entity.AuditLog;
import com.edgareldy.micronauttutorial.repository.AuditLogRepository;
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
import java.util.List;
import java.util.Map;

import static com.edgareldy.micronauttutorial.support.RbacTestSupport.MISSING;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.as;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.asJson;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.uniqueResource;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.uniqueRoleName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that every RBAC mutation writes exactly one matching audit_logs row (action, entity type, entity id,
 * actor = the caller, creation time), that refusals are audited with the REJECTED_ prefix, and that unknown ids are not.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@MicronautTest(transactional = false)
class AuditTrailTest {

    @Inject
    EmbeddedServer server;

    @Inject
    ConnectionOperations<Connection> connections;

    // A Micronaut Data repository can be injected in the test as well: each call runs in its own transaction.
    @Inject
    AuditLogRepository auditLogs;

    RbacTestSupport rbac;
    Actor admin;

    @BeforeEach
    void setUp() {
        rbac = new RbacTestSupport(server, connections);
        admin = rbac.admin();
    }

    @AfterEach
    void tearDown() {
        rbac.close();
    }

    /** Asserts exactly one row with this action for the entity, written by the caller, with a creation time. */
    private void assertOneRow(String action, String entityType, long entityId) {
        List<AuditLog> rows = auditLogs.findByEntityTypeAndEntityIdOrderById(entityType, entityId).stream()
                .filter(row -> row.getAction().equals(action)).toList();
        assertEquals(1, rows.size(), action);
        AuditLog row = rows.get(0);
        assertEquals(entityType, row.getEntityType());
        assertEquals(entityId, row.getEntityId());
        assertEquals(admin.userId(), row.getActorUserId(), action + " actor");
        assertNotNull(row.getCreatedAt(), action + " created_at");
        assertNotNull(row.getDetails(), action + " details");
    }

    @Test
    void everyMutationWritesExactlyOneMatchingRow() {
        String token = admin.token();

        long roleId = asJson(token).body(Map.of("roleName", uniqueRoleName())).post("/api/v1/roles").then()
                .statusCode(201).extract().jsonPath().getLong("data.id");
        assertOneRow("ROLE_CREATE", "ROLE", roleId);

        asJson(token).body(Map.of("roleName", uniqueRoleName())).put("/api/v1/roles/" + roleId).then().statusCode(200);
        assertOneRow("ROLE_UPDATE", "ROLE", roleId);

        long permissionId = asJson(token).body(Map.of("resource", uniqueResource(), "action", "READ"))
                .post("/api/v1/permissions").then().statusCode(201).extract().jsonPath().getLong("data.id");
        assertOneRow("PERMISSION_CREATE", "PERMISSION", permissionId);

        asJson(token).body(Map.of("resource", uniqueResource(), "action", "WRITE"))
                .put("/api/v1/permissions/" + permissionId).then().statusCode(200);
        assertOneRow("PERMISSION_UPDATE", "PERMISSION", permissionId);

        as(token).post("/api/v1/roles/" + roleId + "/permissions/" + permissionId).then().statusCode(200);
        assertOneRow("ROLE_PERMISSION_ASSIGN", "ROLE", roleId);

        as(token).delete("/api/v1/roles/" + roleId + "/permissions/" + permissionId).then().statusCode(200);
        assertOneRow("ROLE_PERMISSION_REMOVE", "ROLE", roleId);

        long userId = rbac.createUser(true, false);
        as(token).patch("/api/v1/users/" + userId + "/roles/" + roleId).then().statusCode(200);
        assertOneRow("USER_ROLE_ASSIGN", "USER", userId);

        as(token).delete("/api/v1/users/" + userId + "/roles/" + roleId).then().statusCode(200);
        assertOneRow("USER_ROLE_REMOVE", "USER", userId);

        as(token).delete("/api/v1/roles/" + roleId).then().statusCode(200);
        assertOneRow("ROLE_DELETE", "ROLE", roleId);

        as(token).delete("/api/v1/permissions/" + permissionId).then().statusCode(200);
        assertOneRow("PERMISSION_DELETE", "PERMISSION", permissionId);

        // 10 mutations above, one row each and nothing else.
        assertEquals(10, rbac.auditCountByActor(admin.userId()));
    }

    @Test
    void refusedDuplicatesAreAuditedWithTheRejectedPrefix() {
        String name = uniqueRoleName();
        asJson(admin.token()).body(Map.of("roleName", name)).post("/api/v1/roles").then().statusCode(201);
        long before = rbac.auditCountByActor(admin.userId());

        asJson(admin.token()).body(Map.of("roleName", name)).post("/api/v1/roles").then().statusCode(422);

        assertEquals(before + 1, rbac.auditCountByActor(admin.userId()));
        assertEquals(1, rbac.db().queryLong(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'REJECTED_ROLE_CREATE' AND entity_type = 'ROLE' "
                        + "AND entity_id IS NULL AND actor_user_id = ? AND details LIKE ?",
                admin.userId(), "%" + name + "%"));
    }

    @Test
    void unknownIdsAreNotAudited() {
        String token = admin.token();
        long before = rbac.auditCountByActor(admin.userId());
        long roleId = rbac.createRole();
        long userId = rbac.createUser(true, false);

        asJson(token).body(Map.of("roleName", uniqueRoleName())).put("/api/v1/roles/" + MISSING).then().statusCode(404);
        as(token).delete("/api/v1/roles/" + MISSING).then().statusCode(404);
        as(token).post("/api/v1/roles/" + MISSING + "/permissions/" + MISSING).then().statusCode(404);
        as(token).delete("/api/v1/roles/" + roleId + "/permissions/" + MISSING).then().statusCode(404);
        as(token).patch("/api/v1/users/" + MISSING + "/roles/" + roleId).then().statusCode(404);
        as(token).delete("/api/v1/users/" + userId + "/roles/" + MISSING).then().statusCode(404);
        as(token).delete("/api/v1/users/" + userId + "/roles/" + roleId).then().statusCode(404);
        asJson(token).body(Map.of("resource", uniqueResource(), "action", "READ"))
                .put("/api/v1/permissions/" + MISSING).then().statusCode(404);
        as(token).delete("/api/v1/permissions/" + MISSING).then().statusCode(404);

        assertEquals(before, rbac.auditCountByActor(admin.userId()));
    }
}
