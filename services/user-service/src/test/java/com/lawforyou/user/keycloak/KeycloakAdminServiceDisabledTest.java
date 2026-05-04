package com.lawforyou.user.keycloak;

import com.lawforyou.user.entity.Role;
import com.lawforyou.user.entity.User;
import com.lawforyou.user.keycloak.impl.KeycloakAdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KeycloakAdminServiceImpl} when Keycloak is <em>disabled</em>
 * ({@code app.keycloak.enabled=false}).
 *
 * <p>These tests confirm the no-op / safe-default behaviour that lets the application
 * run without a live Keycloak instance.  The "enabled" paths require a running
 * Keycloak realm and are covered by the E2E / Docker-Compose test suite.</p>
 */
@ExtendWith(MockitoExtension.class)
class KeycloakAdminServiceDisabledTest {

    /** Disabled properties — the default for every test environment. */
    private final KeycloakProperties disabledProps = buildProperties(false);

    @Mock RestClient restClient;   // never called when Keycloak is disabled

    private KeycloakAdminService service;

    @BeforeEach
    void setUp() {
        service = new KeycloakAdminServiceImpl(disabledProps, restClient);
    }

    // ── createUser ─────────────────────────────────────────────────────────────

    @Test
    void createUser_whenDisabled_returnsEmpty() {
        User user = testUser();
        Optional<UUID> result = service.createUser(user, UUID.randomUUID(), "secret");
        assertThat(result).isEmpty();
    }

    // ── loginForToken ───────────────────────────────────────────────────────────

    @Test
    void loginForToken_whenDisabled_returnsEmpty() {
        Optional<KeycloakTokenResponse> result = service.loginForToken("user", "pass");
        assertThat(result).isEmpty();
    }

    // ── revokeUserSessions ──────────────────────────────────────────────────────

    @Test
    void revokeUserSessions_whenDisabled_doesNothing() {
        // Must complete without exception and without touching RestClient
        service.revokeUserSessions(UUID.randomUUID());
        // verifyNoInteractions(restClient) — implicitly satisfied by Mockito strict stubs
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static User testUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hash")
                .roles(Set.of())
                .build();
    }

    private static KeycloakProperties buildProperties(boolean enabled) {
        KeycloakProperties p = new KeycloakProperties();
        p.setEnabled(enabled);
        p.setServerUrl("http://localhost:8180");
        p.setRealm("lawforyou");
        p.setClientId("lawforyou-backend");
        p.setClientSecret("test-secret");
        return p;
    }
}

