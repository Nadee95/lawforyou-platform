package com.lawforyou.gateway.filter;
import com.lawforyou.gateway.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.UUID;
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private static final String BEARER_PREFIX    = "Bearer ";
    private static final String HEADER_AUTH      = "Authorization";
    private static final String HEADER_USER_ID   = "X-User-ID";
    private static final String HEADER_TENANT_ID = "X-Tenant-ID";
    private static final String HEADER_USERNAME  = "X-Username";
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/", "/actuator/", "/v3/api-docs", "/swagger-ui"
    );
    private final JwtTokenProvider jwtTokenProvider;
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
            log.warn("Missing/malformed Authorization for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Invalid/expired JWT for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        UUID userId   = jwtTokenProvider.getUserId(token);
        UUID tenantId = jwtTokenProvider.getTenantId(token);
        String username = jwtTokenProvider.getUsername(token);
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER_USER_ID,   userId.toString())
                .header(HEADER_TENANT_ID, tenantId.toString())
                .header(HEADER_USERNAME,  username)
                .headers(h -> h.remove(HEADER_AUTH))
                .build();
        log.debug("JWT ok userId={} tenantId={} path={}", userId, tenantId, path);
        return chain.filter(exchange.mutate().request(mutated).build());
    }
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}