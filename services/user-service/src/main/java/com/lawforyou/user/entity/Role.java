package com.lawforyou.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A role that can be assigned to users within a tenant.
 *
 * <h3>Two kinds of roles:</h3>
 * <ul>
 *   <li><b>System roles</b>: {@code tenant_id = NULL}, {@code system_role = TRUE}.
 *       Seeded by Flyway (ADMIN, LAWYER, CLIENT, STAFF). Cannot be deleted.</li>
 *   <li><b>Tenant roles</b>: {@code tenant_id = <id>}, {@code system_role = FALSE}.
 *       Created by tenant admins at runtime, fully customisable.</li>
 * </ul>
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"permissions"})
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * NULL for system/global roles; set to the owning tenant's id for
     * tenant-defined custom roles.
     */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** Role identifier, e.g. {@code ADMIN} or a tenant's custom {@code SENIOR_PARTNER}. */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_name")
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Inactive roles cannot be assigned to users. */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * System roles ({@code TRUE}) are seeded and cannot be deleted by tenant admins.
     * Tenant-defined roles always have {@code FALSE}.
     */
    @Builder.Default
    @Column(name = "system_role", nullable = false)
    private boolean systemRole = false;

    /** Permissions granted to this role. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns        = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

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

    /** Returns true if this role belongs to the system (not tenant-owned). */
    public boolean isGlobal() {
        return tenantId == null;
    }

    /** Returns true if this role belongs to a specific tenant. */
    public boolean isTenantScoped() {
        return tenantId != null;
    }

    /** Returns true if the role has the given permission by name. */
    public boolean hasPermission(String permissionName) {
        return permissions.stream()
                .anyMatch(p -> p.getName().equals(permissionName) && p.isActive());
    }
}

