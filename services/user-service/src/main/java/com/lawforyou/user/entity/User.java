package com.lawforyou.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A platform user belonging to a specific tenant.
 *
 * <p>{@code tenantId} is annotated with Hibernate's {@link TenantId} so Hibernate 6
 * automatically filters all queries by the current tenant context resolved by
 * {@code TenantIdentifierResolver}. Never set {@code tenantId} manually — Hibernate
 * injects it from the session context.</p>
 *
 * <p>Roles may be system-wide (e.g. ADMIN) or tenant-defined custom roles.
 * Both are resolved via the {@code user_roles} join table.</p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "roles")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * Hibernate multi-tenancy discriminator.
     * Automatically populated from the current tenant session context.
     * Never set this field directly in application code.
     */
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 30)
    private String phone;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Keycloak user UUID — populated on registration when Keycloak Admin API integration
     * is enabled ({@code app.keycloak.enabled=true}). Null for users created before Phase 4
     * or in test environments where Keycloak is disabled.
     */
    @Column(name = "keycloak_id", unique = true)
    private UUID keycloakId;

    /**
     * Roles assigned to this user. May include system roles and/or tenant-defined roles.
     * Loaded eagerly — roles are small sets used on every authenticated request.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns        = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ── Convenience helpers ─────────────────────────────────────────────────

    /** Returns the user's full name, falling back to username if names not set. */
    public String getFullName() {
        if (firstName == null && lastName == null) return username;
        if (firstName == null) return lastName;
        if (lastName == null)  return firstName;
        return firstName + " " + lastName;
    }

    /** Returns true if the user has a role matching the given {@link SystemRole}. */
    public boolean hasSystemRole(SystemRole systemRole) {
        return roles.stream()
                .anyMatch(r -> r.isGlobal() && r.getName().equals(systemRole.roleName()));
    }

    /** Returns true if the user has any role that grants the given permission. */
    public boolean hasPermission(String permissionName) {
        return roles.stream()
                .filter(Role::isActive)
                .anyMatch(r -> r.hasPermission(permissionName));
    }

    /** Adds a role to this user. */
    public void addRole(Role role) {
        this.roles.add(role);
    }

    /** Removes a role from this user. */
    public void removeRole(Role role) {
        this.roles.remove(role);
    }
}

