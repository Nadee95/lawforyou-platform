package com.lawforyou.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for registering a new user within a tenant.
 *
 * <p>The {@code tenantSlug} determines which tenant this user belongs to.
 * It is resolved from the request context (header or path) — not trusted from body.</p>
 */
public record RegisterUserRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 100, message = "Username must be 3–100 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                 message = "Username may only contain letters, digits, dots, underscores, hyphens")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be 8–128 characters")
        String password,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Size(max = 30)
        String phone
) {}

