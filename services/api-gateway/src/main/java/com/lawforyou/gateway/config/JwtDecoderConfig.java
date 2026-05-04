package com.lawforyou.gateway.config;

import com.lawforyou.gateway.security.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Registers a {@link ReactiveJwtDecoder} that fetches Keycloak's RS256 public keys
 * from the JWKS URI and caches them automatically (Nimbus rotates on 401 from jwks endpoint).
 *
 * <p>This decoder is used exclusively by {@code DualTokenAuthenticationFilter} to validate
 * Keycloak-issued tokens. Legacy HS256 tokens are still validated by {@code JwtTokenProvider}.
 */
@Configuration
@RequiredArgsConstructor
public class JwtDecoderConfig {

    private final KeycloakProperties keycloakProperties;

    @Bean
    public ReactiveJwtDecoder keycloakJwtDecoder() {
        return NimbusReactiveJwtDecoder
                .withJwkSetUri(keycloakProperties.getJwksUri())
                .build();
    }
}

