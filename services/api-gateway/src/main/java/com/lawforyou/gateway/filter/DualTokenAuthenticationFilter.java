package com.lawforyou.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.gateway.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Phase-4/5 dual-token filter: accepts both Keycloak RS256 tokens and legacy HS256 tokens.
 *
 * <h3>Algorithm detection</h3>
 * The JWT header (first base64url segment) is decoded to read the {@code alg} field.
 * <ul>
 *   <li>{@code RS256} → validated with {@link ReactiveJwtDecoder} (Keycloak JWKS)</li>
 *   <li>Anything else → validated with {@link JwtTokenProvider} (shared HS256 secret)</li>
 * </ul>
 *
 * <h3>Token blacklist (Phase 5)</h3>
 * Before any JWT validation, the raw token is checked against the Redis blacklist
 * (key format: {@code token:bl:<jwt>}) written by {@code user-service} on logout.
 * Blacklisted tokens are rejected with {@code 401} immediately — even if they are
 * still cryptographically valid. If Redis is unavailable or exceeds
 * {@code BLACKLIST_CHECK_TIMEOUT} (500ms), the request is <b>allowed through
 * (fail-open)</b> to prevent Redis saturation under load from rejecting valid users.
 *
 * <h3>Downstream contract (Phase 5)</h3>
 * After successful validation, the original Authorization header is <b>removed</b> and
 * the following headers are injected for downstream services:
 * <ol>
 *   <li>{@code X-User-ID}   — authenticated user UUID</li>
 *   <li>{@code X-Tenant-ID} — tenant UUID from JWT claims</li>
 *   <li>{@code X-Username}  — username / preferred_username</li>
 * </ol>
 * Downstream services use {@code HeaderAuthenticationFilter} (nadeex-spring-security:0.3.0)
 * to build their {@code SecurityContext} from these headers — no JWT re-validation.
 *
 * <h3>Phase roadmap</h3>
 * <ul>
 *   <li><b>Phase 4</b>: dual validation + HS256 internal token re-issue for backward compat.</li>
 *   <li><b>Phase 5 (now)</b>: internal HS256 re-issue removed; downstream uses header-auth.</li>
 *   <li><b>Phase 8</b>: legacy HS256 fallback and {@code jjwt-*} dependencies removed.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DualTokenAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX      = "Bearer ";
    private static final String HEADER_AUTH        = "Authorization";
    private static final String HEADER_USER_ID     = "X-User-ID";
    private static final String HEADER_TENANT_ID   = "X-Tenant-ID";
    private static final String HEADER_USERNAME    = "X-Username";
    private static final String HEADER_ROLES       = "X-Roles";
    private static final String HEADER_PERMISSIONS = "X-Permissions";

    /** Must match {@code TokenBlacklistServiceImpl.KEY_PREFIX} in user-service. */
    private static final String BLACKLIST_KEY_PREFIX = "token:bl:";

    /**
     * Max time to wait for Redis blacklist check.
     * If Redis is slow under load, fail-open (treat as not blacklisted) rather than
     * rejecting valid user requests with 401.
     */
    private static final Duration BLACKLIST_CHECK_TIMEOUT = Duration.ofMillis(500);

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/", "/actuator/", "/v3/api-docs", "/swagger-ui"
    );

    private final JwtTokenProvider          legacyJwtProvider;
    private final ReactiveJwtDecoder        keycloakJwtDecoder;
    private final ObjectMapper              objectMapper;
    private final ReactiveStringRedisTemplate reactiveRedisTemplate;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HEADER_AUTH);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing/malformed Authorization header for path: {}", path);
            return unauthorized(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        String alg   = readAlgorithmFromJwtHeader(token);

        // ── Blacklist check (fail-open on timeout) ───────────────────────────
        // Checked BEFORE signature validation so a logged-out token never reaches
        // downstream services, even if still cryptographically valid.
        //
        // Under load Redis can become a bottleneck — we apply a 500ms timeout and
        // fail-open (treat as NOT blacklisted) rather than rejecting valid requests.
        // True blacklisted tokens are rare; Redis saturation under load is not.
        return reactiveRedisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token)
                .timeout(BLACKLIST_CHECK_TIMEOUT)
                .onErrorResume(e -> {
                    // Redis unavailable OR timed out → fail-open, log at WARN level.
                    log.warn("Redis blacklist check failed for path '{}' (fail-open): {}",
                            path, e.getMessage());
                    return Mono.just(false);   // treat as not blacklisted
                })
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        log.warn("Rejected blacklisted token for path: {}", path);
                        return unauthorized(exchange);
                    }
                    if ("RS256".equals(alg)) {
                        return handleKeycloakToken(token, exchange, chain);
                    } else {
                        return handleLegacyToken(token, exchange, chain);
                    }
                });
    }

    // ── Keycloak RS256 path ───────────────────────────────────────────────────

    private Mono<Void> handleKeycloakToken(String token, ServerWebExchange exchange,
                                            GatewayFilterChain chain) {
        return keycloakJwtDecoder.decode(token)
                .flatMap(jwt -> {
                    String userId   = jwt.getClaimAsString("user_id");
                    String tenantId = jwt.getClaimAsString("tenant_id");
                    String username = jwt.getClaimAsString("preferred_username");
                    List<String> roles       = jwt.getClaimAsStringList("roles");
                    List<String> permissions = jwt.getClaimAsStringList("permissions");

                    if (userId == null || tenantId == null) {
                        log.warn("Keycloak token missing required claims (user_id={}, tenant_id={})",
                                userId, tenantId);
                        return unauthorized(exchange);
                    }

                    log.debug("KC RS256 ok userId={} tenantId={} path={}",
                            userId, tenantId, exchange.getRequest().getURI().getPath());
                    return proceed(exchange, chain, userId, tenantId, username, roles, permissions, "KC");
                })
                .onErrorResume(e -> {
                    log.warn("Keycloak token validation failed: {}", e.getMessage());
                    return unauthorized(exchange);
                });
    }

    // ── Legacy HS256 path ─────────────────────────────────────────────────────

    private Mono<Void> handleLegacyToken(String token, ServerWebExchange exchange,
                                          GatewayFilterChain chain) {
        if (!legacyJwtProvider.validateToken(token)) {
            log.warn("Legacy HS256 token invalid for path: {}",
                    exchange.getRequest().getURI().getPath());
            return unauthorized(exchange);
        }

        String userId   = legacyJwtProvider.getUserId(token).toString();
        String tenantId = legacyJwtProvider.getTenantId(token).toString();
        String username = legacyJwtProvider.getUsername(token);
        List<String> roles       = legacyJwtProvider.getRoles(token);
        List<String> permissions = legacyJwtProvider.getPermissions(token);

        log.debug("HS256 ok userId={} tenantId={} path={}",
                userId, tenantId, exchange.getRequest().getURI().getPath());
        return proceed(exchange, chain, userId, tenantId, username, roles, permissions, "HS256");
    }

    // ── Common continuation ───────────────────────────────────────────────────

    /**
     * Phase 5: Injects tenant/user identity headers and strips the Authorization header.
     * Downstream services authenticate via {@code HeaderAuthenticationFilter} — no JWT
     * re-validation and no internal HS256 token re-issuance.
     */
    private Mono<Void> proceed(ServerWebExchange exchange, GatewayFilterChain chain,
                                String userId, String tenantId, String username,
                                List<String> roles, List<String> permissions, String tokenType) {

        String rolesHeader       = roles       != null ? String.join(",", roles)       : "";
        String permissionsHeader = permissions != null ? String.join(",", permissions) : "";

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER_USER_ID,     userId)
                .header(HEADER_TENANT_ID,   tenantId)
                .header(HEADER_USERNAME,    username != null ? username : "")
                .header(HEADER_ROLES,       rolesHeader)
                .header(HEADER_PERMISSIONS, permissionsHeader)
                // Remove the original Authorization header — downstream services do not
                // re-validate tokens; they trust the X-User-ID / X-Tenant-ID headers.
                .headers(h -> h.remove(HEADER_AUTH))
                .build();

        log.debug("[{}] → userId={} tenantId={} roles={}", tokenType, userId, tenantId, rolesHeader);
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Decodes the JWT header (first base64url segment) to read the {@code alg} field
     * without verifying the signature. Safe — used only for routing, not auth decisions.
     */
    private String readAlgorithmFromJwtHeader(String token) {
        try {
            String[] parts  = token.split("\\.");
            if (parts.length < 2) return "";
            byte[] decoded  = Base64.getUrlDecoder().decode(parts[0]);
            @SuppressWarnings("unchecked")
            Map<String, Object> header = objectMapper.readValue(decoded, Map.class);
            return String.valueOf(header.getOrDefault("alg", ""));
        } catch (Exception e) {
            log.debug("Could not read JWT header alg: {}", e.getMessage());
            return "";
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}

