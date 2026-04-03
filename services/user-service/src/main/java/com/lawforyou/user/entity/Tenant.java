package com.lawforyou.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant represents a law firm or organisation using the platform.
 *
 * <p>Every {@link User} and tenant-scoped {@link Role} belongs to exactly one tenant.
 * The {@code slug} is used in custom domain routing (e.g. {@code acme-law.lawforyou.com}).</p>
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** URL-safe identifier used in subdomain routing, e.g. {@code acme-law}. */
    @Column(nullable = false, unique = true, length = 63)
    private String slug;

    @Column(nullable = false)
    private String name;

    /** Optional custom domain, e.g. {@code portal.acmelaw.com}. */
    @Column
    private String domain;

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

