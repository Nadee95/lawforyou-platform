package com.lawforyou.user.dto.response;

/**
 * Response returned after a successful authentication.
 *
 * @param accessToken  JWT bearer token to include in subsequent requests.
 * @param tokenType    Always {@code "Bearer"}.
 * @param expiresIn    Token validity in seconds.
 * @param user         Authenticated user's profile and permissions.
 */
public record LoginResponse(
        String  accessToken,
        String  tokenType,
        long    expiresIn,
        UserDto user
) {
    /** Factory method with {@code tokenType} defaulted to {@code "Bearer"}. */
    public static LoginResponse of(String accessToken, long expiresIn, UserDto user) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, user);
    }
}

