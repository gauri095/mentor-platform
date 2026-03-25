package com.mentorplatform.mentor_platform.dto;

import java.time.Instant;

/**
 * STOMP message payload for session chat.
 *
 * Sent by client to /app/chat:
 * {
 *   "sessionId": "abc-123",
 *   "senderId":  "user-uuid",
 *   "senderEmail": "alice@example.com",
 *   "message":   "Can you explain line 5?",
 *   "type":      "CHAT"
 * }
 *
 * Broadcast by server to /topic/session/{sessionId}
 * (timestamp added server-side before broadcast)
 */
public record ChatMessage(
        String  sessionId,
        String  senderId,
        String  senderEmail,
        String  message,
        Type    type,
        Instant timestamp
) {
    public enum Type { CHAT, SYSTEM }

    /** Factory used by the server to add a timestamp before broadcasting. */
    public ChatMessage withTimestamp() {
        return new ChatMessage(
                sessionId, senderId, senderEmail,
                message, type, Instant.now()
        );
    }
}