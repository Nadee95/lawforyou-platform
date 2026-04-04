package com.lawforyou.user.repository;

import com.lawforyou.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /** Returns all system/global roles (tenant_id IS NULL). */
    List<Role> findByTenantIdIsNull();

    /** Returns all active system/global roles. */
    List<Role> findByTenantIdIsNullAndActiveTrue();

    /** Returns all roles visible to a tenant: system roles + that tenant's own roles. */
    @Query("SELECT r FROM Role r WHERE r.tenantId IS NULL OR r.tenantId = :tenantId")
    List<Role> findAllVisibleToTenant(@Param("tenantId") UUID tenantId);

    /** Finds a system role by name (tenant_id IS NULL). */
    Optional<Role> findByNameAndTenantIdIsNull(String name);

    /** Finds a tenant-scoped role by name. */
    Optional<Role> findByNameAndTenantId(String name, UUID tenantId);

    boolean existsByNameAndTenantIdIsNull(String name);

    boolean existsByNameAndTenantId(String name, UUID tenantId);
}

