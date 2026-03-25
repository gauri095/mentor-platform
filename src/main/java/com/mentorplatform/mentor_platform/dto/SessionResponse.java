package com.mentorplatform.mentor_platform.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mentorplatform.mentor_platform.entity.Session;

/**
 * API response shape for a Session.
 *
 * Null fields (studentId, startedAt, endedAt) are omitted from JSON
 * while the session is still in WAITING state — keeps the payload clean.
 *
 * Example response (WAITING):
 * {
 *   "id":        "550e8400-...",
 *   "mentorId":  "a1b2c3...",
 *   "status":    "WAITING",
 *   "joinLink":  "https://mentor-app.vercel.app/join/f47ac10b-...",
 *   "createdAt": "2025-06-01T10:00:00Z"
 * }
 *
 * Example response (ACTIVE):
 * {
 *   "id":        "550e8400-...",
 *   "mentorId":  "a1b2c3...",
 *   "studentId": "d4e5f6...",
 *   "status":    "ACTIVE",
 *   "joinLink":  "https://mentor-app.vercel.app/join/f47ac10b-...",
 *   "createdAt": "2025-06-01T10:00:00Z",
 *   "startedAt": "2025-06-01T10:05:00Z"
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponse(
        String  id,
        String  mentorId,
        String  studentId,
        String  status,
        String  joinLink,      // full invite URL, not just the token
        Instant createdAt,
        Instant startedAt,
        Instant endedAt
) {
    /**
     * Maps a {@link Session} entity to a response DTO.
     * joinLink is expanded into a full URL using the configured base URL.
     */
    public static SessionResponse from(Session session, String appBaseUrl) {
        return new SessionResponse(
                session.getId(),
                session.getMentorId(),
                session.getStudentId(),
                session.getStatus().name(),
                appBaseUrl + "/join/" + session.getJoinLink(),
                session.getCreatedAt(),
                session.getStartedAt(),
                session.getEndedAt()
        );
    }
}