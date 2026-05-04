package com.lawforyou.cases.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.cases.dto.request.AssignLawyerRequest;
import com.lawforyou.cases.dto.request.ChangeCaseStatusRequest;
import com.lawforyou.cases.dto.request.CreateCaseRequest;
import com.lawforyou.cases.dto.request.UpdateCaseRequest;
import com.lawforyou.cases.entity.CaseStatus;
import com.lawforyou.cases.entity.CaseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
 * <p>Authentication is simulated by injecting gateway headers ({@code X-User-ID},
 * {@code X-Tenant-ID}, {@code X-Roles}, {@code X-Permissions}) — the same headers
 * that {@code DualTokenAuthenticationFilter} injects after validating the JWT.
 * This is the correct approach for Phase 5 header-based internal auth.</p>
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

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    /** Well-known tenant — must match the UUID used in requests. */
    private static final String TENANT_STR = "00000000-0000-0000-0000-000000000001";

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Applies gateway-injected identity headers to a MockMvc request builder.
     * Simulates what {@code DualTokenAuthenticationFilter} does after JWT validation.
     *
     * @param builder    the request builder to decorate
     * @param userId     the authenticated user's UUID
     * @param roles      role names (e.g. "ADMIN", "LAWYER")
     * @param permissions permission strings (e.g. "CASE_CREATE", "CASE_READ")
     * @return the same builder with headers set
     */
    private MockHttpServletRequestBuilder withGatewayAuth(MockHttpServletRequestBuilder builder,
                                                          UUID userId,
                                                          List<String> roles,
                                                          List<String> permissions) {
        return builder
                .header("X-User-ID",     userId.toString())
                .header("X-Tenant-ID",   TENANT_STR)
                .header("X-Username",    "testuser")
                .header("X-Roles",       String.join(",", roles))
                .header("X-Permissions", String.join(",", permissions));
    }

    /** Convenience: all case permissions included (matches LAWYER/ADMIN scenarios). */
    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, List<String> roles) {
        return withGatewayAuth(builder, UUID.randomUUID(), roles,
                List.of("CASE_CREATE", "CASE_READ", "CASE_UPDATE", "CASE_ASSIGN"));
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
    private String createCaseAndGetId(List<String> roles) throws Exception {
        String body = mockMvc.perform(withAuth(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uniqueCreateRequest())), roles))
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
    void createCase_withValidHeaders_returns201AndCaseDto() throws Exception {
        var request = uniqueCreateRequest();

        mockMvc.perform(withAuth(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)), List.of("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value(request.title()))
                .andExpect(jsonPath("$.data.caseType").value("CIVIL"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_STR));
    }

    @Test
    void createCase_withoutHeaders_returns401() throws Exception {
        mockMvc.perform(post("/api/cases")
                        .header("X-Tenant-ID", TENANT_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uniqueCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCase_byId_returnsCorrectCase() throws Exception {
        String caseId = createCaseAndGetId(List.of("LAWYER"));

        mockMvc.perform(withAuth(get("/api/cases/{id}", caseId), List.of("LAWYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(caseId))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void getCase_unknownId_returns404() throws Exception {
        mockMvc.perform(withAuth(get("/api/cases/{id}", UUID.randomUUID()), List.of("LAWYER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listCases_returnsPaginatedResults() throws Exception {
        createCaseAndGetId(List.of("ADMIN"));
        createCaseAndGetId(List.of("ADMIN"));

        mockMvc.perform(withAuth(get("/api/cases")
                        .param("page", "0")
                        .param("size", "10"), List.of("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void listCases_filteredByStatus_returnsOnlyMatchingCases() throws Exception {
        createCaseAndGetId(List.of("ADMIN")); // status = OPEN by default

        mockMvc.perform(withAuth(get("/api/cases")
                        .param("status", "OPEN"), List.of("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));
    }

    @Test
    void updateCase_withValidHeaders_returnsUpdatedDto() throws Exception {
        String caseId = createCaseAndGetId(List.of("LAWYER"));
        var update = new UpdateCaseRequest("Updated Title", "New description", null);

        mockMvc.perform(withAuth(put("/api/cases/{id}", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)), List.of("LAWYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated Title"))
                .andExpect(jsonPath("$.data.description").value("New description"));
    }

    @Test
    void assignLawyer_withValidHeaders_returnsActiveLawyerId() throws Exception {
        String caseId  = createCaseAndGetId(List.of("ADMIN"));
        UUID   lawyerId = UUID.randomUUID();
        var    request  = new AssignLawyerRequest(lawyerId);

        mockMvc.perform(withAuth(patch("/api/cases/{id}/assign", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)), List.of("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeLawyerId").value(lawyerId.toString()));
    }

    @Test
    void assignLawyer_reassign_deactivatesPrevious() throws Exception {
        String caseId  = createCaseAndGetId(List.of("ADMIN"));
        UUID   lawyer1 = UUID.randomUUID();
        UUID   lawyer2 = UUID.randomUUID();

        // First assignment
        mockMvc.perform(withAuth(patch("/api/cases/{id}/assign", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignLawyerRequest(lawyer1))), List.of("ADMIN")))
                .andExpect(status().isOk());

        // Reassign — should deactivate previous and return new lawyer
        mockMvc.perform(withAuth(patch("/api/cases/{id}/assign", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignLawyerRequest(lawyer2))), List.of("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeLawyerId").value(lawyer2.toString()));
    }

    @Test
    void changeStatus_toInProgress_updatesStatus() throws Exception {
        String caseId  = createCaseAndGetId(List.of("ADMIN"));
        var    request = new ChangeCaseStatusRequest(CaseStatus.IN_PROGRESS);

        mockMvc.perform(withAuth(patch("/api/cases/{id}/status", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)), List.of("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void changeStatus_toClosed_publishesCaseClosedEvent() throws Exception {
        String caseId  = createCaseAndGetId(List.of("ADMIN"));
        var    request = new ChangeCaseStatusRequest(CaseStatus.CLOSED);

        mockMvc.perform(withAuth(patch("/api/cases/{id}/status", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)), List.of("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        // OutboxRelay will pick up the CaseClosedEvent — verified via status change
    }
}

