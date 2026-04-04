package com.lawforyou.user.service;

import com.lawforyou.user.domain.AuthResult;
import com.lawforyou.user.dto.request.LoginRequest;
import com.lawforyou.user.dto.request.RegisterUserRequest;
import com.lawforyou.user.dto.request.UpdateUserRequest;
import com.lawforyou.user.dto.response.UserDto;
import com.nadeex.spring.common.response.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    /** Registers a new user in the given tenant. */
    UserDto register(RegisterUserRequest request, UUID tenantId);

    /** Authenticates a user and returns a JWT token on success. */
    AuthResult authenticate(LoginRequest request, UUID tenantId);

    /** Returns a user by id, scoped to the given tenant. */
    UserDto findById(UUID userId, UUID tenantId);

    /** Returns a paged list of users for a tenant. */
    PagedResponse<UserDto> findAll(UUID tenantId, Pageable pageable);

    /** Full-text search across username, email, first/last name. */
    PagedResponse<UserDto> search(UUID tenantId, String query, Pageable pageable);

    /** Partial update of a user's profile fields. */
    UserDto update(UUID userId, UUID tenantId, UpdateUserRequest request);

    /** Activates or deactivates a user account. */
    UserDto setActive(UUID userId, UUID tenantId, boolean active);

    /** Assigns a role to a user. */
    UserDto assignRole(UUID userId, UUID tenantId, UUID roleId);

    /** Removes a role from a user. */
    UserDto removeRole(UUID userId, UUID tenantId, UUID roleId);
}

