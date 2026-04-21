package com.lawforyou.gateway;
import com.lawforyou.gateway.filter.JwtAuthenticationFilter;
import com.lawforyou.gateway.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {
    private static final String SECRET = "lawforyou-dev-secret-key-change-in-production-min32chars";
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private GatewayFilterChain chain;
    @InjectMocks private JwtAuthenticationFilter filter;
    @BeforeEach
    void setUp() {
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }
    private String validToken() {
        Date now = new Date();
        return Jwts.builder()
                .subject(USER_ID.toString())
                .claim("tenantId", TENANT_ID.toString())
                .claim("username", "john.doe")
                .claim("roles", List.of("CLIENT"))
                .claim("permissions", List.of())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
    @Nested @DisplayName("Public paths")
    class PublicPaths {
        @Test @DisplayName("bypass JWT for /api/auth/")
        void bypassAuth() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/auth/register").build());
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            verify(chain).filter(any());
            verify(jwtTokenProvider, never()).validateToken(any());
        }
        @Test @DisplayName("bypass JWT for /actuator/")
        void bypassActuator() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/actuator/health").build());
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            verify(jwtTokenProvider, never()).validateToken(any());
        }
    }
    @Nested @DisplayName("Missing or malformed token")
    class MissingToken {
        @Test @DisplayName("401 when no Authorization header")
        void noHeader() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/users/me").build());
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }
        @Test @DisplayName("401 when header is not Bearer")
        void notBearer() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/users/me")
                            .header("Authorization", "Basic abc123").build());
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
    @Nested @DisplayName("Invalid token")
    class InvalidToken {
        @Test @DisplayName("401 when token fails validation")
        void badToken() {
            when(jwtTokenProvider.validateToken("bad.token")).thenReturn(false);
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/cases")
                            .header("Authorization", "Bearer bad.token").build());
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }
    }
    @Nested @DisplayName("Valid token")
    class ValidToken {
        @Test @DisplayName("forwards request with injected identity headers")
        void injectsHeaders() {
            String token = validToken();
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getUserId(token)).thenReturn(USER_ID);
            when(jwtTokenProvider.getTenantId(token)).thenReturn(TENANT_ID);
            when(jwtTokenProvider.getUsername(token)).thenReturn("john.doe");
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/cases")
                            .header("Authorization", "Bearer " + token).build());
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            verify(chain).filter(any());
        }
    }
}