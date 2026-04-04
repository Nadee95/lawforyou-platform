package com.lawforyou.user.multitenancy;

import com.lawforyou.user.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that resolves the current tenant identifier before any
 * Spring Security or application logic runs.
 *
 * <h3>Resolution order:</h3>
 * <ol>
 *   <li><b>{@code X-Tenant-ID} header</b> — used by unauthenticated callers
 *       (login, register) and by Phase-1 clients that send the header explicitly.</li>
 *   <li><b>JWT {@code tenantId} claim</b> — fallback for authenticated requests
 *       where the header may be absent (e.g. in Phase 2 behind the API Gateway).</li>
 * </ol>
 *
 * <p>The resolved UUID is stored in {@link TenantContext} and is cleared in a
 * {@code finally} block so pooled threads are never contaminated.</p>
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} to ensure the tenant is set
 * before Hibernate opens any session.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String AUTH_HEADER   = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         filterChain)
            throws ServletException, IOException {
        try {
            UUID tenantId = resolveTenantId(request);
            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);
                log.debug("Tenant context set to {} for {}", tenantId, request.getRequestURI());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID resolveTenantId(HttpServletRequest request) {
        // 1. Explicit header — covers login, register, and Phase-1 direct clients
        String tenantHeader = request.getHeader(TENANT_HEADER);
        if (StringUtils.hasText(tenantHeader)) {
            try {
                return UUID.fromString(tenantHeader);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid X-Tenant-ID header value '{}': {}", tenantHeader, e.getMessage());
            }
        }

        // 2. JWT claim fallback — covers authenticated endpoints without the header
        String authHeader = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            try {
                if (jwtTokenProvider.validateToken(token)) {
                    return jwtTokenProvider.getTenantId(token);
                }
            } catch (Exception e) {
                log.debug("Could not extract tenant from JWT: {}", e.getMessage());
            }
        }

        return null;
    }
}

