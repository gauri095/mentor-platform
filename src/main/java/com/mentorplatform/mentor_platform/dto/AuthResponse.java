package com.mentorplatform.mentor_platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for all /api/auth/* endpoints.
 *
 * Example JSON:
 * {
 *   "access_token":  "eyJ...",
 *   "refresh_token": "eyJ...",
 *   "token_type":    "Bearer",
 *   "role":          "MENTOR"
 * }
 */
public record AuthResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("token_type")
        String tokenType,

        String role
) {
    /** Convenience factory — token type is always "Bearer". */
    public static AuthResponse of(String accessToken, String refreshToken, String role) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", role);
    }
}