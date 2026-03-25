package com.mentorplatform.mentor_platform.controller;

import com.mentorplatform.mentor_platform.dto.ChatMessage;
import com.mentorplatform.mentor_platform.dto.CodeSyncMessage;
import com.mentorplatform.mentor_platform.entity.Session;
import com.mentorplatform.mentor_platform.service.MessageService;
import com.mentorplatform.mentor_platform.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Handles real-time WebSocket messages for the session room.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  Client → Server          Server → All session subscribers  │
 * │  /app/code          →     /topic/session/{id}               │
 * │  /app/chat          →     /topic/session/{id} + persisted   │
 * │  /app/session/join  →     /topic/session/{id}               │
 * └─────────────────────────────────────────────────────────────┘
 */
@Controller
public class CodeSyncController {

    private static final Logger log = LoggerFactory.getLogger(CodeSyncController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionService        sessionService;
    private final MessageService        messageService;

    public CodeSyncController(SimpMessagingTemplate messagingTemplate,
                              SessionService        sessionService,
                              MessageService        messageService) {
        this.messagingTemplate = messagingTemplate;
        this.sessionService    = sessionService;
        this.messageService    = messageService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /app/code  →  broadcast code update (last-write-wins, not persisted)
    // ─────────────────────────────────────────────────────────────────────────

    @MessageMapping("/code")
    public void syncCode(@Payload CodeSyncMessage message) {
        if (!isSessionActive(message.sessionId())) {
            log.warn("syncCode: session {} not active — dropping", message.sessionId());
            return;
        }
        log.debug("syncCode: session={} lang={} sender={}",
                message.sessionId(), message.language(), message.senderId());
        messagingTemplate.convertAndSend(
                "/topic/session/" + message.sessionId(), message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /app/chat  →  broadcast + persist
    // ─────────────────────────────────────────────────────────────────────────

    @MessageMapping("/chat")
    public void sendChat(@Payload ChatMessage message) {
        if (!isSessionActive(message.sessionId())) {
            log.warn("sendChat: session {} not active — dropping", message.sessionId());
            return;
        }
        // 1. Broadcast first — real-time delivery is never blocked by DB
        ChatMessage stamped = message.withTimestamp();
        messagingTemplate.convertAndSend(
                "/topic/session/" + message.sessionId(), stamped);

        // 2. Persist after broadcast
        try {
            messageService.save(stamped);
        } catch (Exception e) {
            log.error("sendChat: persist failed for session={}: {}",
                    message.sessionId(), e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /app/session/join  →  broadcast join event + persist
    // ─────────────────────────────────────────────────────────────────────────

    @MessageMapping("/session/join")
    public void participantJoined(@Payload ChatMessage message) {
        log.info("participantJoined: session={} user={}",
                message.sessionId(), message.senderEmail());
        ChatMessage stamped = message.withTimestamp();
       messagingTemplate.convertAndSend(
        "/topic/session/" + message.sessionId() + "/chat",
        stamped
);
        try {
            messageService.save(stamped);
        } catch (Exception e) {
            log.error("participantJoined: persist failed: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isSessionActive(String sessionId) {
        try {
            Session session = sessionService.getSession(sessionId);
            return session.isActive();
        } catch (IllegalArgumentException e) {
            log.warn("isSessionActive: session {} not found", sessionId);
            return false;
        }
    }
}