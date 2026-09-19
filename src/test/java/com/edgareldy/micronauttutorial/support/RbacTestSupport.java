package com.edgareldy.micronauttutorial.support;

import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.server.EmbeddedServer;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Shared fixture of the RBAC tests: callers holding chosen permissions (token from a real login), roles and users
 * created directly in the database, audit counters, and the isolation of the global last-admin rule. Create one per
 * test and close it in @AfterEach, it removes everything it created and restores every account it locked.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// All test classes share ONE PostgreSQL (Test Resources) and its seeded data, and the last-admin rule counts
// enabled, unlocked users holding ROLE:WRITE across the whole database. lockOtherRoleWriteHolders() therefore
// locks every current holder (account_locked = TRUE, so they stop counting) and close() unlocks them again, also
// when the test failed. The locked callers keep working because a token already issued is not re-checked
// against the account state, only login refuses a locked account.
public final class RbacTestSupport implements AutoCloseable {

    /** Every role name created by a test starts with this prefix so close() can sweep them, whatever created them. */
    public static final String NAME_PREFIX = "QA_";

    /** Every permission resource created by a test starts with this prefix, for the same reason. */
    public static final String RESOURCE_PREFIX = "QA";

    /** An id that exists in no table. */
    public static final long MISSING = 999_999_999L;

    /** The six permissions the RBAC endpoints are protected with. */
    public static final String[] ALL_RBAC = {"USER:READ", "USER:WRITE", "ROLE:READ", "ROLE:WRITE",
            "PERMISSION:READ", "PERMISSION:WRITE"};

    /**
     * A test caller: an enabled user holding a role with the requested permissions, and a bearer token from a real login.
     * <p>
     * Created edgar.muhamyangabo on 9/19/26
     * Author : edgar.muhamyangabo
     * Date : 9/19/26
     * Project : micronaut-tutorial
     */
    public record Actor(long userId, long roleId, String email, String token) {
    }

    private final AuthTestSupport auth;
    private final TestDatabase db;
    private final List<Long> users = new ArrayList<>();
    private final List<Long> lockedByIsolation = new ArrayList<>();

    public RbacTestSupport(EmbeddedServer server, ConnectionOperations<Connection> connections) {
        this.auth = new AuthTestSupport(server, connections);
        this.db = auth.database();
    }

    public AuthTestSupport auth() {
        return auth;
    }

    public TestDatabase db() {
        return db;
    }

    @Override
    public void close() {
        try {
            for (Long id : lockedByIsolation) {
                db.execute("UPDATE users SET account_locked = FALSE WHERE id = ?", id);
            }
            for (Long id : users) {
                db.execute("DELETE FROM audit_logs WHERE actor_user_id = ?", id);
                db.removeUser(id);
            }
            db.execute("DELETE FROM roles WHERE role_name LIKE 'QA\\_%'");
            db.execute("DELETE FROM permissions WHERE resource LIKE 'QA%'");
        } finally {
            auth.close();
        }
    }

    // ---------------------------------------------------------------- fixtures

    /** Creates an enabled user holding a role with the given seeded permission codes (RESOURCE:ACTION) and logs it in. */
    public Actor actor(String... permissionCodes) {
        String email = AuthTestSupport.uniqueEmail();
        long userId = auth.createEnabledUser(email, AuthTestSupport.PASSWORD);
        users.add(userId);
        long roleId = createRole();
        for (String code : permissionCodes) {
            grantToRole(roleId, permissionId(code));
        }
        assignRole(userId, roleId);
        return new Actor(userId, roleId, email, auth.loginToken(email, AuthTestSupport.PASSWORD));
    }

    /** A caller holding the six RBAC permissions. */
    public Actor admin() {
        return actor(ALL_RBAC);
    }

    /** A unique role name that close() sweeps. */
    public static String uniqueRoleName() {
        return NAME_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /** A unique upper case resource or action name accepted by the permission pattern, swept by close(). */
    public static String uniqueResource() {
        return RESOURCE_PREFIX + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    public long createRole() {
        return db.queryLong("INSERT INTO roles (role_name) VALUES (?) RETURNING id", uniqueRoleName());
    }

    /** A role that grants ROLE:WRITE. */
    public long createRoleWriteRole() {
        long roleId = createRole();
        grantToRole(roleId, permissionId("ROLE:WRITE"));
        return roleId;
    }

    /** Inserts a user with the requested state and no role; removed by close(). */
    public long createUser(boolean enabled, boolean locked) {
        long id = auth.createEnabledUser(AuthTestSupport.uniqueEmail(), AuthTestSupport.PASSWORD);
        users.add(id);
        db.execute("UPDATE users SET enabled = ?, account_locked = ? WHERE id = ?", enabled, locked, id);
        return id;
    }

    /** A user in the requested state holding the role. */
    public long holder(long roleId, boolean enabled, boolean locked) {
        long id = createUser(enabled, locked);
        assignRole(id, roleId);
        return id;
    }

    public long permissionId(String code) {
        return db.queryLong("SELECT id FROM permissions WHERE resource || ':' || action = ?", code);
    }

    public void grantToRole(long roleId, long permissionId) {
        db.execute("INSERT INTO role_permission (role_id, permission_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                roleId, permissionId);
    }

    public void assignRole(long userId, long roleId) {
        db.execute("INSERT INTO role_user (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING", userId, roleId);
    }

    public boolean roleHasPermission(long roleId, long permissionId) {
        return db.queryLong("SELECT COUNT(*) FROM role_permission WHERE role_id = ? AND permission_id = ?",
                roleId, permissionId) == 1;
    }

    public boolean userHasRole(long userId, long roleId) {
        return db.queryLong("SELECT COUNT(*) FROM role_user WHERE user_id = ? AND role_id = ?", userId, roleId) == 1;
    }

    public boolean exists(String table, long id) {
        return db.queryLong("SELECT COUNT(*) FROM " + table + " WHERE id = ?", id) == 1;
    }

    /** The number of enabled, unlocked users holding ROLE:WRITE through any role, the value the rule protects. */
    public long roleWriteHolders() {
        return db.queryLong(HOLDERS_SQL);
    }

    private static final String HOLDER_IDS_SQL = "SELECT DISTINCT u.id FROM users u "
            + "JOIN role_user ru ON ru.user_id = u.id JOIN role_permission rp ON rp.role_id = ru.role_id "
            + "JOIN permissions p ON p.id = rp.permission_id "
            + "WHERE u.enabled AND NOT u.account_locked AND p.resource = 'ROLE' AND p.action = 'WRITE'";

    private static final String HOLDERS_SQL = "SELECT COUNT(*) FROM (" + HOLDER_IDS_SQL + ") h";

    /**
     * Locks every user currently counting as a ROLE:WRITE holder (callers included), so that the users created
     * afterwards are the only admins the rule sees. Call it after creating the callers and before the scenario.
     */
    public void lockOtherRoleWriteHolders() {
        for (Long id : db.queryLongs(HOLDER_IDS_SQL)) {
            lockedByIsolation.add(id);
            db.execute("UPDATE users SET account_locked = TRUE WHERE id = ?", id);
        }
    }

    // ---------------------------------------------------------------- audit

    /** Rows of audit_logs with this exact action, entity type and entity id. */
    public long auditCount(String action, String entityType, long entityId) {
        return db.queryLong("SELECT COUNT(*) FROM audit_logs WHERE action = ? AND entity_type = ? AND entity_id = ?",
                action, entityType, entityId);
    }

    /** Rows of audit_logs written on behalf of the actor, whatever the action. */
    public long auditCountByActor(long actorId) {
        return db.queryLong("SELECT COUNT(*) FROM audit_logs WHERE actor_user_id = ?", actorId);
    }

    // ---------------------------------------------------------------- HTTP

    /** A request carrying the bearer token. */
    public static RequestSpecification as(String token) {
        return given().header("Authorization", AuthTestSupport.bearer(token));
    }

    /** A request carrying the bearer token and a JSON content type (for requests with a body). */
    public static RequestSpecification asJson(String token) {
        return as(token).contentType(ContentType.JSON);
    }

    /** Asserts the README error format: success=false, message, data=null (key present), timestamp, and the status. */
    public static ValidatableResponse assertError(ValidatableResponse response, int status) {
        return response.statusCode(status)
                .body("success", equalTo(false))
                .body("message", notNullValue())
                .body("data", nullValue())
                .body("containsKey('data')", equalTo(true))
                .body("timestamp", notNullValue());
    }
}
