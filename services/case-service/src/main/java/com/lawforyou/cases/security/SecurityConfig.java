package com.lawforyou.cases.security;

import com.nadeex.spring.security.config.SecurityFilterChainConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the Case Service.
 *
 * <p>Delegates stateless JWT filter-chain setup to
 * {@link SecurityFilterChainConfigurer} from {@code nadeex-spring-security}.
 * Case-service has no user store — authorities come directly from JWT claims.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityFilterChainConfigurer securityFilterChainConfigurer;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return securityFilterChainConfigurer.build(http, auth -> auth
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
        );
    }
}
