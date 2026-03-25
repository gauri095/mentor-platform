package com.mentorplatform.mentor_platform.dto;

/**
 * STOMP message payload for code sync.
 *
 * Sent by client to /app/code:
 * {
 *   "sessionId": "abc-123",
 *   "code":      "function greet() { ... }",
 *   "language":  "javascript",
 *   "senderId":  "user-uuid"
 * }
 *
 * Broadcast by server to /topic/session/{sessionId}
 */
public record CodeSyncMessage(
        String sessionId,
        String code,
        String language,
        String senderId
) {}