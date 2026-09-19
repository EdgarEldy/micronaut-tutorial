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
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.uniqueResource;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end tests of /api/v1/permissions over real HTTP: CRUD, validation of the resource and action pattern,
 * duplicates, unknown ids, and the "still held by a role" refusal with its audit row.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@MicronautTest(transactional = false)
class PermissionControllerTest {

    private static final String PERMISSIONS = "/api/v1/permissions";

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

    private long createViaApi(String resource, String action) {
        return asJson(admin.token()).body(Map.of("resource", resource, "action", action)).post(PERMISSIONS).then()
                .statusCode(201).extract().jsonPath().getLong("data.id");
    }

    @Test
    void listReturnsTheBaselinePermissionsAsPlainObjects() {
        as(admin.token()).get(PERMISSIONS).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.size()", greaterThanOrEqualTo(14))
                .body("data.find { it.resource == 'ROLE' && it.action == 'WRITE' }.id", notNullValue());
    }

    @Test
    void createReturns201() {
        String resource = uniqueResource();

        asJson(admin.token()).body(Map.of("resource", resource, "action", "READ")).post(PERMISSIONS).then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("data.id", notNullValue())
                .body("data.resource", equalTo(resource))
                .body("data.action", equalTo("READ"));
    }

    @Test
    void createRejectsADuplicateWith422() {
        String resource = uniqueResource();
        createViaApi(resource, "READ");

        assertError(asJson(admin.token()).body(Map.of("resource", resource, "action", "READ")).post(PERMISSIONS).then(), 422)
                .body("message", containsString("already exists"));
        assertEquals(1, rbac.db().queryLong("SELECT COUNT(*) FROM permissions WHERE resource = ?", resource));
    }

    @Test
    void createRejectsBadValuesWith400() {
        for (String bad : new String[]{"lower", "1START", "HAS SPACE", "DASH-ED", " "}) {
            assertError(asJson(admin.token()).body(Map.of("resource", bad, "action", "READ")).post(PERMISSIONS).then(), 400);
            assertError(asJson(admin.token()).body(Map.of("resource", "QAOK", "action", bad)).post(PERMISSIONS).then(), 400);
        }
        assertError(asJson(admin.token()).body(Map.of("resource", "QAOK")).post(PERMISSIONS).then(), 400)
                .body("message", containsString("must not be blank"));
        assertError(asJson(admin.token()).body(Map.of("resource", "lower", "action", "READ")).post(PERMISSIONS).then(), 400)
                .body("message", containsString("upper case"));
        assertError(asJson(admin.token()).body(Map.of("resource", "Q" + "X".repeat(100), "action", "READ")).post(PERMISSIONS).then(), 400)
                .body("message", containsString("at most 100"));
    }

    @Test
    void updateChangesTheValuesAndRefusesDuplicatesUnknownIdsAndBadValues() {
        String resource = uniqueResource();
        long id = createViaApi(resource, "READ");
        String other = uniqueResource();
        createViaApi(other, "READ");
        String renamed = uniqueResource();

        asJson(admin.token()).body(Map.of("resource", renamed, "action", "WRITE")).put(PERMISSIONS + "/" + id).then()
                .statusCode(200).body("data.resource", equalTo(renamed)).body("data.action", equalTo("WRITE"));
        // Saving the same values again is not a duplicate of itself.
        asJson(admin.token()).body(Map.of("resource", renamed, "action", "WRITE")).put(PERMISSIONS + "/" + id).then().statusCode(200);
        assertError(asJson(admin.token()).body(Map.of("resource", other, "action", "READ")).put(PERMISSIONS + "/" + id).then(), 422);
        assertError(asJson(admin.token()).body(Map.of("resource", "bad", "action", "READ")).put(PERMISSIONS + "/" + id).then(), 400);
        assertError(asJson(admin.token()).body(Map.of("resource", renamed, "action", "READ")).put(PERMISSIONS + "/" + MISSING).then(), 404)
                .body("message", containsString("not found"));
    }

    @Test
    void deleteRemovesThePermissionAndUnknownIdsAre404() {
        long id = createViaApi(uniqueResource(), "READ");

        as(admin.token()).delete(PERMISSIONS + "/" + id).then()
                .statusCode(200).body("success", equalTo(true)).body("data", nullValue());
        assertFalse(rbac.exists("permissions", id));
        assertError(as(admin.token()).delete(PERMISSIONS + "/" + id).then(), 404);
        assertError(as(admin.token()).delete(PERMISSIONS + "/" + MISSING).then(), 404);
    }

    @Test
    void bodilessDeleteAcceptsAFormContentType() {
        long id = createViaApi(uniqueResource(), "READ");

        as(admin.token()).contentType(ContentType.URLENC).delete(PERMISSIONS + "/" + id).then().statusCode(200);
    }

    @Test
    void deleteIsRefusedWhileARoleHoldsThePermissionAndSucceedsOnceRemoved() {
        long id = createViaApi(uniqueResource(), "READ");
        long roleId = rbac.createRole();
        rbac.grantToRole(roleId, id);

        assertError(as(admin.token()).delete(PERMISSIONS + "/" + id).then(), 422)
                .body("message", containsString("still assigned"));
        assertTrue(rbac.exists("permissions", id));
        assertEquals(1, rbac.auditCount("REJECTED_PERMISSION_DELETE", "PERMISSION", id));
        assertEquals(0, rbac.auditCount("PERMISSION_DELETE", "PERMISSION", id));

        rbac.db().execute("DELETE FROM role_permission WHERE permission_id = ?", id);
        as(admin.token()).delete(PERMISSIONS + "/" + id).then().statusCode(200);
        assertFalse(rbac.exists("permissions", id));
        assertEquals(1, rbac.auditCount("PERMISSION_DELETE", "PERMISSION", id));
    }
}
