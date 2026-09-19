package com.edgareldy.micronauttutorial.config;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that Flyway applied V1 on the Test Resources PostgreSQL and that the schema of both
 * domains, with its key constraints, is in place.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// The injected DataSource points at the PostgreSQL container started by Test Resources; Flyway ran
// its migrations at application startup, before this test executes.
@MicronautTest
class FlywayMigrationTest {

    private static final Set<String> TABLES = Set.of("users", "roles", "permissions", "role_user",
            "role_permission", "activation_tokens", "blacklisted_tokens", "password_reset_tokens",
            "audit_logs", "categories", "products", "customers", "orders");

    @Inject
    DataSource dataSource;

    @Test
    void versionOneIsAppliedSuccessfully() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT success FROM flyway_schema_history WHERE version = '1'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "migration V1 is missing from flyway_schema_history");
            assertTrue(rs.getBoolean(1));
        }
    }

    @Test
    void versionTwoSeedsTheFourteenBaselinePermissionsAndTheAdminRoleHoldingAll() throws SQLException {
        Set<String> expected = new HashSet<>();
        for (String resource : new String[]{"USER", "ROLE", "PERMISSION", "CATEGORY", "PRODUCT", "CUSTOMER", "ORDER"}) {
            expected.add(resource + ":READ");
            expected.add(resource + ":WRITE");
        }
        assertEquals(14, expected.size());
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT success FROM flyway_schema_history WHERE version = '2'");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "migration V2 is missing from flyway_schema_history");
                assertTrue(rs.getBoolean(1));
            }
            Set<String> permissions = new HashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT resource || ':' || action FROM permissions");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    permissions.add(rs.getString(1));
                }
            }
            assertEquals(expected, permissions);
            Set<String> adminHolds = new HashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT p.resource || ':' || p.action FROM roles r "
                    + "JOIN role_permission rp ON rp.role_id = r.id JOIN permissions p ON p.id = rp.permission_id "
                    + "WHERE r.role_name = 'ADMIN'");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    adminHolds.add(rs.getString(1));
                }
            }
            assertEquals(expected, adminHolds);
        }
    }

    @Test
    void allTablesOfBothDomainsExist() throws SQLException {
        Set<String> found = new HashSet<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                found.add(rs.getString(1));
            }
        }
        found.remove("flyway_schema_history");
        assertEquals(TABLES, found);
    }

    @Test
    void keyConstraintsExist() throws SQLException {
        assertConstraint("users", "uk_users_email", "UNIQUE");
        assertConstraint("permissions", "uk_permissions_resource_action", "UNIQUE");
        assertConstraint("products", "fk_products_category", "FOREIGN KEY");
    }

    private void assertConstraint(String table, String name, String type) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT constraint_type FROM information_schema.table_constraints "
                             + "WHERE table_schema = current_schema() AND table_name = ? AND constraint_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "missing constraint " + name + " on " + table);
                assertEquals(type, rs.getString(1));
            }
        }
    }
}
