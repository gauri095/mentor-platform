package com.mentorplatform.mentor_platform.service;

import com.mentorplatform.mentor_platform.entity.Session;
import com.mentorplatform.mentor_platform.entity.SessionStatus;
import com.mentorplatform.mentor_platform.repository.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * All business logic for Session lifecycle.
 *
 * State machine enforced here — never mutate status outside this service.
 *
 *   create()  →  WAITING
 *   join()    →  ACTIVE   (WAITING → ACTIVE)
 *   end()     →  ENDED    (ACTIVE  → ENDED)
 */
@Service
@Transactional
public class SessionService {

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    // ── create ────────────────────────────────────────────────────────────────

    /**
     * Creates a new session in WAITING state.
     * Only one WAITING or ACTIVE session per mentor is allowed at a time.
     *
     * @param mentorId UUID of the authenticated mentor
     * @return persisted {@link Session}
     * @throws IllegalStateException if the mentor already has an open session
     */
    public Session createSession(String mentorId) {
        boolean hasOpenSession =
                sessionRepository.existsByMentorIdAndStatus(mentorId, SessionStatus.WAITING) ||
                        sessionRepository.existsByMentorIdAndStatus(mentorId, SessionStatus.ACTIVE);

        if (hasOpenSession) {
            throw new IllegalStateException(
                    "You already have an open session. End it before creating a new one.");
        }

        Session session = Session.builder()
                .mentorId(mentorId)
                .status(SessionStatus.WAITING)
                .joinLink(UUID.randomUUID().toString())   // unique invite token
                .build();

        return sessionRepository.save(session);
    }

    // ── join ──────────────────────────────────────────────────────────────────

    /**
     * Student joins a WAITING session via its join link.
     * Transitions status WAITING → ACTIVE and records startedAt.
     *
     * @param joinLink  token from the invite URL
     * @param studentId UUID of the authenticated student
     * @return updated {@link Session}
     * @throws IllegalArgumentException if the join link is invalid
     * @throws IllegalStateException    if the session is not joinable
     */
    public Session joinSession(String joinLink, String studentId) {
        Session session = sessionRepository.findByJoinLink(joinLink)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid session link."));

        if (!session.isWaiting()) {
            throw new IllegalStateException(
                    "This session is no longer available to join.");
        }

        session.setStudentId(studentId);
        session.setStatus(SessionStatus.ACTIVE);
        session.setStartedAt(Instant.now());

        return sessionRepository.save(session);
    }

    // ── end ───────────────────────────────────────────────────────────────────

    /**
     * Mentor ends an ACTIVE session.
     * Transitions status ACTIVE → ENDED and records endedAt.
     *
     * @param sessionId UUID of the session to end
     * @param mentorId  UUID of the requesting mentor (ownership check)
     * @return updated {@link Session}
     * @throws IllegalArgumentException if the session does not exist
     * @throws SecurityException        if the caller does not own the session
     * @throws IllegalStateException    if the session is already ended
     */
    public Session endSession(String sessionId, String mentorId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Session not found: " + sessionId));

        if (!session.getMentorId().equals(mentorId)) {
            throw new SecurityException("You do not own this session.");
        }

        if (session.isEnded()) {
            throw new IllegalStateException("Session is already ended.");
        }

        session.setStatus(SessionStatus.ENDED);
        session.setEndedAt(Instant.now());

        return sessionRepository.save(session);
    }

    // ── queries ───────────────────────────────────────────────────────────────

    /**
     * Fetches a single session by ID.
     * Used by the WebSocket layer and the REST GET endpoint.
     *
     * @throws IllegalArgumentException if not found
     */
    @Transactional(readOnly = true)
    public Session getSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Session not found: " + sessionId));
    }

    /** Returns all sessions for a given mentor, newest first. */
    @Transactional(readOnly = true)
    public List<Session> getMentorSessions(String mentorId) {
        return sessionRepository.findByMentorIdOrderByCreatedAtDesc(mentorId);
    }

    /** Returns all sessions for a given student, newest first. */
    @Transactional(readOnly = true)
    public List<Session> getStudentSessions(String studentId) {
        return sessionRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }
}