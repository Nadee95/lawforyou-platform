package com.lawforyou.user.controller;

import com.lawforyou.user.dto.request.UpdateUserRequest;
import com.lawforyou.user.dto.response.UserDto;
import com.lawforyou.user.service.UserService;
import com.nadeex.spring.common.response.ApiResponse;
import com.nadeex.spring.common.response.PagedResponse;
import com.nadeex.spring.logging.annotation.Loggable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * CRUD endpoints for user management. All require authentication.
 *
 * <p>Permission-based access control via {@code @PreAuthorize}:
 * <ul>
 *   <li>List/search all users — requires {@code USER_READ} permission</li>
 *   <li>Get/update own profile — authenticated user only</li>
 *   <li>Activate/deactivate — requires {@code USER_UPDATE} permission</li>
 *   <li>Role assignment — requires {@code ROLE_MANAGE} permission</li>
 * </ul>
 * </p>
 */
@Tag(name = "Users", description = "User management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Loggable
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get paginated list of users for the tenant")
    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<PagedResponse<UserDto>> listUsers(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PageableDefault(size = 20, sort = "createdAt",
                             direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(userService.findAll(tenantId, pageable));
    }

    @Operation(summary = "Search users by username, email or name")
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<PagedResponse<UserDto>> searchUsers(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(userService.search(tenantId, q, pageable));
    }

    @Operation(summary = "Get a user by id")
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('USER_READ') or #userId.toString() == authentication.name")
    public ResponseEntity<ApiResponse<UserDto>> getUser(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID userId) {

        return ResponseEntity.ok(ApiResponse.success(userService.findById(userId, tenantId)));
    }

    @Operation(summary = "Update own profile (partial update)")
    @PatchMapping("/{userId}")
    @PreAuthorize("#userId.toString() == authentication.name or hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(userService.update(userId, tenantId, request)));
    }

    @Operation(summary = "Activate or deactivate a user")
    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<UserDto>> setUserStatus(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID userId,
            @RequestParam boolean active) {

        return ResponseEntity.ok(
                ApiResponse.success(userService.setActive(userId, tenantId, active)));
    }

    @Operation(summary = "Assign a role to a user")
    @PostMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<UserDto>> assignRole(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID userId,
            @PathVariable UUID roleId) {

        return ResponseEntity.ok(
                ApiResponse.success(userService.assignRole(userId, tenantId, roleId)));
    }

    @Operation(summary = "Remove a role from a user")
    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<UserDto>> removeRole(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID userId,
            @PathVariable UUID roleId) {

        return ResponseEntity.ok(
                ApiResponse.success(userService.removeRole(userId, tenantId, roleId)));
    }
}

