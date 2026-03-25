package com.mentorplatform.mentor_platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/auth/login
 *
 * Example JSON:
 * {
 *   "email":    "alice@example.com",
 *   "password": "Secret123!"
 * }
 */
public record LoginRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "Must be a valid email address.")
        String email,

        @NotBlank(message = "Password is required.")
        String password
        // No size constraint on login — reject by credential check, not length
) {}