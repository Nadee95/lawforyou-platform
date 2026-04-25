package com.lawforyou.document.integration;

import com.lawforyou.document.model.DocumentCategory;
import com.lawforyou.document.model.DocumentMetadata;
import com.lawforyou.document.repository.DocumentMetadataRepository;
import com.lawforyou.document.repository.OutboxEventRepository;
import com.nadeex.spring.security.token.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for the Document Service.
 *
 * <p>JWT tokens are generated directly via {@link JwtTokenProvider} — same pattern as case-service IT.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class DocumentControllerIT {

    // ── Containers ────────────────────────────────────────────────────────────

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> minio = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2024-03-21T23-13-43Z"))
            .withCommand("server /data")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",        mongodb::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.minio.endpoint",
                () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    @Autowired MockMvc                    mockMvc;
    @Autowired DocumentMetadataRepository documentMetadataRepository;
    @Autowired OutboxEventRepository      outboxEventRepository;
    @Autowired JwtTokenProvider           jwtTokenProvider;

    private static final UUID   TENANT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TENANT_STR = TENANT_ID.toString();

    @AfterEach
    void cleanUp() {
        documentMetadataRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String jwtFor(UUID userId, List<String> roles) {
        return jwtTokenProvider.generateToken(userId, TENANT_ID, "testuser", roles, List.of());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void contextLoads() {
        // Verifies MongoDB wiring, MinIO config, and Spring context all succeed.
    }

    @Test
    void upload_validRequest_returns201AndPersistsMetadata() throws Exception {
        UUID caseId = UUID.randomUUID();
        String jwt  = jwtFor(UUID.randomUUID(), List.of("LAWYER"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "PDF content".getBytes());

        String metadataJson = """
                { "caseId": "%s", "category": "CONTRACT", "description": "Main contract" }
                """.formatted(caseId);

        MockMultipartFile metadataPart = new MockMultipartFile(
                "metadata", "", "application/json", metadataJson.getBytes());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .file(metadataPart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .header("Authorization", "Bearer " + jwt)
                        .header("X-Tenant-ID", TENANT_STR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalFilename").value("contract.pdf"))
                .andExpect(jsonPath("$.data.category").value("CONTRACT"));

        // Verify persisted in MongoDB
        assertThat(documentMetadataRepository.findAll()).hasSize(1);
        DocumentMetadata saved = documentMetadataRepository.findAll().get(0);
        assertThat(saved.getCaseId()).isEqualTo(caseId);
        assertThat(saved.getCategory()).isEqualTo(DocumentCategory.CONTRACT);
        assertThat(saved.getVersions()).hasSize(1);

        // Verify outbox event written
        assertThat(outboxEventRepository.findAll()).hasSize(1);
        assertThat(outboxEventRepository.findAll().get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void upload_withoutToken_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());
        MockMultipartFile metadataPart = new MockMultipartFile(
                "metadata", "", "application/json",
                ("{\"caseId\":\"" + UUID.randomUUID() + "\",\"category\":\"OTHER\"}").getBytes());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .file(metadataPart)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Uploads a document and returns its ID from the response body. */
    private String uploadAndGetId(UUID caseId, String jwt) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "brief.pdf", "application/pdf", "PDF content".getBytes());
        String metadataJson = """
                { "caseId": "%s", "category": "PLEADING", "description": "A brief" }
                """.formatted(caseId);
        MockMultipartFile metadataPart = new MockMultipartFile(
                "metadata", "", "application/json", metadataJson.getBytes());

        String body = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .file(metadataPart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .header("Authorization", "Bearer " + jwt)
                        .header("X-Tenant-ID", TENANT_STR))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).path("data").path("id").asText();
    }

    // ── GET /api/v1/documents/{id} ────────────────────────────────────────────

    @Test
    void getById_existingDocument_returns200WithMetadata() throws Exception {
        UUID caseId = UUID.randomUUID();
        String jwt   = jwtFor(UUID.randomUUID(), List.of("LAWYER"));
        String docId = uploadAndGetId(caseId, jwt);

        mockMvc.perform(get("/api/v1/documents/{id}", docId)
                        .header("Authorization", "Bearer " + jwt)
                        .header("X-Tenant-ID", TENANT_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(docId))
                .andExpect(jsonPath("$.data.originalFilename").value("brief.pdf"))
                .andExpect(jsonPath("$.data.category").value("PLEADING"));
    }

    @Test
    void getById_unknownDocument_returns404() throws Exception {
        String jwt = jwtFor(UUID.randomUUID(), List.of("LAWYER"));

        mockMvc.perform(get("/api/v1/documents/{id}", "non-existent-id")
                        .header("Authorization", "Bearer " + jwt)
                        .header("X-Tenant-ID", TENANT_STR))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/v1/documents/{id} ─────────────────────────────────────────

    @Test
    void delete_existingDocument_returns204AndDocumentIsGone() throws Exception {
        UUID caseId = UUID.randomUUID();
        String jwt   = jwtFor(UUID.randomUUID(), List.of("LAWYER"));
        String docId = uploadAndGetId(caseId, jwt);

        // Delete it
        mockMvc.perform(delete("/api/v1/documents/{id}", docId)
                        .header("Authorization", "Bearer " + jwt)
                        .header("X-Tenant-ID", TENANT_STR))
                .andExpect(status().isNoContent());

        // Verify soft-deleted — subsequent GET returns 404
        mockMvc.perform(get("/api/v1/documents/{id}", docId)
                        .header("Authorization", "Bearer " + jwt)
                        .header("X-Tenant-ID", TENANT_STR))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/documents/case/{caseId} ──────────────────────────────────

    @Test
    void listByCase_returns200WithUploadedDocuments() throws Exception {
        UUID caseId = UUID.randomUUID();
        String jwt  = jwtFor(UUID.randomUUID(), List.of("LAWYER"));

        // Upload two documents for the same case
        uploadAndGetId(caseId, jwt);
        uploadAndGetId(caseId, jwt);

        mockMvc.perform(get("/api/v1/documents/case/{caseId}", caseId)
                        .header("Authorization", "Bearer " + jwt)
                        .header("X-Tenant-ID", TENANT_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }
}

