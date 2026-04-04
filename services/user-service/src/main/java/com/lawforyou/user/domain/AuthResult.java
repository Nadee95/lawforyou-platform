package com.lawforyou.user.domain;

import com.lawforyou.user.dto.response.UserDto;
import com.nadeex.spring.common.exception.ErrorCode;

/**
 * Sealed result type for authentication operations.
 *
 * <p>Avoids throwing exceptions inside the service layer for expected failures
 * (wrong password, inactive account). The controller uses a pattern-matching
 * switch to decide the HTTP response.</p>
 *
 * <pre>{@code
 * return switch (userService.authenticate(request, tenantId)) {
 *     case AuthResult.Success s -> ResponseEntity.ok(ApiResponse.success(
 *             LoginResponse.of(s.token(), s.expiresIn(), s.user())));
 *     case AuthResult.Failure f -> throw new UnauthorizedException(f.reason());
 * };
 * }</pre>
 */
public sealed interface AuthResult
        permits AuthResult.Success, AuthResult.Failure {

    /**
     * Authentication succeeded.
     *
     * @param token     Signed JWT access token.
     * @param expiresIn Token lifetime in seconds.
     * @param user      Authenticated user's read model.
     */
    record Success(
            String  token,
            long    expiresIn,
            UserDto user
    ) implements AuthResult {}

    /**
     * Authentication failed for an expected reason (wrong credentials, inactive user).
     *
     * @param reason    Human-readable explanation (logged server-side, not exposed to client).
     * @param errorCode Application error code for mapping to HTTP status.
     */
    record Failure(
            String    reason,
            ErrorCode errorCode
    ) implements AuthResult {}
}

