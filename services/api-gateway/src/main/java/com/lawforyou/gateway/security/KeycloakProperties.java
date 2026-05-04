package com.lawforyou.gateway.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Keycloak JWKS configuration for RS256 token validation.
 * Bound from {@code app.keycloak.*} properties.
 *
 * <p>Local dev: {@code http://localhost:8180/realms/lawforyou/protocol/openid-connect/certs}<br>
 * Docker:      {@code http://lawforyou-keycloak:8080/realms/lawforyou/protocol/openid-connect/certs}
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.keycloak")
public class KeycloakProperties {

    @NotBlank
    private String jwksUri;
}

