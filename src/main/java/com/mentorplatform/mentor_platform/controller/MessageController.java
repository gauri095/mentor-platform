package com.mentorplatform.mentor_platform.controller;

import com.mentorplatform.mentor_platform.entity.Message;
import com.mentorplatform.mentor_platform.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for chat message history.
 *
 *  GET /api/messages/{sessionId}          → full history, oldest → newest
 *  GET /api/messages/{sessionId}?limit=50 → last N messages, oldest → newest
 *
 * Called by the frontend on session join to populate the chat pane.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/messages/{sessionId}
    // GET /api/messages/{sessionId}?limit=50
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns chat history for a session.
     *
     * Without ?limit  → returns full history ordered oldest → newest
     * With ?limit=N   → returns last N messages ordered oldest → newest
     *
     * The frontend calls this on join:
     *   GET /api/messages/{sessionId}?limit=50
     *
     * Possible errors:
     *   401 — no/invalid JWT (handled by SecurityConfig)
     *   404 — session not found (returns empty list, not an error)
     *
     * @param sessionId UUID of the session
     * @param limit     optional max number of messages to return (default: all)
     * @return 200 OK + list of {@link Message} objects, oldest → newest
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<List<Message>> getHistory(
            @PathVariable String sessionId,
            @RequestParam(required = false) Integer limit) {

        List<Message> messages = (limit != null && limit > 0)
                ? messageService.getLastN(sessionId, limit)
                : messageService.getHistory(sessionId);

        return ResponseEntity.ok(messages);
    }
}