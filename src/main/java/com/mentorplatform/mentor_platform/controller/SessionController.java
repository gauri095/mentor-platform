package com.mentorplatform.mentor_platform.controller;

import com.mentorplatform.mentor_platform.dto.JoinSessionRequest;
import com.mentorplatform.mentor_platform.dto.SessionResponse;
import com.mentorplatform.mentor_platform.entity.Session;
import com.mentorplatform.mentor_platform.entity.User;
import com.mentorplatform.mentor_platform.service.SessionService;
import com.mentorplatform.mentor_platform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for Session lifecycle.
 *
 *  POST   /api/sessions/create         → MENTOR only   → 201
 *  POST   /api/sessions/join           → STUDENT only  → 200
 *  POST   /api/sessions/{id}/end       → MENTOR only   → 200
 *  GET    /api/sessions/{id}           → any auth user → 200
 *  GET    /api/sessions/my             → any auth user → 200
 *
 * Role enforcement is layered:
 *   1. SecurityConfig locks POST /api/sessions/create to ROLE_MENTOR at the URL level.
 *   2. @PreAuthorize on each method gives fine-grained, self-documenting control.
 *   3. SessionService re-validates ownership (mentorId check) as a defence-in-depth guard.
 *
 * The authenticated user's UUID is resolved once via UserService to avoid
 * trusting anything from the request body for identity.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final UserService    userService;

    /** Injected from application.properties: app.base-url */
    @Value("${app.base-url:http://localhost:3000}")
    private String appBaseUrl;

    public SessionController(SessionService sessionService,
                             UserService    userService) {
        this.sessionService = sessionService;
        this.userService    = userService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/sessions/create  — MENTOR only
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new session and returns its join link.
     *
     * The mentor's UUID is resolved from the JWT — nothing is accepted
     * from the request body for identity, preventing spoofing.
     *
     * Possible errors:
     *   401 — no/invalid JWT
     *   403 — caller is a STUDENT
     *   409 — mentor already has an open session
     *
     * @param principal injected by Spring Security from the validated JWT
     * @return 201 Created + {@link SessionResponse} with joinLink
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SessionResponse> createSession(
            @AuthenticationPrincipal UserDetails principal) {

        String mentorId = resolveUserId(principal);
        Session session = sessionService.createSession(mentorId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SessionResponse.from(session, appBaseUrl));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/sessions/join  — STUDENT only
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Student joins a WAITING session via the join link token.
     *
     * Possible errors:
     *   400 — joinLink blank
     *   401 — no/invalid JWT
     *   403 — caller is a MENTOR
     *   404 — join link not found (surfaced as IllegalArgumentException → 404)
     *   409 — session is no longer WAITING
     *
     * @param req       validated body containing the join link token
     * @param principal injected by Spring Security
     * @return 200 OK + updated {@link SessionResponse}
     */
    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SessionResponse> joinSession(
            @Valid @RequestBody JoinSessionRequest req,
            @AuthenticationPrincipal UserDetails principal) {

        String studentId = resolveUserId(principal);
        Session session  = sessionService.joinSession(req.joinLink(), studentId);

        return ResponseEntity.ok(SessionResponse.from(session, appBaseUrl));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/sessions/{id}/end  — MENTOR only
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ends an ACTIVE or WAITING session.
     * SessionService enforces that the caller is the session owner.
     *
     * Possible errors:
     *   401 — no/invalid JWT
     *   403 — caller is a STUDENT
     *   404 — session not found
     *   403 — caller is not the session owner (SecurityException → 403)
     *   409 — session is already ENDED
     *
     * @param sessionId path variable — UUID of the session to end
     * @param principal injected by Spring Security
     * @return 200 OK + updated {@link SessionResponse}
     */
    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SessionResponse> endSession(
            @PathVariable("id") String sessionId,
            @AuthenticationPrincipal UserDetails principal) {

        String mentorId = resolveUserId(principal);
        Session session = sessionService.endSession(sessionId, mentorId);

        return ResponseEntity.ok(SessionResponse.from(session, appBaseUrl));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/sessions/{id}  — any authenticated user
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches a single session by its UUID.
     * Used by the frontend when restoring session state after a page refresh.
     *
     * Possible errors:
     *   401 — no/invalid JWT
     *   404 — session not found
     *
     * @param sessionId path variable — UUID of the session
     * @return 200 OK + {@link SessionResponse}
     */
    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSession(
            @PathVariable("id") String sessionId) {

        Session session = sessionService.getSession(sessionId);
        return ResponseEntity.ok(SessionResponse.from(session, appBaseUrl));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/sessions/my  — any authenticated user
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the session history for the calling user.
     * Mentors get their created sessions; students get sessions they joined.
     * Role is read from the JWT claim — no extra DB lookup needed.
     *
     * @param principal injected by Spring Security
     * @return 200 OK + list of {@link SessionResponse}, newest first
     */
    @GetMapping("/my")
    public ResponseEntity<List<SessionResponse>> getMySessions(
            @AuthenticationPrincipal UserDetails principal) {

        String userId = resolveUserId(principal);
        boolean isMentor = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MENTOR"));

        List<Session> sessions = isMentor
                ? sessionService.getMentorSessions(userId)
                : sessionService.getStudentSessions(userId);

        List<SessionResponse> body = sessions.stream()
                .map(s -> SessionResponse.from(s, appBaseUrl))
                .toList();

        return ResponseEntity.ok(body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves the internal UUID of the authenticated user from their email.
     * The JWT subject is the email (set in UserService.loadUserByUsername).
     * We look up the UUID here so the service layer never has to trust
     * anything from the HTTP request for identity.
     */
    private String resolveUserId(UserDetails principal) {
        User user = userService.getUserByEmail(principal.getUsername());
        return user.getId();
    }
}