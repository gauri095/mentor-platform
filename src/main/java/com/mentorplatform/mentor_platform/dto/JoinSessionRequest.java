package com.mentorplatform.mentor_platform.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/sessions/join
 *
 * {
 *   "joinLink": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
 * }
 */
public record JoinSessionRequest(
        @NotBlank(message = "Join link is required.")
        String joinLink
) {}