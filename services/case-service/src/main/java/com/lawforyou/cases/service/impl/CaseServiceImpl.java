package com.lawforyou.cases.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.cases.dto.request.*;
import com.lawforyou.cases.dto.response.CaseDto;
import com.lawforyou.cases.entity.*;
import com.lawforyou.cases.event.CaseAssignedEvent;
import com.lawforyou.cases.event.CaseClosedEvent;
import com.lawforyou.cases.event.CaseCreatedEvent;
import com.lawforyou.cases.repository.CaseAssignmentRepository;
import com.lawforyou.cases.repository.CaseRepository;
import com.lawforyou.cases.repository.OutboxEventRepository;
import com.lawforyou.cases.service.CaseService;
import com.nadeex.spring.common.response.PagedResponse;
import com.nadeex.spring.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseServiceImpl implements CaseService {

    private final CaseRepository           caseRepository;
    private final CaseAssignmentRepository caseAssignmentRepository;
    private final OutboxEventRepository    outboxEventRepository;
    private final ObjectMapper             objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CaseDto createCase(CreateCaseRequest request, UUID tenantId, String createdBy) {
        Case newCase = Case.builder()
                .title(request.title())
                .description(request.description())
                .caseType(request.caseType())
                .clientId(request.clientId())
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();

        Case saved = caseRepository.saveAndFlush(newCase);

        // Refresh to populate @TenantId and server-assigned timestamps
        entityManager.refresh(saved);

        CaseCreatedEvent event = new CaseCreatedEvent(
                saved.getId(),
                saved.getTenantId(),
                saved.getClientId(),
                saved.getTitle(),
                saved.getCaseType(),
                Instant.now()
        );
        saveOutboxEvent("Case", saved.getId().toString(), CaseCreatedEvent.class, event);

        log.info("Created case {} for tenant {}", saved.getId(), tenantId);
        return toDto(saved, null);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CaseDto> findAll(UUID tenantId, CaseStatus status, CaseType caseType, Pageable pageable) {
        Page<Case> page;

        if (status != null && caseType != null) {
            page = caseRepository.findAllByTenantIdAndStatusAndCaseType(tenantId, status, caseType, pageable);
        } else if (status != null) {
            page = caseRepository.findAllByTenantIdAndStatus(tenantId, status, pageable);
        } else if (caseType != null) {
            page = caseRepository.findAllByTenantIdAndCaseType(tenantId, caseType, pageable);
        } else {
            page = caseRepository.findAllByTenantId(tenantId, pageable);
        }

        return PagedResponse.of(
                page.getContent().stream()
                        .map(c -> toDto(c, getActiveLawyerId(c.getId())))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CaseDto findById(UUID caseId, UUID tenantId) {
        Case c = requireCase(caseId, tenantId);
        return toDto(c, getActiveLawyerId(caseId));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CaseDto updateCase(UUID caseId, UUID tenantId, UpdateCaseRequest request, String updatedBy) {
        Case c = requireCase(caseId, tenantId);

        if (request.title()       != null) c.setTitle(request.title());
        if (request.description() != null) c.setDescription(request.description());
        if (request.caseType()    != null) c.setCaseType(request.caseType());
        c.setUpdatedBy(updatedBy);

        // @Transactional dirty-check handles the save — no explicit save() needed
        return toDto(c, getActiveLawyerId(caseId));
    }

    // ── Assign ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CaseDto assignLawyer(UUID caseId, UUID tenantId, AssignLawyerRequest request, String assignedBy) {
        Case c = requireCase(caseId, tenantId);

        // Deactivate current assignment (if any)
        caseAssignmentRepository.findByCaseIdAndActiveTrue(caseId)
                .ifPresent(a -> a.setActive(false));

        CaseAssignment assignment = CaseAssignment.builder()
                .caseId(caseId)
                .lawyerId(request.lawyerId())
                .assignedBy(assignedBy)
                .build();
        caseAssignmentRepository.saveAndFlush(assignment);

        CaseAssignedEvent event = new CaseAssignedEvent(
                caseId,
                c.getTenantId(),
                request.lawyerId(),
                assignedBy != null ? UUID.fromString(assignedBy) : null,
                Instant.now()
        );
        saveOutboxEvent("Case", caseId.toString(), CaseAssignedEvent.class, event);

        log.info("Assigned lawyer {} to case {}", request.lawyerId(), caseId);
        return toDto(c, request.lawyerId());
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CaseDto changeStatus(UUID caseId, UUID tenantId, ChangeCaseStatusRequest request) {
        Case c = requireCase(caseId, tenantId);
        c.setStatus(request.status());

        if (request.status() == CaseStatus.CLOSED || request.status() == CaseStatus.ARCHIVED) {
            CaseClosedEvent event = new CaseClosedEvent(
                    caseId, c.getTenantId(), request.status(), Instant.now()
            );
            saveOutboxEvent("Case", caseId.toString(), CaseClosedEvent.class, event);
        }

        return toDto(c, getActiveLawyerId(caseId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Case requireCase(UUID caseId, UUID tenantId) {
        return caseRepository.findById(caseId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
    }

    private UUID getActiveLawyerId(UUID caseId) {
        return caseAssignmentRepository.findByCaseIdAndActiveTrue(caseId)
                .map(CaseAssignment::getLawyerId)
                .orElse(null);
    }

    private CaseDto toDto(Case c, UUID activeLawyerId) {
        return new CaseDto(
                c.getId(),
                c.getTenantId(),
                c.getTitle(),
                c.getDescription(),
                c.getCaseType(),
                c.getStatus(),
                c.getClientId(),
                activeLawyerId,
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getCreatedBy(),
                c.getUpdatedBy()
        );
    }

    private <T> void saveOutboxEvent(String aggregateType, String aggregateId, Class<T> eventClass, T event) {
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventClass.getName())
                .payload(toJson(event))
                .status("PENDING")
                .build());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize outbox event: " + obj.getClass().getSimpleName(), ex);
        }
    }
}

