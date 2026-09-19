package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.User;
import com.edgareldy.micronauttutorial.support.TestDatabase;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.edgareldy.micronauttutorial.support.AuthTestSupport.uniqueEmail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository tests of UserRepository against the Test Resources PostgreSQL: derived queries and the native
 * permission-code query.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// The Micronaut Data repository is injected like any bean and talks to the PostgreSQL container started by
// Test Resources (no manual container). transactional = false makes each repository call commit on its
// own, so the rows are really written; the test therefore cleans up after itself with @AfterEach.
@MicronautTest(transactional = false)
class UserRepositoryTest {

    @Inject
    UserRepository users;

    @Inject
    ConnectionOperations<Connection> connections;

    private final List<Long> createdUsers = new ArrayList<>();
    private final List<Long> createdRoles = new ArrayList<>();
    private final List<String[]> createdPermissions = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        TestDatabase db = new TestDatabase(connections);
        createdRoles.forEach(db::removeRole);
        createdPermissions.forEach(p -> db.removePermission(p[0], p[1]));
        createdUsers.forEach(db::removeUser);
    }

    private User saveUser(String email) {
        User saved = users.save(new User("Ada", "Lovelace", email, "hash"));
        createdUsers.add(saved.getId());
        return saved;
    }

    @Test
    void findByEmailAndExistsByEmail() {
        String email = uniqueEmail();
        User saved = saveUser(email);

        assertEquals(saved.getId(), users.findByEmail(email).orElseThrow().getId());
        assertTrue(users.existsByEmail(email));
        assertFalse(users.findByEmail(uniqueEmail()).isPresent());
        assertFalse(users.existsByEmail(uniqueEmail()));
    }

    @Test
    void newUsersAreDisabledAndUnlocked() {
        User saved = users.findById(saveUser(uniqueEmail()).getId()).orElseThrow();

        assertFalse(saved.isEnabled());
        assertFalse(saved.isAccountLocked());
    }

    @Test
    void permissionCodesAreEmptyWithoutRolesThenDistinctAndSorted() {
        User user = saveUser(uniqueEmail());
        assertEquals(List.of(), users.findPermissionCodesByUserId(user.getId()));

        TestDatabase db = new TestDatabase(connections);
        String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String resource = "REPO" + tag;
        // The same permission through two roles must appear once; B is granted before A to prove the ordering.
        createdRoles.add(db.grantPermission(user.getId(), resource, "B"));
        createdRoles.add(db.grantPermission(user.getId(), resource, "B"));
        createdRoles.add(db.grantPermission(user.getId(), resource, "A"));
        createdPermissions.add(new String[]{resource, "A"});
        createdPermissions.add(new String[]{resource, "B"});

        assertEquals(List.of(resource + ":A", resource + ":B"), users.findPermissionCodesByUserId(user.getId()));
    }
}
