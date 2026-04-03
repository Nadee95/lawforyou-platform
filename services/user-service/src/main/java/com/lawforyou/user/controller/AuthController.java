package com.lawforyou.user.controller;

import com.lawforyou.user.domain.AuthResult;
import com.lawforyou.user.dto.request.LoginRequest;
import com.lawforyou.user.dto.request.RegisterUserRequest;
import com.lawforyou.user.dto.response.LoginResponse;
import com.lawforyou.user.dto.response.UserDto;
import com.lawforyou.user.service.UserService;
import com.nadeex.spring.common.response.ApiResponse;
import com.nadeex.spring.exception.UnauthorizedException;
import com.nadeex.spring.logging.annotation.Loggable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Authentication endpoints — public, no JWT required.
 *
 * <p>Tenant context is resolved from the {@code X-Tenant-ID} header.
 * The API Gateway will set this header based on subdomain routing in Phase 2.
 * For Phase 1, clients pass it directly.</p>
 */
@Tag(name = "Authentication", description = "Login and registration")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Loggable
public class AuthController {

    private final UserService userService;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<UserDto>> register(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody RegisterUserRequest request) {

        UserDto created = userService.register(request, tenantId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "User registered successfully"));
    }

    @Operation(summary = "Authenticate and receive JWT token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody LoginRequest request) {

        // Pattern-matching switch on sealed AuthResult
        return switch (userService.authenticate(request, tenantId)) {
            case AuthResult.Success s -> ResponseEntity.ok(
                    ApiResponse.success(
                            LoginResponse.of(s.token(), s.expiresIn(), s.user()),
                            "Login successful"));
            case AuthResult.Failure f -> throw new UnauthorizedException(f.reason());
        };
    }
}

