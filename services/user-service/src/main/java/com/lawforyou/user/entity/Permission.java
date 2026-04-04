package com.lawforyou.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Granular permission that can be assigned to one or more {@link Role}s.
 *
 * <p>Permissions follow the pattern: {@code RESOURCE_ACTION} (e.g. {@code USER_READ},
 * {@code CASE_CREATE}). They are seeded by Flyway and are read-only at runtime.</p>
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "roles")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Unique permission key, e.g. {@code USER_READ}. */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "display_name")
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Domain object this permission applies to.
     * Values: USER, CASE, DOCUMENT, ROLE, TENANT.
     */
    @Column(nullable = false, length = 50)
    private String resource;

    /**
     * Operation this permission grants.
     * Values: READ, CREATE, UPDATE, DELETE, MANAGE.
     */
    @Column(nullable = false, length = 50)
    private String action;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

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
}

