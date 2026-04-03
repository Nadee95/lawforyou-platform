package com.lawforyou.user.repository;

import com.lawforyou.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link User} entities.
 *
 * <p>All methods that accept {@code tenantId} use it explicitly in queries.
 * Hibernate's {@code @TenantId} filter provides an additional safety layer,
 * but explicit parameters make intent clear and queries testable in isolation.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    Optional<User> findByUsernameAndTenantId(String username, UUID tenantId);

    boolean existsByEmailAndTenantId(String email, UUID tenantId);

    boolean existsByUsernameAndTenantId(String username, UUID tenantId);

    Page<User> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<User> findAllByTenantIdAndActiveTrue(UUID tenantId, Pageable pageable);

    @Query("""
           SELECT u FROM User u
           WHERE u.tenantId = :tenantId
             AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :query, '%')))
           """)
    Page<User> searchByTenantId(@Param("tenantId") UUID tenantId,
                                @Param("query")    String query,
                                Pageable pageable);
}

