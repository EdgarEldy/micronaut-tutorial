package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.support.RbacTestSupport;
import com.edgareldy.micronauttutorial.support.RbacTestSupport.Actor;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end tests of /api/v1/users over real HTTP: pagination bounds and arithmetic, user detail with roles,
 * role assignment and removal (bodiless requests), the rejections, and the absence of any user creation endpoint.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@MicronautTest(transactional = false)
class UserControllerTest {

    private static final String USERS = "/api/v1/users";

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

    @Test
    void listIsPagedWithTheDocumentedShape() {
        rbac.createUser(true, false);
        rbac.createUser(false, true);

        as(admin.token()).queryParam("page", 0).queryParam("size", 2).get(USERS).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.content", hasSize(2))
                .body("data.page", equalTo(0))
                .body("data.size", equalTo(2))
                .body("data.totalElements", org.hamcrest.Matchers.greaterThanOrEqualTo(3))
                .body("data.content[0].email", org.hamcrest.Matchers.notNullValue())
                .body("data.content[0]", not(hasKey("password")));

        // Defaults: page 0, size 20.
        as(admin.token()).get(USERS).then().statusCode(200).body("data.page", equalTo(0)).body("data.size", equalTo(20));
    }

    @Test
    void everySizeFromOneToOneHundredIsAcceptedAndTheArithmeticHolds() {
        for (int size = 1; size <= 100; size++) {
            JsonPath body = as(admin.token()).queryParam("size", size).get(USERS).then().statusCode(200)
                    .extract().jsonPath();
            long total = body.getLong("data.totalElements");
            assertEquals(size, body.getInt("data.size"));
            assertEquals((total + size - 1) / size, body.getLong("data.totalPages"), "totalPages for size " + size);
            assertTrue(body.getList("data.content").size() <= size);
        }
    }

    @Test
    void pageBeyondTheEndIsEmptyButValid() {
        as(admin.token()).queryParam("page", 100000).queryParam("size", 10).get(USERS).then()
                .statusCode(200).body("data.content", hasSize(0)).body("data.page", equalTo(100000));
    }

    @Test
    void outOfBoundsPaginationIsRefusedWith400() {
        assertError(as(admin.token()).queryParam("size", 101).get(USERS).then(), 400);
        assertError(as(admin.token()).queryParam("size", 0).get(USERS).then(), 400);
        assertError(as(admin.token()).queryParam("page", -1).get(USERS).then(), 400);
    }

    @Test
    void detailIncludesTheAssignedRolesAndAnEmptyArrayWhenThereAreNone() {
        long roleId = rbac.createRole();
        long withRole = rbac.createUser(true, false);
        long without = rbac.createUser(true, false);
        rbac.assignRole(withRole, roleId);

        as(admin.token()).get(USERS + "/" + withRole).then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.id", equalTo((int) withRole))
                .body("data.roles", hasSize(1))
                .body("data.roles[0].id", equalTo((int) roleId))
                .body("data.roles[0].roleName", org.hamcrest.Matchers.startsWith("QA_"))
                .body("data", not(hasKey("password")));
        as(admin.token()).get(USERS + "/" + without).then().statusCode(200).body("data.roles", hasSize(0));
        assertError(as(admin.token()).get(USERS + "/" + MISSING).then(), 404).body("message", containsString("not found"));
    }

    @Test
    void assignThenRemoveARoleWithBodilessRequests() {
        long roleId = rbac.createRole();
        long userId = rbac.createUser(true, false);
        String url = USERS + "/" + userId + "/roles/" + roleId;

        as(admin.token()).patch(url).then().statusCode(200).body("data.roles", hasSize(1));
        assertTrue(rbac.userHasRole(userId, roleId));
        as(admin.token()).delete(url).then().statusCode(200).body("data.roles", hasSize(0));
        assertFalse(rbac.userHasRole(userId, roleId));
    }

    // Non-regression: an endpoint without a body must not demand a Content-Type, and must accept the form or JSON
    // Content-Type REST Assured and other clients add by default.
    @Test
    void bodilessPatchAndDeleteAcceptAnyContentType() {
        long roleId = rbac.createRole();
        long userId = rbac.createUser(true, false);
        String url = USERS + "/" + userId + "/roles/" + roleId;

        as(admin.token()).contentType(ContentType.URLENC).patch(url).then().statusCode(200);
        as(admin.token()).contentType(ContentType.URLENC).delete(url).then().statusCode(200);
        as(admin.token()).contentType(ContentType.JSON).patch(url).then().statusCode(200);
        as(admin.token()).contentType(ContentType.JSON).delete(url).then().statusCode(200);
    }

    @Test
    void assignAndRemoveRejections() {
        long roleId = rbac.createRole();
        long userId = rbac.createUser(true, false);
        String url = USERS + "/" + userId + "/roles/" + roleId;

        assertError(as(admin.token()).delete(url).then(), 404).body("message", containsString("does not hold"));
        as(admin.token()).patch(url).then().statusCode(200);
        assertError(as(admin.token()).patch(url).then(), 422).body("message", containsString("already holds"));
        assertError(as(admin.token()).patch(USERS + "/" + MISSING + "/roles/" + roleId).then(), 404);
        assertError(as(admin.token()).patch(USERS + "/" + userId + "/roles/" + MISSING).then(), 404);
        assertError(as(admin.token()).delete(USERS + "/" + MISSING + "/roles/" + roleId).then(), 404);
        assertError(as(admin.token()).delete(USERS + "/" + userId + "/roles/" + MISSING).then(), 404);
        assertEquals(1, rbac.auditCount("REJECTED_USER_ROLE_ASSIGN", "USER", userId));
    }

    @Test
    void usersCannotBeCreatedThroughTheUserController() {
        // Registration stays exclusively the job of /auth/register: the collection has no POST route (405).
        assertError(asJson(admin.token()).body(Map.of("email", "x@example.com")).post(USERS).then(), 405);
    }
}
