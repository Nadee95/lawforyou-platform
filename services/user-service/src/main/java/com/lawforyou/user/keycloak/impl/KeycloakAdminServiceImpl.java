package com.lawforyou.user.keycloak.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lawforyou.user.entity.User;
import com.lawforyou.user.keycloak.KeycloakAdminService;
import com.lawforyou.user.keycloak.KeycloakProperties;
import com.lawforyou.user.keycloak.KeycloakTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keycloak Admin REST API implementation using Spring's {@link RestClient}.
 *
 * <p>All methods check {@code app.keycloak.enabled} first and return empty/noop
 * when disabled — safe for test environments without Keycloak.</p>
 *
 * <h3>Keycloak Admin API calls used:</h3>
 * <ul>
 *   <li>{@code POST /realms/{realm}/protocol/openid-connect/token} — service account + password grant</li>
 *   <li>{@code POST /admin/realms/{realm}/users} — create user</li>
 *   <li>{@code DELETE /admin/realms/{realm}/users/{id}/sessions} — revoke sessions</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminServiceImpl implements KeycloakAdminService {

    private final KeycloakProperties keycloakProperties;
    private final RestClient         restClient;

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    public Optional<UUID> createUser(User user, UUID tenantId, String password) {
        if (!keycloakProperties.isEnabled()) return Optional.empty();

        try {
            String serviceToken = getServiceAccountToken();
            String url = keycloakProperties.getServerUrl()
                    + "/admin/realms/" + keycloakProperties.getRealm() + "/users";

            // Keycloak UserRepresentation (minimal subset)
            Map<String, Object> body = Map.of(
                    "username",      user.getUsername(),
                    "email",         user.getEmail(),
                    "firstName",     user.getFirstName()  != null ? user.getFirstName()  : "",
                    "lastName",      user.getLastName()   != null ? user.getLastName()   : "",
                    "enabled",       true,
                    "emailVerified", true,
                    "attributes", Map.of(
                            "tenant_id", List.of(tenantId.toString()),
                            "user_id",   List.of(user.getId().toString())
                    ),
                    "credentials", List.of(Map.of(
                            "type",      "password",
                            "value",     password,
                            "temporary", false
                    )),
                    "groups", List.of("/tenant-" + tenantId)
            );

            ResponseEntity<Void> response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            if (response.getStatusCode() == HttpStatus.CREATED) {
                URI location = response.getHeaders().getLocation();
                if (location != null) {
                    String path = location.getPath();
                    String keycloakId = path.substring(path.lastIndexOf('/') + 1);
                    log.info("Created Keycloak user {} for PostgreSQL user {}", keycloakId, user.getId());
                    assignClientRole(serviceToken, keycloakId, "CLIENT");
                    return Optional.of(UUID.fromString(keycloakId));
                }
            }
        } catch (HttpClientErrorException e) {
            log.warn("Keycloak user creation failed ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Keycloak user creation error (continuing without KC identity): {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<KeycloakTokenResponse> loginForToken(String usernameOrEmail, String password) {
        if (!keycloakProperties.isEnabled()) return Optional.empty();

        try {
            String url = keycloakProperties.getServerUrl()
                    + "/realms/" + keycloakProperties.getRealm()
                    + "/protocol/openid-connect/token";

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type",    "password");
            form.add("client_id",     keycloakProperties.getClientId());
            form.add("client_secret", keycloakProperties.getClientSecret());
            form.add("username",      usernameOrEmail);
            form.add("password",      password);
            form.add("scope",         "openid");

            KcTokenRaw raw = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KcTokenRaw.class);

            if (raw != null && raw.accessToken() != null) {
                return Optional.of(new KeycloakTokenResponse(
                        raw.accessToken(), raw.refreshToken(), raw.expiresIn(), "Bearer"));
            }
        } catch (HttpClientErrorException.Unauthorized e) {
            log.debug("Keycloak login rejected — invalid credentials");
        } catch (Exception e) {
            log.warn("Keycloak login error (falling back to legacy auth): {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void revokeUserSessions(UUID keycloakId) {
        if (!keycloakProperties.isEnabled()) return;

        try {
            String serviceToken = getServiceAccountToken();
            String url = keycloakProperties.getServerUrl()
                    + "/admin/realms/" + keycloakProperties.getRealm()
                    + "/users/" + keycloakId + "/sessions";

            restClient.delete()
                    .uri(url)
                    .header("Authorization", "Bearer " + serviceToken)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Revoked Keycloak sessions for user {}", keycloakId);
        } catch (Exception e) {
            log.warn("Failed to revoke Keycloak sessions for {}: {}", keycloakId, e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Assigns a realm-level role to a Keycloak user.
     * Used to mirror the PostgreSQL role (e.g. CLIENT) into the Keycloak token.
     */
    @SuppressWarnings("unchecked")
    private void assignClientRole(String serviceToken, String kcUserId, String roleName) {
        try {
            String baseUrl = keycloakProperties.getServerUrl()
                    + "/admin/realms/" + keycloakProperties.getRealm();

            // 1. Look up the role representation (returns single object)
            Map<String, Object> role = restClient.get()
                    .uri(baseUrl + "/roles/" + roleName)
                    .header("Authorization", "Bearer " + serviceToken)
                    .retrieve()
                    .body(Map.class);

            if (role == null || role.get("id") == null) {
                log.warn("Realm role '{}' not found in Keycloak", roleName);
                return;
            }

            // 2. Assign to user (Keycloak expects a JSON array)
            restClient.post()
                    .uri(baseUrl + "/users/" + kcUserId + "/role-mappings/realm")
                    .header("Authorization", "Bearer " + serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Assigned realm role '{}' to Keycloak user {}", roleName, kcUserId);
        } catch (Exception e) {
            log.warn("Could not assign realm role '{}' to Keycloak user {}: {}", roleName, kcUserId, e.getMessage());
        }
    }

    /**
     * Obtains a service account token via {@code client_credentials} grant.
     * The service account on {@code lawforyou-backend} must have the
     * {@code realm-management > manage-users} role assigned in Keycloak.
     */
    private String getServiceAccountToken() {
        String url = keycloakProperties.getServerUrl()
                + "/realms/" + keycloakProperties.getRealm()
                + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",    "client_credentials");
        form.add("client_id",     keycloakProperties.getClientId());
        form.add("client_secret", keycloakProperties.getClientSecret());

        KcTokenRaw raw = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KcTokenRaw.class);

        if (raw == null || raw.accessToken() == null) {
            throw new IllegalStateException("Failed to obtain Keycloak service account token");
        }
        return raw.accessToken();
    }

    /** Internal DTO for deserializing Keycloak's snake_case token response. */
    private record KcTokenRaw(
            @JsonProperty("access_token")  String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in")    long   expiresIn
    ) {}
}

