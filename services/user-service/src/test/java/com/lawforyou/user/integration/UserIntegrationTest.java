package com.lawforyou.user.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.user.dto.request.LoginRequest;
import com.lawforyou.user.dto.request.RegisterUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// ── TestContainers imports ──────────────────────────────────
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
// ─────────────────────────────────────────────────────────────

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests using TestContainers.
 *
 * <p><b>Tenant setup:</b> no {@code @BeforeEach} required — the well-known development
 * tenant ({@value #DEV_TENANT_STR}) is seeded by {@code V2__Seed_default_tenant.sql}
 * which Flyway runs automatically on every fresh database.</p>
 *
 * <p><b>Test isolation:</b> each test generates a UUID-suffixed username/email so
 * there are no unique-constraint conflicts between runs without needing a cleanup step.</p>
 *
 * <p><b>Infrastructure:</b> Postgres, Redis and Kafka are started once per test class
 * via {@code static @Container} fields and wired through {@code @DynamicPropertySource}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class UserIntegrationTest {

    // ── Containers (started once per class) ──────────────────────────────────

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

    // ── Fixtures ─────────────────────────────────────────────────────────────

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    /**
     * Well-known dev tenant seeded by {@code V2__Seed_default_tenant.sql}.
     * Pass this as the {@code X-Tenant-ID} header in every request.
     */
    private static final String DEV_TENANT_STR = "00000000-0000-0000-0000-000000000001";
    // private static final UUID   DEV_TENANT     = UUID.fromString(DEV_TENANT_STR);

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Builds a request with a unique username/email to avoid constraint conflicts. */
    private RegisterUserRequest uniqueRegisterRequest() {
        String s = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return new RegisterUserRequest("user_" + s, s + "@test.com",
                "Password123!", "Test", "User", null);
    }

    /** Registers a user and returns the created userId string. */
    private String registerAndGetId(RegisterUserRequest req) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .header("X-Tenant-ID", DEV_TENANT_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    /** Logs in and returns the JWT access token. */
    private String loginAndGetToken(String usernameOrEmail, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .header("X-Tenant-ID", DEV_TENANT_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(usernameOrEmail, password))))

                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void contextLoads() {
        // Confirms the full Spring context, Flyway migrations, and all
        // container wiring complete without error.
    }

    /**
     * Happy path: register → login → fetch own profile.
     * Exercises the entire auth + RBAC chain end-to-end.
     */
    @Test
    void register_thenLogin_thenGetOwnProfile_fullFlow() throws Exception {

        // ── 1. Register ──────────────────────────────────────────────────────
        var req = uniqueRegisterRequest();

        String registerBody = mockMvc.perform(post("/api/auth/register")
                        .header("X-Tenant-ID", DEV_TENANT_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(req.username()))
                .andExpect(jsonPath("$.data.email").value(req.email()))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.tenantId").value(DEV_TENANT_STR))
                .andReturn().getResponse().getContentAsString();

        String userId = objectMapper.readTree(registerBody)
                .path("data").path("id").asText();

        // ── 2. Login by username ─────────────────────────────────────────────
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .header("X-Tenant-ID", DEV_TENANT_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(req.username(), req.password()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value(not(emptyString())))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginBody)
                .path("data").path("accessToken").asText();

        // ── 3. Get own profile via JWT ───────────────────────────────────────
        // Security rule: #userId.toString() == authentication.name  (owner access)
        mockMvc.perform(get("/api/users/{id}", userId)
                        .header("X-Tenant-ID", DEV_TENANT_STR)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.username").value(req.username()))
                .andExpect(jsonPath("$.data.email").value(req.email()));
    }

    /** Login by email (not username) should also work. */
    @Test
    void login_byEmail_returnsToken() throws Exception {
        var req = uniqueRegisterRequest();
        registerAndGetId(req);

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Tenant-ID", DEV_TENANT_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(req.email(), req.password()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value(not(emptyString())));
    }

    /** Registering twice with the same email must return 409. */
    @Test
    void register_duplicateEmail_returns409() throws Exception {
        var req = uniqueRegisterRequest();
        registerAndGetId(req);  // first registration succeeds

        // Second registration — same email, different username
        var duplicate = new RegisterUserRequest(
                "other_" + UUID.randomUUID().toString().substring(0, 8),
                req.email(),          // ← same email → conflict
                req.password(), null, null, null);

        mockMvc.perform(post("/api/auth/register")
                        .header("X-Tenant-ID", DEV_TENANT_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict());
    }

    /** Login with a wrong password must return 401. */
    @Test
    void login_wrongPassword_returns401() throws Exception {
        var req = uniqueRegisterRequest();
        registerAndGetId(req);

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Tenant-ID", DEV_TENANT_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(req.username(), "WrongPassword999!"))))
                .andExpect(status().isUnauthorized());
    }

    /** Login for a non-existent user must return 401 (not 404, to prevent user enumeration). */
    @Test
    void login_unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Tenant-ID", DEV_TENANT_STR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("ghost@nobody.com", "Password123!"))))
                .andExpect(status().isUnauthorized());
    }

    /** Accessing a protected endpoint without a JWT must return 401. */
    @Test
    void getUser_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/{id}", UUID.randomUUID())
                        .header("X-Tenant-ID", DEV_TENANT_STR))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A CLIENT user (no USER_READ authority) trying to access another user's
     * profile must receive 403 Forbidden.
     */
    @Test
    void getUser_withAnotherUsersToken_returns403() throws Exception {
        // Two separate users
        var alice = uniqueRegisterRequest();
        var bob   = uniqueRegisterRequest();
        String aliceId = registerAndGetId(alice);
        registerAndGetId(bob);

        // Bob logs in and uses his token to access Alice's profile
        String bobToken = loginAndGetToken(bob.username(), bob.password());

        mockMvc.perform(get("/api/users/{id}", aliceId)
                        .header("X-Tenant-ID", DEV_TENANT_STR)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
    }
}
