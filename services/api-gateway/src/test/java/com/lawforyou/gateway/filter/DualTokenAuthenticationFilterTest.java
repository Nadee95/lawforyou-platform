package com.lawforyou.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.gateway.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DualTokenAuthenticationFilter} — focuses on the token blacklist
 * check (Phase 5 addition). Legacy JWT validation is tested in {@code JwtAuthenticationFilterTest}.
 *
 * <p>A real {@link ObjectMapper} is used so {@code readAlgorithmFromJwtHeader}
 * works correctly without mocking JSON deserialisation.</p>
 *
 * <p>Fake HS256 token header: {@code eyJhbGciOiJIUzI1NiJ9} = base64url of {@code {"alg":"HS256"}}.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DualTokenAuthenticationFilter")
class DualTokenAuthenticationFilterTest {

    // A minimal structurally-valid JWT with alg=HS256 in the header — signature is fake.
    private static final String FAKE_HS256_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.fake-sig";

    private static final String BLACKLIST_KEY = "token:bl:" + FAKE_HS256_TOKEN;

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock private JwtTokenProvider           legacyJwtProvider;
    @Mock private ReactiveJwtDecoder         keycloakJwtDecoder;
    @Mock private ReactiveStringRedisTemplate reactiveRedisTemplate;
    @Mock private GatewayFilterChain         chain;

    private DualTokenAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        // Use a real ObjectMapper so readAlgorithmFromJwtHeader actually works.
        filter = new DualTokenAuthenticationFilter(
                legacyJwtProvider, keycloakJwtDecoder, new ObjectMapper(), reactiveRedisTemplate);

        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ── Public paths — blacklist must NOT be consulted ────────────────────────

    @Nested
    @DisplayName("Public paths")
    class PublicPaths {

        @Test
        @DisplayName("skips blacklist check for /api/auth/ (no token required)")
        void authPath_skipsBlacklist() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/auth/login").build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(chain).filter(any());
            verifyNoInteractions(reactiveRedisTemplate);
        }

        @Test
        @DisplayName("skips blacklist check for /actuator/health")
        void actuatorPath_skipsBlacklist() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/actuator/health").build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verifyNoInteractions(reactiveRedisTemplate);
        }
    }

    // ── Missing token — blacklist must NOT be consulted ───────────────────────

    @Nested
    @DisplayName("Missing or malformed Authorization header")
    class MissingToken {

        @Test
        @DisplayName("401 with no Authorization header — no Redis call")
        void noHeader_returns401() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/cases").build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
            verifyNoInteractions(reactiveRedisTemplate);
        }

        @Test
        @DisplayName("401 when Authorization is not Bearer — no Redis call")
        void basicAuth_returns401() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/cases")
                            .header("Authorization", "Basic abc123").build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(reactiveRedisTemplate);
        }
    }

    // ── Blacklist checks ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Token blacklist")
    class Blacklist {

        @Test
        @DisplayName("401 and no downstream call when token is blacklisted")
        void blacklistedToken_returns401AndSkipsValidation() {
            when(reactiveRedisTemplate.hasKey(BLACKLIST_KEY)).thenReturn(Mono.just(true));

            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/cases")
                            .header("Authorization", "Bearer " + FAKE_HS256_TOKEN).build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
            // JWT validation must NOT be attempted for a blacklisted token
            verify(legacyJwtProvider, never()).validateToken(anyString());
            verify(keycloakJwtDecoder, never()).decode(anyString());
        }

        @Test
        @DisplayName("401 (fail-closed) when Redis throws an exception")
        void redisError_returns401FailClosed() {
            when(reactiveRedisTemplate.hasKey(anyString()))
                    .thenReturn(Mono.error(new RuntimeException("Redis connection refused")));

            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/cases")
                            .header("Authorization", "Bearer " + FAKE_HS256_TOKEN).build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("proceeds to JWT validation when token is NOT blacklisted")
        void notBlacklisted_proceedsToValidation() {
            when(reactiveRedisTemplate.hasKey(BLACKLIST_KEY)).thenReturn(Mono.just(false));
            when(legacyJwtProvider.validateToken(FAKE_HS256_TOKEN)).thenReturn(true);
            when(legacyJwtProvider.getUserId(FAKE_HS256_TOKEN)).thenReturn(USER_ID);
            when(legacyJwtProvider.getTenantId(FAKE_HS256_TOKEN)).thenReturn(TENANT_ID);
            when(legacyJwtProvider.getUsername(FAKE_HS256_TOKEN)).thenReturn("john.doe");
            when(legacyJwtProvider.getRoles(FAKE_HS256_TOKEN)).thenReturn(List.of("LAWYER"));
            when(legacyJwtProvider.getPermissions(FAKE_HS256_TOKEN)).thenReturn(List.of());

            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/cases")
                            .header("Authorization", "Bearer " + FAKE_HS256_TOKEN).build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(reactiveRedisTemplate).hasKey(BLACKLIST_KEY);
            verify(legacyJwtProvider).validateToken(FAKE_HS256_TOKEN);
            verify(chain).filter(any());
        }

        @Test
        @DisplayName("blacklist is checked using the correct key prefix 'token:bl:'")
        void blacklistKey_usesCorrectPrefix() {
            when(reactiveRedisTemplate.hasKey(BLACKLIST_KEY)).thenReturn(Mono.just(false));
            when(legacyJwtProvider.validateToken(FAKE_HS256_TOKEN)).thenReturn(true);
            when(legacyJwtProvider.getUserId(FAKE_HS256_TOKEN)).thenReturn(USER_ID);
            when(legacyJwtProvider.getTenantId(FAKE_HS256_TOKEN)).thenReturn(TENANT_ID);
            when(legacyJwtProvider.getUsername(FAKE_HS256_TOKEN)).thenReturn("test");
            when(legacyJwtProvider.getRoles(FAKE_HS256_TOKEN)).thenReturn(List.of());
            when(legacyJwtProvider.getPermissions(FAKE_HS256_TOKEN)).thenReturn(List.of());

            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/cases")
                            .header("Authorization", "Bearer " + FAKE_HS256_TOKEN).build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            // Verify the exact key format matches user-service's TokenBlacklistServiceImpl
            verify(reactiveRedisTemplate).hasKey("token:bl:" + FAKE_HS256_TOKEN);
        }
    }
}

