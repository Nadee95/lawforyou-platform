    package com.lawforyou.user.keycloak;

/**
 * Token response from Keycloak's {@code /protocol/openid-connect/token} endpoint.
 */
public record KeycloakTokenResponse(
        String accessToken,
        String refreshToken,
        long   expiresIn,
        String tokenType
) {}

