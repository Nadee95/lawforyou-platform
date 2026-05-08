package com.lawforyou.user.keycloak;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.user.dto.request.LoginRequest;
import com.lawforyou.user.dto.request.RegisterUserRequest;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test that boots a real Keycloak container (Keycloak 26)
 * and verifies that the JWT issued after registration carries the correct claims.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Keycloak Integration — JWT claim verification")
class KeycloakIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @Container
    static KeycloakContainer keycloak =
            new KeycloakContainer("quay.io/keycloak/keycloak:26.0")
                    .withRealmImportFile("keycloak/realm-export.json");

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
        registry.add("app.keycloak.enabled",           () -> "true");
        registry.add("app.keycloak.server-url",        keycloak::getAuthServerUrl);
        registry.add("app.keycloak.realm",             () -> "lawforyou");
        registry.add("app.keycloak.client-id",         () -> "lawforyou-backend");
        registry.add("app.keycloak.client-secret",     () -> "lawforyou-backend-secret");
    }

    @BeforeAll
    static void enableKeycloakUnmanagedAttributes() {
        RestTemplate rest = new RestTemplate();
        HttpHeaders formHeaders = new HttpHeaders();
        formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
        tokenForm.add("grant_type", "password");
        tokenForm.add("client_id",  "admin-cli");
        tokenForm.add("username",   keycloak.getAdminUsername());
        tokenForm.add("password",   keycloak.getAdminPassword());
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResp = rest.postForObject(
                keycloak.getAuthServerUrl() + "/realms/master/protocol/openid-connect/token",
                new HttpEntity<>(tokenForm, formHeaders), Map.class);
        String adminToken = (String) Objects.requireNonNull(tokenResp).get("access_token");
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(adminToken);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange(
                keycloak.getAuthServerUrl() + "/admin/realms/lawforyou/users/profile",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("unmanagedAttributePolicy", "ENABLED"), authHeaders),
                Void.class);
    }

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String DEV_TENANT = "00000000-0000-0000-0000-000000000001";

    private RegisterUserRequest uniqueRegisterRequest() {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return new RegisterUserRequest("kc_" + uid, uid + "@kctest.com", "Password123!", "KC", "User", null);
    }

    private String registerUser(RegisterUserRequest req) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .header("X-Tenant-ID", DEV_TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String loginAndGetToken(String usernameOrEmail, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .header("X-Tenant-ID", DEV_TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(usernameOrEmail, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private Map<String, Object> decodeJwtPayload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        String padded = parts[1] + "=".repeat((4 - parts[1].length() % 4) % 4);
        byte[] decoded = Base64.getUrlDecoder().decode(padded);
        return objectMapper.readValue(decoded, new TypeReference<>() {});
    }

    @Nested
    @DisplayName("JWT claim verification after register + login")
    class JwtClaimsTests {

        @Test
        @DisplayName("JWT contains tenant_id, user_id and CLIENT role")
        void jwtContainsAllRequiredClaims() throws Exception {
            var    req    = uniqueRegisterRequest();
            String userId = registerUser(req);
            String token  = loginAndGetToken(req.username(), req.password());
            assertThat(token).isNotBlank();
            Map<String, Object> claims = decodeJwtPayload(token);
            assertThat(claims).as("tenant_id required").containsKey("tenant_id");
            assertThat(claims.get("tenant_id")).asString().isEqualTo(DEV_TENANT);
            assertThat(claims).as("user_id required").containsKey("user_id");
            assertThat(claims.get("user_id")).asString().isEqualTo(userId);
            assertThat(claims).as("roles required").containsKey("roles");
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.get("roles");
            assertThat(roles).as("must have CLIENT role").contains("CLIENT");
            if (claims.containsKey("permissions")) {
                assertThat(claims.get("permissions")).isInstanceOf(List.class);
            }
        }

        @Test
        @DisplayName("Login response has Bearer type and numeric expiresIn")
        void loginResponse_hasCorrectStructure() throws Exception {
            var req = uniqueRegisterRequest();
            registerUser(req);
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Tenant-ID", DEV_TENANT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest(req.username(), req.password()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresIn").isNumber());
        }

        @Test
        @DisplayName("Wrong password returns 401")
        void login_wrongPassword_returns401() throws Exception {
            var req = uniqueRegisterRequest();
            registerUser(req);
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Tenant-ID", DEV_TENANT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest(req.username(), "WrongPass999!"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Duplicate registration")
    class DuplicateRegistrationTests {

        @Test
        @DisplayName("Same email returns 409")
        void duplicateEmail_returns409() throws Exception {
            var req = uniqueRegisterRequest();
            registerUser(req);
            var duplicate = new RegisterUserRequest(
                    "oth_" + UUID.randomUUID().toString().substring(0, 8),
                    req.email(), req.password(), null, null, null);
            mockMvc.perform(post("/api/auth/register")
                            .header("X-Tenant-ID", DEV_TENANT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(duplicate)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Permissions mapper — direct Keycloak call with pre-seeded admin")
    class PermissionsMapperTests {

        @Test
        @DisplayName("Admin JWT contains permissions claim — proves permissions-mapper is configured")
        void adminToken_hasPermissionsClaimWithExpectedValues() throws Exception {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type",    "password");
            form.add("client_id",     "lawforyou-backend");
            form.add("client_secret", "lawforyou-backend-secret");
            form.add("username",      "admin");
            form.add("password",      "Admin@12345");
            form.add("scope",         "openid");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            @SuppressWarnings("unchecked")
            Map<String, Object> tr = new RestTemplate().postForObject(
                    keycloak.getAuthServerUrl() + "/realms/lawforyou/protocol/openid-connect/token",
                    new HttpEntity<>(form, headers), Map.class);
            assertThat(tr).isNotNull();
            String accessToken = (String) tr.get("access_token");
            assertThat(accessToken).isNotBlank();
            Map<String, Object> claims = decodeJwtPayload(accessToken);
            assertThat(claims).as("permissions-mapper must be configured").containsKey("permissions");
            @SuppressWarnings("unchecked")
            List<String> perms = (List<String>) claims.get("permissions");
            assertThat(perms).contains("USER_READ", "CASE_CREATE", "DOCUMENT_READ", "NOTIFICATION_READ");
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.get("roles");
            assertThat(roles).contains("ADMIN");
            assertThat(claims).containsKey("tenant_id");
            assertThat(claims).containsKey("user_id");
        }
    }
}
