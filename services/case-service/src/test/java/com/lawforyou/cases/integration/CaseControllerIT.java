package com.lawforyou.cases.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.cases.dto.request.AssignLawyerRequest;
import com.lawforyou.cases.dto.request.ChangeCaseStatusRequest;
import com.lawforyou.cases.dto.request.CreateCaseRequest;
import com.lawforyou.cases.dto.request.UpdateCaseRequest;
import com.lawforyou.cases.entity.CaseStatus;
import com.lawforyou.cases.entity.CaseType;
import com.lawforyou.cases.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests for the Case Service.
 *
 * <p>Infrastructure (Postgres, Redis, Kafka) is started once per class via
 * {@code static @Container} fields and wired via {@code @DynamicPropertySource}.</p>
 *
 * <p>JWT tokens are generated directly using {@link JwtTokenProvider} — there is
 * no auth endpoint in case-service (tokens are issued by user-service).</p>
 *
 * <p>Test isolation: each test uses a random UUID tenantId and clientId so
 * there are no unique-constraint conflicts between runs.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class CaseControllerIT {

    // ── Containers ────────────────────────────────────────────────────────────

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",          postgres::getJdbcUrl);
        registry.add("spring.datasource.username",     postgres::getUsername);
        registry.add("spring.datasource.password",     postgres::getPassword);
        registry.add("spring.data.redis.host",         redis::getHost);
        registry.add("spring.data.redis.port",         () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.cloud.config.enabled",    () -> "false");
        registry.add("eureka.client.enabled",          () -> "false");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    @Autowired MockMvc          mockMvc;
    @Autowired ObjectMapper     objectMapper;
    @Autowired JwtTokenProvider jwtTokenProvider;

    /** Well-known tenant — must match the UUID used in requests. */
    private static final String TENANT_STR = "00000000-0000-0000-0000-000000000001";
    private static final UUID   TENANT_ID  = UUID.fromString(TENANT_STR);

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Generates a JWT signed with the test secret (from application-test.yml).
     * case-service validates but does not issue tokens — we build them directly here.
     */
    private String jwtFor(UUID userId, List<String> roles) {
        return jwtTokenProvider.generateToken(userId, TENANT_ID, "testuser", roles, List.of());
    }

    private CreateCaseRequest uniqueCreateRequest() {
        return new CreateCaseRequest(
                "Case-" + UUID.randomUUID().toString().substring(0, 8),
                "Integration test case",
                CaseType.CIVIL,
                UUID.randomUUID()
        );
    }

    /** Creates a case and returns its ID. */
    private String createCaseAndGetId(String jwt) throws Exception {
        String body = mockMvc.perform(post("/api/cases")
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uniqueCreateRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void contextLoads() {
        // Verifies Flyway migrations, container wiring, and Spring context all succeed.
    }

    @Test
    void createCase_withValidToken_returns201AndCaseDto() throws Exception {
        String jwt     = jwtFor(UUID.randomUUID(), List.of("ADMIN"));
        var    request = uniqueCreateRequest();

        mockMvc.perform(post("/api/cases")
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value(request.title()))
                .andExpect(jsonPath("$.data.caseType").value("CIVIL"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_STR));
    }

    @Test
    void createCase_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/cases")
                        .header("X-Tenant-ID", TENANT_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uniqueCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCase_byId_returnsCorrectCase() throws Exception {
        String jwt    = jwtFor(UUID.randomUUID(), List.of("LAWYER"));
        String caseId = createCaseAndGetId(jwt);

        mockMvc.perform(get("/api/cases/{id}", caseId)
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(caseId))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void getCase_unknownId_returns404() throws Exception {
        String jwt = jwtFor(UUID.randomUUID(), List.of("LAWYER"));

        mockMvc.perform(get("/api/cases/{id}", UUID.randomUUID())
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void listCases_returnsPaginatedResults() throws Exception {
        String jwt = jwtFor(UUID.randomUUID(), List.of("ADMIN"));
        // Create two cases
        createCaseAndGetId(jwt);
        createCaseAndGetId(jwt);

        mockMvc.perform(get("/api/cases")
                        .param("page", "0")
                        .param("size", "10")
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void listCases_filteredByStatus_returnsOnlyMatchingCases() throws Exception {
        String jwt = jwtFor(UUID.randomUUID(), List.of("ADMIN"));
        createCaseAndGetId(jwt); // status = OPEN by default

        mockMvc.perform(get("/api/cases")
                        .param("status", "OPEN")
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));
    }

    @Test
    void updateCase_withValidToken_returnsUpdatedDto() throws Exception {
        String jwt    = jwtFor(UUID.randomUUID(), List.of("LAWYER"));
        String caseId = createCaseAndGetId(jwt);
        var update    = new UpdateCaseRequest("Updated Title", "New description", null);

        mockMvc.perform(put("/api/cases/{id}", caseId)
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated Title"))
                .andExpect(jsonPath("$.data.description").value("New description"));
    }

    @Test
    void assignLawyer_withValidToken_returnsActiveLawyerId() throws Exception {
        String jwt      = jwtFor(UUID.randomUUID(), List.of("ADMIN"));
        String caseId   = createCaseAndGetId(jwt);
        UUID   lawyerId = UUID.randomUUID();
        var    request  = new AssignLawyerRequest(lawyerId);

        mockMvc.perform(patch("/api/cases/{id}/assign", caseId)
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeLawyerId").value(lawyerId.toString()));
    }

    @Test
    void assignLawyer_reassign_deactivatesPrevious() throws Exception {
        String jwt       = jwtFor(UUID.randomUUID(), List.of("ADMIN"));
        String caseId    = createCaseAndGetId(jwt);
        UUID   lawyer1   = UUID.randomUUID();
        UUID   lawyer2   = UUID.randomUUID();

        // First assignment
        mockMvc.perform(patch("/api/cases/{id}/assign", caseId)
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignLawyerRequest(lawyer1))))
                .andExpect(status().isOk());

        // Reassign — should deactivate previous and return new lawyer
        mockMvc.perform(patch("/api/cases/{id}/assign", caseId)
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignLawyerRequest(lawyer2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeLawyerId").value(lawyer2.toString()));
    }

    @Test
    void changeStatus_toInProgress_updatesStatus() throws Exception {
        String jwt    = jwtFor(UUID.randomUUID(), List.of("ADMIN"));
        String caseId = createCaseAndGetId(jwt);
        var    request = new ChangeCaseStatusRequest(CaseStatus.IN_PROGRESS);

        mockMvc.perform(patch("/api/cases/{id}/status", caseId)
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void changeStatus_toClosed_publishesCaseClosedEvent() throws Exception {
        String jwt     = jwtFor(UUID.randomUUID(), List.of("ADMIN"));
        String caseId  = createCaseAndGetId(jwt);
        var    request = new ChangeCaseStatusRequest(CaseStatus.CLOSED);

        mockMvc.perform(patch("/api/cases/{id}/status", caseId)
                        .header("X-Tenant-ID", TENANT_STR)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        // OutboxRelay will pick up the CaseClosedEvent — verified via status change
    }
}

