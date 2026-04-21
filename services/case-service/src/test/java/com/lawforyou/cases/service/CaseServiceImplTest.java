package com.lawforyou.cases.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.cases.dto.request.AssignLawyerRequest;
import com.lawforyou.cases.dto.request.ChangeCaseStatusRequest;
import com.lawforyou.cases.dto.request.CreateCaseRequest;
import com.lawforyou.cases.dto.request.UpdateCaseRequest;
import com.lawforyou.cases.entity.Case;
import com.lawforyou.cases.entity.CaseAssignment;
import com.lawforyou.cases.entity.CaseStatus;
import com.lawforyou.cases.entity.CaseType;
import com.lawforyou.cases.entity.OutboxEvent;
import com.lawforyou.cases.event.CaseAssignedEvent;
import com.lawforyou.cases.event.CaseClosedEvent;
import com.lawforyou.cases.event.CaseCreatedEvent;
import com.lawforyou.cases.repository.CaseAssignmentRepository;
import com.lawforyou.cases.repository.CaseRepository;
import com.lawforyou.cases.repository.OutboxEventRepository;
import com.lawforyou.cases.service.impl.CaseServiceImpl;
import com.nadeex.spring.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CaseServiceImpl}.
 * Uses Mockito only — no Spring context, no database.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Happy-path business logic for each operation</li>
 *   <li>Outbox event is written with correct {@code eventType} and {@code status=PENDING}</li>
 *   <li>Not-found and tenant-isolation paths throw {@link ResourceNotFoundException}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CaseServiceImplTest {

    @Mock CaseRepository           caseRepository;
    @Mock CaseAssignmentRepository caseAssignmentRepository;
    @Mock OutboxEventRepository    outboxEventRepository;
    @Mock ObjectMapper             objectMapper;
    @Mock EntityManager            entityManager;

    @InjectMocks CaseServiceImpl caseService;

    @BeforeEach
    void injectEntityManager() {
        // @PersistenceContext fields are NOT in the Lombok constructor, so Mockito's
        // constructor injection skips them. Inject the mock manually after construction.
        ReflectionTestUtils.setField(caseService, "entityManager", entityManager);
    }

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID LAWYER_ID = UUID.randomUUID();
    private static final String CREATED_BY = UUID.randomUUID().toString();

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Case buildCase(UUID id) {
        return Case.builder()
                .id(id)
                .tenantId(TENANT_ID)
                .title("Smith vs. Jones")
                .description("Civil dispute")
                .caseType(CaseType.CIVIL)
                .status(CaseStatus.OPEN)
                .clientId(CLIENT_ID)
                .createdBy(CREATED_BY)
                .build();
    }

    // ── createCase ────────────────────────────────────────────────────────────

    @Test
    void createCase_withValidRequest_savesAndWritesOutboxEvent() throws JsonProcessingException {
        var request = new CreateCaseRequest("Smith vs. Jones", "Civil dispute", CaseType.CIVIL, CLIENT_ID);
        var savedCase = buildCase(UUID.randomUUID());

        when(caseRepository.saveAndFlush(any(Case.class))).thenReturn(savedCase);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        caseService.createCase(request, TENANT_ID, CREATED_BY);

        verify(entityManager).refresh(savedCase);
        verify(caseRepository).saveAndFlush(any(Case.class));

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent outbox = captor.getValue();
        assertThat(outbox.getEventType()).isEqualTo(CaseCreatedEvent.class.getName());
        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getAggregateId()).isEqualTo(savedCase.getId().toString());
        assertThat(outbox.getAggregateType()).isEqualTo("Case");
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_whenCaseExists_returnsDto() {
        UUID caseId = UUID.randomUUID();
        Case c = buildCase(caseId);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));
        when(caseAssignmentRepository.findByCaseIdAndActiveTrue(caseId)).thenReturn(Optional.empty());

        var dto = caseService.findById(caseId, TENANT_ID);

        assertThat(dto.id()).isEqualTo(caseId);
        assertThat(dto.title()).isEqualTo("Smith vs. Jones");
        assertThat(dto.activeLawyerId()).isNull();
    }

    @Test
    void findById_withWrongTenantId_throwsNotFound() {
        UUID caseId = UUID.randomUUID();
        Case c = buildCase(caseId); // tenantId = TENANT_ID

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));

        UUID differentTenant = UUID.randomUUID();
        assertThatThrownBy(() -> caseService.findById(caseId, differentTenant))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_whenCaseNotFound_throwsNotFound() {
        UUID caseId = UUID.randomUUID();
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caseService.findById(caseId, TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateCase ────────────────────────────────────────────────────────────

    @Test
    void updateCase_withValidRequest_mutatesFields() {
        UUID caseId = UUID.randomUUID();
        Case c = buildCase(caseId);
        var request = new UpdateCaseRequest("Updated Title", "Updated desc", CaseType.CORPORATE);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));
        when(caseAssignmentRepository.findByCaseIdAndActiveTrue(caseId)).thenReturn(Optional.empty());

        var dto = caseService.updateCase(caseId, TENANT_ID, request, CREATED_BY);

        // Dirty-check handles DB write — no outbox for plain updates
        assertThat(c.getTitle()).isEqualTo("Updated Title");
        assertThat(c.getDescription()).isEqualTo("Updated desc");
        assertThat(c.getCaseType()).isEqualTo(CaseType.CORPORATE);
        assertThat(dto.title()).isEqualTo("Updated Title");
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void updateCase_nullFields_doesNotOverwriteExistingValues() {
        UUID caseId = UUID.randomUUID();
        Case c = buildCase(caseId);
        var request = new UpdateCaseRequest(null, null, null); // nothing to change

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));
        when(caseAssignmentRepository.findByCaseIdAndActiveTrue(caseId)).thenReturn(Optional.empty());

        caseService.updateCase(caseId, TENANT_ID, request, CREATED_BY);

        assertThat(c.getTitle()).isEqualTo("Smith vs. Jones");   // unchanged
        assertThat(c.getCaseType()).isEqualTo(CaseType.CIVIL);   // unchanged
    }

    // ── assignLawyer ──────────────────────────────────────────────────────────

    @Test
    void assignLawyer_deactivatesPreviousAssignmentAndWritesOutboxEvent() throws JsonProcessingException {
        UUID caseId = UUID.randomUUID();
        Case c = buildCase(caseId);

        CaseAssignment existingAssignment = CaseAssignment.builder()
                .id(UUID.randomUUID())
                .caseId(caseId)
                .lawyerId(UUID.randomUUID())
                .active(true)
                .build();

        var request = new AssignLawyerRequest(LAWYER_ID);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));
        when(caseAssignmentRepository.findByCaseIdAndActiveTrue(caseId))
                .thenReturn(Optional.of(existingAssignment));
        when(caseAssignmentRepository.saveAndFlush(any(CaseAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = caseService.assignLawyer(caseId, TENANT_ID, request, CREATED_BY);

        // Previous assignment deactivated
        assertThat(existingAssignment.isActive()).isFalse();

        // New assignment saved
        verify(caseAssignmentRepository).saveAndFlush(any(CaseAssignment.class));

        // Outbox event written
        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(CaseAssignedEvent.class.getName());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");

        assertThat(dto.activeLawyerId()).isEqualTo(LAWYER_ID);
    }

    @Test
    void assignLawyer_whenNoPreviousAssignment_createsNew() throws JsonProcessingException {
        UUID caseId = UUID.randomUUID();
        Case c = buildCase(caseId);
        var request = new AssignLawyerRequest(LAWYER_ID);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));
        when(caseAssignmentRepository.findByCaseIdAndActiveTrue(caseId)).thenReturn(Optional.empty());
        when(caseAssignmentRepository.saveAndFlush(any(CaseAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        caseService.assignLawyer(caseId, TENANT_ID, request, CREATED_BY);

        verify(caseAssignmentRepository).saveAndFlush(any(CaseAssignment.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    // ── changeStatus ──────────────────────────────────────────────────────────

    @Test
    void changeStatus_toInProgress_updatesStatusNoOutboxEvent() {
        UUID caseId = UUID.randomUUID();
        Case c = buildCase(caseId);
        var request = new ChangeCaseStatusRequest(CaseStatus.IN_PROGRESS);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));
        when(caseAssignmentRepository.findByCaseIdAndActiveTrue(caseId)).thenReturn(Optional.empty());

        var dto = caseService.changeStatus(caseId, TENANT_ID, request);

        assertThat(c.getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
        assertThat(dto.status()).isEqualTo(CaseStatus.IN_PROGRESS);
        verifyNoInteractions(outboxEventRepository);  // no outbox for IN_PROGRESS
    }

    @Test
    void changeStatus_toClosed_writesOutboxEvent() throws JsonProcessingException {
        UUID caseId = UUID.randomUUID();
        Case c = buildCase(caseId);
        var request = new ChangeCaseStatusRequest(CaseStatus.CLOSED);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));
        when(caseAssignmentRepository.findByCaseIdAndActiveTrue(caseId)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        caseService.changeStatus(caseId, TENANT_ID, request);

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(CaseClosedEvent.class.getName());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void changeStatus_toArchived_writesOutboxEvent() throws JsonProcessingException {
        UUID caseId = UUID.randomUUID();
        Case c = buildCase(caseId);
        var request = new ChangeCaseStatusRequest(CaseStatus.ARCHIVED);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(c));
        when(caseAssignmentRepository.findByCaseIdAndActiveTrue(caseId)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        caseService.changeStatus(caseId, TENANT_ID, request);

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(CaseClosedEvent.class.getName());
    }
}

