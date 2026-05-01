package com.lawforyou.user.dto.response;

/**
 * Response returned after a successful authentication.
 *
 * @param accessToken   JWT bearer token to include in subsequent requests.
 * @param refreshToken  Keycloak refresh token (Phase 4+). {@code null} for legacy HS256 logins.
 *                      Use this to obtain a new access token via the Keycloak token endpoint.
 * @param tokenType     Always {@code "Bearer"}.
 * @param expiresIn     Token validity in seconds.
 * @param user          Authenticated user's profile and permissions.
 */
public record LoginResponse(
        String  accessToken,
        String  refreshToken,
        String  tokenType,
        long    expiresIn,
        UserDto user
) {
    /** Factory for legacy HS256 logins (no refresh token). */
    public static LoginResponse of(String accessToken, long expiresIn, UserDto user) {
        return new LoginResponse(accessToken, null, "Bearer", expiresIn, user);
    }

    /** Factory for Keycloak logins — includes refresh token. */
    public static LoginResponse ofKeycloak(String accessToken, String refreshToken,
                                           long expiresIn, UserDto user) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}

