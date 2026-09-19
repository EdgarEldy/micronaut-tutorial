package com.edgareldy.micronauttutorial.support;

import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plain JDBC helper for tests that must write or read rows directly on the Test Resources PostgreSQL. Use it only from classes running with transactional = false.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Every call opens its own auto-commit connection, so the rows are committed immediately and visible to
// the server threads handling HTTP requests. Under the default @MicronautTest transactional wrapper the
// test thread would hold an uncommitted transaction and the server would not see these writes.
// The injected DataSource of Micronaut Data only hands out connections inside a managed connection scope,
// so ConnectionOperations opens that scope around each statement.
public final class TestDatabase {

    private final ConnectionOperations<Connection> connections;

    public TestDatabase(ConnectionOperations<Connection> connections) {
        this.connections = connections;
    }

    /** Runs an INSERT/UPDATE/DELETE and returns the number of affected rows. */
    public int execute(String sql, Object... params) {
        return connections.execute(ConnectionDefinition.DEFAULT, status -> {
            try (PreparedStatement ps = status.getConnection().prepareStatement(sql)) {
                bind(ps, params);
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException(sql, e);
            }
        });
    }

    /** Runs a query returning one column of one row (null when there is no row). */
    public String queryString(String sql, Object... params) {
        return connections.execute(ConnectionDefinition.DEFAULT, status -> {
            try (PreparedStatement ps = status.getConnection().prepareStatement(sql)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            } catch (SQLException e) {
                throw new IllegalStateException(sql, e);
            }
        });
    }

    /** Runs a query returning one BIGINT column and collects every row. */
    public List<Long> queryLongs(String sql, Object... params) {
        return connections.execute(ConnectionDefinition.DEFAULT, status -> {
            try (PreparedStatement ps = status.getConnection().prepareStatement(sql)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Long> values = new ArrayList<>();
                    while (rs.next()) {
                        values.add(rs.getLong(1));
                    }
                    return values;
                }
            } catch (SQLException e) {
                throw new IllegalStateException(sql, e);
            }
        });
    }

    public long queryLong(String sql, Object... params) {
        return Long.parseLong(queryString(sql, params));
    }

    /**
     * Grants the user a fresh role holding the permission RESOURCE:ACTION (created when missing) using plain SQL.
     * Returns the id of the created role; pass it to {@link #removeRole(long)} to clean up.
     */
    public long grantPermission(long userId, String resource, String action) {
        long roleId = queryLong("INSERT INTO roles (role_name) VALUES (?) RETURNING id", "ROLE_" + UUID.randomUUID());
        execute("INSERT INTO permissions (resource, action) VALUES (?, ?) ON CONFLICT DO NOTHING", resource, action);
        long permissionId = queryLong("SELECT id FROM permissions WHERE resource = ? AND action = ?", resource, action);
        execute("INSERT INTO role_permission (role_id, permission_id) VALUES (?, ?)", roleId, permissionId);
        execute("INSERT INTO role_user (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return roleId;
    }

    /** Deletes the role; the join rows go with it (ON DELETE CASCADE). */
    public void removeRole(long roleId) {
        execute("DELETE FROM roles WHERE id = ?", roleId);
    }

    /** Deletes the permission; the role_permission rows go with it (ON DELETE CASCADE). */
    public void removePermission(String resource, String action) {
        execute("DELETE FROM permissions WHERE resource = ? AND action = ?", resource, action);
    }

    /** Deletes the user; its tokens and role links go with it (ON DELETE CASCADE). */
    public void removeUser(long userId) {
        execute("DELETE FROM users WHERE id = ?", userId);
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
