package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.support.RbacTestSupport;
import com.edgareldy.micronauttutorial.support.RbacTestSupport.Actor;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Map;

import static com.edgareldy.micronauttutorial.support.RbacTestSupport.MISSING;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.as;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.asJson;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.assertError;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.uniqueRoleName;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end tests of /api/v1/roles over real HTTP: CRUD, permission assignment, list shape, rejections in the
 * ApiResponse error format, and the "still assigned to a user" refusal with its audit row.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @MicronautTest starts the whole application (Netty on a random port, Flyway, Test Resources PostgreSQL) once
// for the class. transactional = false so rows written by the JDBC fixtures are committed and visible to the
// server threads. Everything is created with unique names and swept by RbacTestSupport.close().
@MicronautTest(transactional = false)
class RoleControllerTest {

    private static final String ROLES = "/api/v1/roles";

    @Inject
    EmbeddedServer server;

    @Inject
    ConnectionOperations<Connection> connections;

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

    private long createViaApi(String name) {
        return asJson(admin.token()).body(Map.of("roleName", name)).post(ROLES).then().statusCode(201)
                .extract().jsonPath().getLong("data.id");
    }

    @Test
    void createReturns201WithAnEmptyPermissionArrayAndTrimsTheName() {
        String name = uniqueRoleName();

        asJson(admin.token()).body(Map.of("roleName", "  " + name + "  ")).post(ROLES).then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("timestamp", notNullValue())
                .body("data.id", notNullValue())
                .body("data.roleName", equalTo(name))
                .body("data.permissions", hasSize(0));
    }

    @Test
    void listIncludesPermissionsAndSerializesEmptyListsAsArrays() {
        String name = uniqueRoleName();
        long id = createViaApi(name);
        long withPermission = createViaApi(uniqueRoleName());
        as(admin.token()).post(ROLES + "/" + withPermission + "/permissions/" + rbac.permissionId("CATEGORY:READ"))
                .then().statusCode(200);

        as(admin.token()).get(ROLES).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.find { it.id == " + id + " }.permissions", hasSize(0))
                .body("data.find { it.id == " + withPermission + " }.permissions[0].resource", equalTo("CATEGORY"))
                .body("data.find { it.id == " + withPermission + " }.permissions[0].action", equalTo("READ"))
                .body("data.find { it.roleName == 'ADMIN' }.permissions.size()", greaterThan(0));
    }

    @Test
    void createRejectsADuplicateNameWith422() {
        String name = uniqueRoleName();
        createViaApi(name);

        assertError(asJson(admin.token()).body(Map.of("roleName", name)).post(ROLES).then(), 422)
                .body("message", containsString("already exists"));
        assertEquals(1, rbac.db().queryLong("SELECT COUNT(*) FROM roles WHERE role_name = ?", name));
    }

    @Test
    void createRejectsInvalidNamesWith400() {
        assertError(asJson(admin.token()).body(Map.of("roleName", "   ")).post(ROLES).then(), 400)
                .body("message", containsString("must not be blank"));
        assertError(asJson(admin.token()).body(Map.of()).post(ROLES).then(), 400)
                .body("message", containsString("must not be blank"));
        assertError(asJson(admin.token()).body(Map.of("roleName", "x".repeat(101))).post(ROLES).then(), 400)
                .body("message", containsString("at most 100"));
    }

    @Test
    void updateRenamesRefusesDuplicatesAndUnknownIds() {
        long id = createViaApi(uniqueRoleName());
        String other = uniqueRoleName();
        createViaApi(other);
        String renamed = uniqueRoleName();

        asJson(admin.token()).body(Map.of("roleName", renamed)).put(ROLES + "/" + id).then()
                .statusCode(200).body("success", equalTo(true)).body("data.roleName", equalTo(renamed));
        // Saving the current name again is not a duplicate of itself.
        asJson(admin.token()).body(Map.of("roleName", renamed)).put(ROLES + "/" + id).then().statusCode(200);
        assertError(asJson(admin.token()).body(Map.of("roleName", other)).put(ROLES + "/" + id).then(), 422);
        assertError(asJson(admin.token()).body(Map.of("roleName", " ")).put(ROLES + "/" + id).then(), 400);
        assertError(asJson(admin.token()).body(Map.of("roleName", uniqueRoleName())).put(ROLES + "/" + MISSING).then(), 404)
                .body("message", containsString("not found"));
    }

    @Test
    void deleteRemovesTheRoleAndUnknownIdsAre404() {
        long id = createViaApi(uniqueRoleName());

        as(admin.token()).delete(ROLES + "/" + id).then()
                .statusCode(200).body("success", equalTo(true)).body("data", org.hamcrest.Matchers.nullValue());
        assertFalse(rbac.exists("roles", id));
        assertError(as(admin.token()).delete(ROLES + "/" + id).then(), 404);
        assertError(as(admin.token()).delete(ROLES + "/" + MISSING).then(), 404);
    }

    @Test
    void assignAndRemovePermissionWithTheirRejections() {
        long roleId = createViaApi(uniqueRoleName());
        long permissionId = rbac.permissionId("CATEGORY:READ");
        String base = ROLES + "/" + roleId + "/permissions/";

        as(admin.token()).post(base + permissionId).then()
                .statusCode(200)
                .body("data.permissions", hasSize(1))
                .body("data.permissions[0].id", equalTo((int) permissionId));
        assertError(as(admin.token()).post(base + permissionId).then(), 422)
                .body("message", containsString("already holds"));
        assertError(as(admin.token()).post(ROLES + "/" + MISSING + "/permissions/" + permissionId).then(), 404);
        assertError(as(admin.token()).post(base + MISSING).then(), 404);

        as(admin.token()).delete(base + permissionId).then().statusCode(200).body("data.permissions", hasSize(0));
        assertError(as(admin.token()).delete(base + permissionId).then(), 404);
        assertError(as(admin.token()).delete(ROLES + "/" + MISSING + "/permissions/" + permissionId).then(), 404);
    }

    // Non-regression: bodiless POST/DELETE must not require a Content-Type, and must also accept the form or JSON
    // Content-Type that HTTP clients often add by default.
    @Test
    void bodilessRequestsWorkWithAnyContentType() {
        long roleId = createViaApi(uniqueRoleName());
        long permissionId = rbac.permissionId("CATEGORY:READ");
        String url = ROLES + "/" + roleId + "/permissions/" + permissionId;

        as(admin.token()).contentType(ContentType.URLENC).post(url).then().statusCode(200);
        as(admin.token()).contentType(ContentType.URLENC).delete(url).then().statusCode(200);
        as(admin.token()).contentType(ContentType.JSON).post(url).then().statusCode(200);
        as(admin.token()).contentType(ContentType.JSON).delete(url).then().statusCode(200);
        as(admin.token()).contentType(ContentType.URLENC).delete(ROLES + "/" + roleId).then().statusCode(200);
    }

    @Test
    void deleteIsRefusedWhileAUserHoldsTheRoleAndSucceedsOnceUnassigned() {
        long roleId = createViaApi(uniqueRoleName());
        long userId = rbac.createUser(true, false);
        rbac.assignRole(userId, roleId);

        assertError(as(admin.token()).delete(ROLES + "/" + roleId).then(), 422)
                .body("message", containsString("still assigned"));
        assertTrue(rbac.exists("roles", roleId));
        assertEquals(1, rbac.auditCount("REJECTED_ROLE_DELETE", "ROLE", roleId));
        assertEquals(0, rbac.auditCount("ROLE_DELETE", "ROLE", roleId));

        rbac.db().execute("DELETE FROM role_user WHERE role_id = ?", roleId);
        as(admin.token()).delete(ROLES + "/" + roleId).then().statusCode(200);
        assertFalse(rbac.exists("roles", roleId));
        assertEquals(1, rbac.auditCount("ROLE_DELETE", "ROLE", roleId));
    }
}
