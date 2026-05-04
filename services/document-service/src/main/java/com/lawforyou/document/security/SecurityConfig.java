package com.lawforyou.document.security;

import com.nadeex.spring.security.config.HeaderSecurityFilterChainConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the Document Service.
 *
 * <p>Phase 5+: Uses {@link HeaderSecurityFilterChainConfigurer} from
 * {@code nadeex-spring-security:0.3.0}. Trusts pre-validated
 * {@code X-User-ID} / {@code X-Tenant-ID} / {@code X-Username} headers
 * injected by the API Gateway after it has validated the original JWT
 * (Keycloak RS256 or legacy HS256). No JWT re-validation occurs here.</p>
 *
 * <p>The gateway is the single authentication boundary for all external traffic.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final HeaderSecurityFilterChainConfigurer headerSecurityFilterChainConfigurer;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return headerSecurityFilterChainConfigurer.build(http, auth -> auth
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
        );
    }
}

