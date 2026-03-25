package com.mentorplatform.mentor_platform.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one 1-on-1 mentoring session.
 *
 * Lifecycle:
 *   WAITING ──(student joins)──► ACTIVE ──(mentor ends)──► ENDED
 *
 * mentorId and studentId are foreign keys to the users table stored as
 * plain Strings (UUID) to avoid a bidirectional JPA relationship —
 * sessions are fetched standalone in the WebSocket layer.
 *
 * DB table: sessions
 * ┌─────────────┬──────────────────────────────────────────────┐
 * │ id          │ UUID primary key, auto-generated             │
 * │ mentor_id   │ UUID FK → users.id  (NOT NULL)              │
 * │ student_id  │ UUID FK → users.id  (nullable until joined)  │
 * │ status      │ WAITING | ACTIVE | ENDED                     │
 * │ join_link   │ unique shareable token for session invite    │
 * │ created_at  │ set on INSERT                                │
 * │ started_at  │ set when student joins                       │
 * │ ended_at    │ set when session ends                        │
 * └─────────────┴──────────────────────────────────────────────┘
 */
@Entity
@Table(name = "sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {

    // ── Primary key ───────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    // ── Participants ──────────────────────────────────────────────────────────

    /**
     * UUID of the mentor who created this session.
     * Maps to users.id — stored as a String to avoid JPA join overhead
     * in the real-time WebSocket layer.
     */
    @Column(name = "mentor_id", nullable = false, updatable = false)
    private String mentorId;

    /**
     * UUID of the student who joined.
     * Null until the student accepts the invite and joins.
     */
    @Column(name = "student_id")
    private String studentId;

    // ── Status ────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private SessionStatus status = SessionStatus.WAITING;

    // ── Join link ─────────────────────────────────────────────────────────────

    /**
     * Unique token appended to the invite URL.
     * e.g. https://mentor-app.vercel.app/join/{joinLink}
     * Generated in SessionService using UUID.randomUUID().
     */
    @Column(name = "join_link", unique = true, nullable = false, updatable = false)
    private String joinLink;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Set to now when the student joins and status transitions to ACTIVE. */
    @Column(name = "started_at")
    private Instant startedAt;

    /** Set to now when the mentor ends the session. */
    @Column(name = "ended_at")
    private Instant endedAt;

    // ── Lifecycle callbacks ───────────────────────────────────────────────────

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = SessionStatus.WAITING;
        }
    }

    // ── Convenience methods (used by SessionService) ──────────────────────────

    /** Returns true when the session can still accept participants or messages. */
    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    /** Returns true when no student has joined yet. */
    public boolean isWaiting() {
        return status == SessionStatus.WAITING;
    }

    /** Returns true when the session is over. */
    public boolean isEnded() {
        return status == SessionStatus.ENDED;
    }
}