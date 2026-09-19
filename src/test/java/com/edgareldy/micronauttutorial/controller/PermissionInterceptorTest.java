package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.support.RbacTestSupport;
import com.edgareldy.micronauttutorial.support.RbacTestSupport.Actor;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.edgareldy.micronauttutorial.support.RbacTestSupport.MISSING;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.assertError;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.uniqueResource;
import static com.edgareldy.micronauttutorial.support.RbacTestSupport.uniqueRoleName;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the compile-time permission check: for every protected endpoint of the three RBAC controllers, 401
 * without a token, 403 with a token lacking the permission, success with the exact permission, and READ never
 * granting WRITE. Also checks that the AOP is woven at compile time.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@MicronautTest(transactional = false)
class PermissionInterceptorTest {

    /**
     * One protected endpoint: the HTTP call, the permission guarding it, and the status a permitted call ends with.
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : micronaut-tutorial
     */
    private record Endpoint(String method, String path, String resource, String action, Object body, int allowedStatus) {

        String code() {
            return resource + ":" + action;
        }

        ValidatableResponse call(RequestSpecification spec) {
            RequestSpecification request = body == null ? spec : spec.contentType(ContentType.JSON).body(body);
            return request.request(method, path).then();
        }
    }

    @Inject
    EmbeddedServer server;

    @Inject
    ConnectionOperations<Connection> connections;

    // Injecting the controllers gives the bean the container really built, which is what the check below inspects.
    @Inject
    RoleController roleController;

    @Inject
    UserController userController;

    @Inject
    PermissionController permissionController;

    RbacTestSupport rbac;

    @BeforeEach
    void setUp() {
        rbac = new RbacTestSupport(server, connections);
    }

    @AfterEach
    void tearDown() {
        rbac.close();
    }

    private List<Endpoint> endpoints(long existingUserId) {
        String users = "/api/v1/users";
        String roles = "/api/v1/roles";
        String perms = "/api/v1/permissions";
        String gone = "/" + MISSING;
        List<Endpoint> list = new ArrayList<>();
        // Permitted calls on an unknown id end with 404: the interceptor let them reach the service.
        list.add(new Endpoint("GET", users, "USER", "READ", null, 200));
        list.add(new Endpoint("GET", users + "/" + existingUserId, "USER", "READ", null, 200));
        list.add(new Endpoint("PATCH", users + gone + "/roles/" + MISSING, "USER", "WRITE", null, 404));
        list.add(new Endpoint("DELETE", users + gone + "/roles/" + MISSING, "USER", "WRITE", null, 404));
        list.add(new Endpoint("GET", roles, "ROLE", "READ", null, 200));
        list.add(new Endpoint("POST", roles, "ROLE", "WRITE", Map.of("roleName", uniqueRoleName()), 201));
        list.add(new Endpoint("PUT", roles + gone, "ROLE", "WRITE", Map.of("roleName", uniqueRoleName()), 404));
        list.add(new Endpoint("DELETE", roles + gone, "ROLE", "WRITE", null, 404));
        list.add(new Endpoint("POST", roles + gone + "/permissions/" + MISSING, "ROLE", "WRITE", null, 404));
        list.add(new Endpoint("DELETE", roles + gone + "/permissions/" + MISSING, "ROLE", "WRITE", null, 404));
        list.add(new Endpoint("GET", perms, "PERMISSION", "READ", null, 200));
        Map<String, String> newPermission = new LinkedHashMap<>();
        newPermission.put("resource", uniqueResource());
        newPermission.put("action", "READ");
        list.add(new Endpoint("POST", perms, "PERMISSION", "WRITE", newPermission, 201));
        list.add(new Endpoint("PUT", perms + gone, "PERMISSION", "WRITE", newPermission, 404));
        list.add(new Endpoint("DELETE", perms + gone, "PERMISSION", "WRITE", null, 404));
        return list;
    }

    @Test
    void everyProtectedEndpointEnforcesItsPermission() {
        Actor unrelated = rbac.actor("CATEGORY:READ");
        Map<String, Actor> exact = new LinkedHashMap<>();
        for (String code : RbacTestSupport.ALL_RBAC) {
            exact.put(code, rbac.actor(code));
        }

        for (Endpoint endpoint : endpoints(unrelated.userId())) {
            // No token: Micronaut Security refuses before the method (401 in the ApiResponse format).
            assertError(endpoint.call(given()), 401);
            assertError(endpoint.call(RbacTestSupport.as("not-a-jwt")), 401);

            // A valid token without the permission: the interceptor answers 403 "Access denied".
            assertError(endpoint.call(RbacTestSupport.as(unrelated.token())), 403)
                    .body("message", equalTo("Access denied"));

            // The exact permission passes.
            endpoint.call(RbacTestSupport.as(exact.get(endpoint.code()).token()))
                    .statusCode(endpoint.allowedStatus())
                    .body("success", equalTo(endpoint.allowedStatus() < 400));

            // READ does not grant WRITE.
            if (endpoint.action().equals("WRITE")) {
                Actor readOnly = exact.get(endpoint.resource() + ":READ");
                assertError(endpoint.call(RbacTestSupport.as(readOnly.token())), 403)
                        .body("message", equalTo("Access denied"));
            }
        }
    }

    @Test
    void writeDoesNotGrantReadEither() {
        Actor writeOnly = rbac.actor("ROLE:WRITE");

        assertError(RbacTestSupport.as(writeOnly.token()).get("/api/v1/roles").then(), 403);
    }

    // The permission check is Micronaut compile-time AOP: the annotation processor generates a subclass of each
    // controller ($X$Definition$Intercepted) with the interceptor calls inlined. No JDK/CGLIB proxy is created
    // at startup, and the bean the container hands out is an instance of that generated class.
    @Test
    void theAspectIsWovenAtCompileTimeIntoGeneratedSubclasses() throws ClassNotFoundException {
        String base = "com.edgareldy.micronauttutorial.controller.$";
        Object[][] controllers = {{"RoleController", roleController}, {"UserController", userController},
                {"PermissionController", permissionController}};

        for (Object[] entry : controllers) {
            Class<?> generated = Class.forName(base + entry[0] + "$Definition$Intercepted");
            assertEquals(generated, entry[1].getClass(), "bean class of " + entry[0]);
            assertEquals(Class.forName("com.edgareldy.micronauttutorial.controller." + entry[0]), generated.getSuperclass());
        }
    }
}
