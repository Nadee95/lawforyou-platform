package com.lawforyou.user.keycloak;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Keycloak Admin API configuration for the user-service.
 *
 * <p>Set {@code app.keycloak.enabled=true} to activate Keycloak integration.
 * Defaults to {@code false} so tests and local dev without Keycloak still work.</p>
 *
 * <p>Local dev:  server-url = http://localhost:8180<br>
 * Docker:        server-url = http://lawforyou-keycloak:8080</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.keycloak")
public class KeycloakProperties {

    /** Master switch — set to true to enable Keycloak Admin API calls. */
    private boolean enabled = false;

    /** Keycloak base URL (no trailing slash). */
    private String serverUrl = "http://localhost:8180";

    /** Realm name. */
    private String realm = "lawforyou";

    /** Confidential client used for Admin API (service account) and password grant (login). */
    private String clientId = "lawforyou-backend";

    /** Client secret — override via KEYCLOAK_CLIENT_SECRET env var in production. */
    private String clientSecret = "lawforyou-backend-secret";
}

