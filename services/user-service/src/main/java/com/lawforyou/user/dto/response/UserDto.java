package com.lawforyou.user.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Read model for a user. Returned from all user-facing endpoints.
 *
 * <p>Uses Java record for immutability. {@code permissions} is flattened from
 * all roles so callers don't need to iterate roles client-side.</p>
 */
public record UserDto(

        UUID    id,
        String  username,
        String  email,
        String  firstName,
        String  lastName,
        String  phone,
        boolean active,

        /** Names of all roles assigned to this user (e.g. "ADMIN", "LAWYER"). */
        Set<String> roles,

        /**
         * Flattened set of permission names from all roles
         * (e.g. "USER_READ", "CASE_CREATE"). Useful for frontend RBAC checks.
         */
        Set<String> permissions,

        UUID    tenantId,
        Instant createdAt,
        Instant updatedAt,
        String  createdBy,
        String  updatedBy
) {}

