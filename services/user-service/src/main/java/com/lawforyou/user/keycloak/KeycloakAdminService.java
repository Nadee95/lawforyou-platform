package com.lawforyou.user.keycloak;

import com.lawforyou.user.entity.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Facade over the Keycloak Admin REST API.
 *
 * <p>All methods are no-ops when {@code app.keycloak.enabled=false},
 * returning empty optionals so callers can proceed without Keycloak.</p>
 */
public interface KeycloakAdminService {

    /**
     * Creates a user in Keycloak and returns the Keycloak UUID.
     * The UUID must be stored in {@code users.keycloak_id} for future lookups.
     *
     * @param user      saved PostgreSQL user (provides email, username, names)
     * @param tenantId  tenant UUID — stored as user attribute and used for group membership
     * @param password  plaintext password (Keycloak will hash it)
     * @return          Keycloak user UUID, or empty if disabled/failed
     */
    Optional<UUID> createUser(User user, UUID tenantId, String password);

    /**
     * Obtains a Keycloak access + refresh token for the given credentials via
     * the password grant (Resource Owner Password Credentials flow).
     * Used by {@code /api/auth/login} to proxy authentication to Keycloak.
     *
     * @return token response or empty if disabled/credentials are wrong
     */
    Optional<KeycloakTokenResponse> loginForToken(String usernameOrEmail, String password);

    /**
     * Revokes all active sessions for the given Keycloak user UUID.
     * Called during logout to invalidate refresh tokens server-side.
     *
     * @param keycloakId Keycloak user UUID stored in {@code users.keycloak_id}
     */
    void revokeUserSessions(UUID keycloakId);
}

