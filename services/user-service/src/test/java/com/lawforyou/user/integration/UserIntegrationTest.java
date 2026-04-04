package com.lawforyou.user.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration test using TestContainers.
 * Starts real Postgres, Redis, and Kafka containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class UserIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",         postgres::getJdbcUrl);
        registry.add("spring.datasource.username",    postgres::getUsername);
        registry.add("spring.datasource.password",    postgres::getPassword);
        registry.add("spring.data.redis.host",        redis::getHost);
        registry.add("spring.data.redis.port",        () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers",kafka::getBootstrapServers);
        // Disable config server for integration tests
        registry.add("spring.cloud.config.enabled",   () -> "false");
        registry.add("eureka.client.enabled",         () -> "false");
    }

    @Autowired MockMvc mockMvc;

    // ── Tenant setup ─────────────────────────────────────────────────────────
    // NOTE: These tests require a tenant to exist. Add a @BeforeEach that inserts
    // a test tenant via TenantRepository, then use its ID in X-Tenant-ID header.

    @Test
    void contextLoads() {
        // Verifies the application context starts with TestContainers infra
    }

    @Test
    void register_thenLogin_shouldReturnJwtToken() throws Exception {
        // TODO: Insert test tenant, then:
        // 1. POST /api/auth/register  → 201
        // 2. POST /api/auth/login     → 200 with JWT token
        // 3. GET  /api/users/{id}     → 200 with user data (using JWT)
    }
}

