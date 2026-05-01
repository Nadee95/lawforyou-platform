package com.lawforyou.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.gateway.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Phase-3 dual-token filter: accepts both Keycloak RS256 tokens and legacy HS256 tokens.
 *
 * <h3>Algorithm detection</h3>
 * The JWT header (first base64url segment) is decoded to read the {@code alg} field.
 * <ul>
 *   <li>{@code RS256} → validated with {@link ReactiveJwtDecoder} (Keycloak JWKS)</li>
 *   <li>Anything else → validated with {@link JwtTokenProvider} (shared HS256 secret)</li>
 * </ul>
 *
 * <h3>Downstream contract</h3>
 * After successful validation, regardless of the input token type:
 * <ol>
 *   <li>{@code X-User-ID}, {@code X-Tenant-ID}, {@code X-Username} headers are injected.</li>
 *   <li>The original Authorization header is <b>replaced</b> with a new short-lived HS256
 *       gateway-internal token so downstream services can continue to validate JWT normally
 *       without any Phase-3 changes.</li>
 * </ol>
 *
 * <h3>Phase roadmap</h3>
 * <ul>
 *   <li><b>Phase 3 (now)</b>: dual validation + HS256 internal token for backward compat.</li>
 *   <li><b>Phase 5</b>: downstream services become OAuth2 resource servers → internal token generation removed.</li>
 *   <li><b>Phase 8</b>: legacy HS256 fallback and {@code jjwt-*} dependencies removed.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DualTokenAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX    = "Bearer ";
    private static final String HEADER_AUTH      = "Authorization";
    private static final String HEADER_USER_ID   = "X-User-ID";
    private static final String HEADER_TENANT_ID = "X-Tenant-ID";
    private static final String HEADER_USERNAME  = "X-Username";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/", "/actuator/", "/v3/api-docs", "/swagger-ui"
    );

    private final JwtTokenProvider  legacyJwtProvider;
    private final ReactiveJwtDecoder keycloakJwtDecoder;
    private final ObjectMapper       objectMapper;

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

        if ("RS256".equals(alg)) {
            return handleKeycloakToken(token, exchange, chain);
        } else {
            return handleLegacyToken(token, exchange, chain);
        }
    }

    // ── Keycloak RS256 path ───────────────────────────────────────────────────

    private Mono<Void> handleKeycloakToken(String token, ServerWebExchange exchange,
                                            GatewayFilterChain chain) {
        return keycloakJwtDecoder.decode(token)
                .flatMap(jwt -> {
                    String userId   = jwt.getClaimAsString("user_id");
                    String tenantId = jwt.getClaimAsString("tenant_id");
                    String username = jwt.getClaimAsString("preferred_username");
                    List<String> roles = jwt.getClaimAsStringList("roles");

                    if (userId == null || tenantId == null) {
                        log.warn("Keycloak token missing required claims (user_id={}, tenant_id={})",
                                userId, tenantId);
                        return unauthorized(exchange);
                    }

                    log.debug("KC RS256 ok userId={} tenantId={} path={}",
                            userId, tenantId, exchange.getRequest().getURI().getPath());
                    return proceed(exchange, chain, userId, tenantId, username, roles, "KC");
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
        List<String> roles = legacyJwtProvider.getRoles(token);

        log.debug("HS256 ok userId={} tenantId={} path={}",
                userId, tenantId, exchange.getRequest().getURI().getPath());
        return proceed(exchange, chain, userId, tenantId, username, roles, "HS256");
    }

    // ── Common continuation ───────────────────────────────────────────────────

    /**
     * Injects tenant/user headers and replaces the Authorization header with a
     * fresh HS256 gateway-internal token for backward-compatible downstream validation.
     */
    private Mono<Void> proceed(ServerWebExchange exchange, GatewayFilterChain chain,
                                String userId, String tenantId, String username,
                                List<String> roles, String tokenType) {
        // Generate a new short-lived HS256 token downstream services can validate
        String internalToken = legacyJwtProvider.generateToken(userId, tenantId, username, roles);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER_USER_ID,   userId)
                .header(HEADER_TENANT_ID, tenantId)
                .header(HEADER_USERNAME,  username != null ? username : "")
                .header(HEADER_AUTH, BEARER_PREFIX + internalToken)
                .build();

        log.debug("[{}] → userId={} tenantId={}", tokenType, userId, tenantId);
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

