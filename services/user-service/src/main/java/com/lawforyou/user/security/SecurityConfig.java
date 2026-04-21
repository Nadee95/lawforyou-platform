package com.lawforyou.user.security;

import com.nadeex.spring.security.config.SecurityFilterChainConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the User Service.
 *
 * <ul>
 *   <li>Delegates stateless JWT filter-chain setup to
 *       {@link SecurityFilterChainConfigurer} from {@code nadeex-spring-security}.</li>
 *   <li>Keeps service-specific beans: {@link DaoAuthenticationProvider},
 *       {@link PasswordEncoder}, {@link AuthenticationManager}.</li>
 *   <li>{@code @EnableMethodSecurity} activates {@code @PreAuthorize}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl        userDetailsService;
    private final SecurityFilterChainConfigurer securityFilterChainConfigurer;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return securityFilterChainConfigurer.build(http, auth -> auth
                // Public — authentication endpoints
                .requestMatchers(HttpMethod.POST,
                        "/api/auth/login",
                        "/api/auth/register").permitAll()
                // Public — infrastructure & observability
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
        );
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
