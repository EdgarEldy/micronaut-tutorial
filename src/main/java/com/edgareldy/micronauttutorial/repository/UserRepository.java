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
     * Permission codes "RESOURCE:ACTION" granted to the user through its roles. Native SQL because the
     * Role and Permission entities only arrive with feature/rbac: the query works on the tables directly.
     * Empty until permissions are seeded and assigned.
     */
    @Query(value = "SELECT DISTINCT p.resource || ':' || p.action FROM role_user ru "
            + "JOIN role_permission rp ON rp.role_id = ru.role_id "
            + "JOIN permissions p ON p.id = rp.permission_id "
            + "WHERE ru.user_id = :userId ORDER BY 1", nativeQuery = true)
    List<String> findPermissionCodesByUserId(Long userId);
}
