package com.mentorplatform.mentor_platform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Persisted chat message for a session.
 *
 * DB table: messages
 * ┌─────────────┬──────────────────────────────────────────────┐
 * │ id          │ UUID primary key, auto-generated             │
 * │ session_id  │ UUID FK → sessions.id  (NOT NULL)           │
 * │ sender_id   │ UUID FK → users.id     (NOT NULL)           │
 * │ sender_email│ denormalised for display (no extra join)     │
 * │ content     │ message text (NOT NULL)                      │
 * │ type        │ CHAT | SYSTEM                                │
 * │ timestamp   │ set on INSERT via @PrePersist                │
 * └─────────────┴──────────────────────────────────────────────┘
 *
 * mentorId / studentId are stored as plain Strings (UUID) — same
 * pattern as Session — to avoid bidirectional JPA joins in the
 * WebSocket layer.
 */
@Entity
@Table(name = "messages", indexes = {
        // fast lookup of all messages for a session, newest first
        @Index(name = "idx_messages_session_id", columnList = "session_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    // ── Primary key ───────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    // ── Session reference ─────────────────────────────────────────────────────

    /**
     * UUID of the session this message belongs to.
     * Maps to sessions.id — stored as String to avoid JPA join overhead.
     */
    @Column(name = "session_id", nullable = false, updatable = false)
    private String sessionId;

    // ── Sender ────────────────────────────────────────────────────────────────

    /**
     * UUID of the user who sent this message.
     * Maps to users.id.
     */
    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    /**
     * Denormalised sender email for display purposes.
     * Avoids a users table join every time messages are fetched.
     */
    @Column(name = "sender_email", nullable = false, updatable = false)
    private String senderEmail;

    // ── Content ───────────────────────────────────────────────────────────────

    /**
     * The message text. Stored as TEXT in PostgreSQL (no length limit).
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // ── Type ──────────────────────────────────────────────────────────────────

    /**
     * CHAT    — regular message sent by a participant
     * SYSTEM  — automated event (e.g. "Student joined the session")
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private MessageType type = MessageType.CHAT;

    // ── Timestamp ─────────────────────────────────────────────────────────────

    /**
     * Server-assigned UTC timestamp, set once on INSERT.
     * Never updated — messages are immutable once persisted.
     */
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    // ── Lifecycle callback ────────────────────────────────────────────────────

    @PrePersist
    private void prePersist() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (type == null) {
            type = MessageType.CHAT;
        }
    }
}