package com.lawforyou.cases.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks lawyer assignments for a case.
 * Only one assignment can be {@code active = true} per case at a time
 * (enforced by partial unique index {@code uq_active_assignment}).
 */
@Entity
@Table(name = "case_assignments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class CaseAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @Column(updatable = false, nullable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "lawyer_id", nullable = false)
    private UUID lawyerId;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}

