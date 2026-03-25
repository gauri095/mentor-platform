package com.mentorplatform.mentor_platform.config;

import com.mentorplatform.mentor_platform.dto.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to raw Spring WebSocket lifecycle events and notifies
 * the session participants when a peer disconnects.
 *
 * Why this is needed:
 *   The STOMP client's onDisconnect fires only on the disconnecting
 *   side. The OTHER participant has no way to know their peer left
 *   unless the server detects the disconnect and broadcasts it.
 *
 * How it works:
 *   1. On CONNECT  — stores { sessionSimpId → (sessionId, userEmail) }
 *   2. On DISCONNECT — looks up the session, broadcasts a SYSTEM
 *      message to /topic/session/{sessionId} so the other participant
 *      sees "mentor left the session" in their chat pane.
 *
 * The sessionId and userEmail are stored in the STOMP session
 * attributes at connect time by the frontend:
 *
 *   // In SessionRoom.jsx — connectHeaders
 *   connectHeaders: {
 *     Authorization:  'Bearer ' + token,
 *     'session-id':   session.id,
 *     'user-email':   user.email,
 *     'user-role':    user.role,
 *   }
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    // simpSessionId (Spring's internal WS session) → connection metadata
    private final Map<String, ConnectionMeta> activeSessions = new ConcurrentHashMap<>();

    public WebSocketEventListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /* ── On connect — store metadata keyed by Spring's WS session ID ── */

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String simpSessionId = accessor.getSessionId();
        String sessionId     = accessor.getFirstNativeHeader("session-id");
        String userEmail     = accessor.getFirstNativeHeader("user-email");
        String userRole      = accessor.getFirstNativeHeader("user-role");

        if (simpSessionId != null && sessionId != null && userEmail != null) {
            activeSessions.put(simpSessionId,
                    new ConnectionMeta(sessionId, userEmail, userRole));
            log.debug("WS connect: simpId={} session={} user={}",
                    simpSessionId, sessionId, userEmail);
        }
    }

    /* ── On disconnect — broadcast system message to session topic ──── */

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor  = StompHeaderAccessor.wrap(event.getMessage());
        String simpSessionId          = accessor.getSessionId();
        ConnectionMeta meta           = activeSessions.remove(simpSessionId);

        if (meta == null) {
            // No metadata — connection was never fully established
            return;
        }

        log.info("WS disconnect: session={} user={}", meta.sessionId(), meta.userEmail());

        // Build a SYSTEM message so the other participant sees the notification
        String displayName = meta.userEmail().split("@")[0];
        String role        = meta.userRole() != null
                ? meta.userRole().charAt(0) + meta.userRole().substring(1).toLowerCase()
                : "Participant";

        ChatMessage disconnectMsg = new ChatMessage(
                meta.sessionId(),
                null,                                   // senderId — system event
                meta.userEmail(),
                role + " " + displayName + " left the session",
                ChatMessage.Type.SYSTEM,
                Instant.now()
        );

        messagingTemplate.convertAndSend(
                "/topic/session/" + meta.sessionId(),
                disconnectMsg
        );
    }

    /* ── Internal record ─────────────────────────────────────────────── */

    private record ConnectionMeta(
            String sessionId,
            String userEmail,
            String userRole
    ) {}
}