package com.lawforyou.document.config;

import com.nadeex.spring.multitenancy.token.TenantTokenParser;
import com.nadeex.spring.security.token.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link TenantTokenParser} bean so that
 * {@code nadeex-spring-multitenancy}'s {@code TenantResolutionFilter}
 * can fall back to the JWT {@code tenantId} claim when the
 * {@code X-Tenant-ID} header is absent.
 */
@Configuration
public class MultitenancyConfig {

    /** Enables JWT-based tenant resolution in {@code TenantResolutionFilter}. */
    @Bean
    public TenantTokenParser tenantTokenParser(JwtTokenProvider jwtTokenProvider) {
        return rawToken -> {
            try {
                return jwtTokenProvider.isTokenValid(rawToken)
                        ? jwtTokenProvider.getTenantId(rawToken)
                        : null;
            } catch (Exception e) {
                return null;
            }
        };
    }
}

