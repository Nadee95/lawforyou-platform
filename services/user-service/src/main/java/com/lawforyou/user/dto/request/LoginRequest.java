package com.lawforyou.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for authenticating a user.
 * Accepts either username or email in the {@code usernameOrEmail} field.
 */
public record LoginRequest(

        @NotBlank(message = "Username or email is required")
        String usernameOrEmail,

        @NotBlank(message = "Password is required")
        String password
) {}

