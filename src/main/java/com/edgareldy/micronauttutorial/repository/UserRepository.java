package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.User;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for users. Micronaut Data generates the implementation at compile time.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @Repository on an interface: the Micronaut Data annotation processor generates the implementation
// (JPQL/SQL built at compile time, no proxy or reflection at runtime), so there is no *RepositoryImpl.
// Derived methods (findByEmail, existsByEmail) are parsed from their names; @Query gives an explicit query.
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Permission codes "RESOURCE:ACTION" granted to the user through its roles, straight from the join
     * tables (no entity loading). Empty when the user holds no role.
     */
    @Query(value = "SELECT DISTINCT p.resource || ':' || p.action FROM role_user ru "
            + "JOIN role_permission rp ON rp.role_id = ru.role_id "
            + "JOIN permissions p ON p.id = rp.permission_id "
            + "WHERE ru.user_id = :userId ORDER BY 1", nativeQuery = true)
    List<String> findPermissionCodesByUserId(Long userId);

    /** How many users currently hold the role (deleteRole is refused while this is above zero). */
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.id = :roleId")
    long countUsersByRoleId(Long roleId);

    /**
     * Users able to manage roles: enabled, not locked, and holding ROLE:WRITE through any of their roles.
     * DISTINCT so a user holding it through several roles counts once. Basis of the last-admin rule.
     */
    @Query("SELECT COUNT(DISTINCT u.id) FROM User u JOIN u.roles r JOIN r.permissions p "
            + "WHERE u.enabled = TRUE AND u.accountLocked = FALSE AND p.resource = 'ROLE' AND p.action = 'WRITE'")
    long countRoleWriteHolders();
}
