package com.lawforyou.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Partial-update payload for user profile fields.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateUserRequest(

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Size(max = 30)
        String phone,

        @Email(message = "Invalid email format")
        @Size(max = 255)
        String email
) {}

