package com.mentorplatform.mentor_platform.controller;

import com.mentorplatform.mentor_platform.dto.SignalMessage;
import com.mentorplatform.mentor_platform.entity.Session;
import com.mentorplatform.mentor_platform.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * WebRTC signaling relay over STOMP WebSocket.
 *
 * The backend is a pure signal relay — it never processes SDP or ICE
 * content. Its only job is to route each signal to the correct peer
 * via their private signaling topic.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  Client A sends to          Server relays to                        │
 * │  /app/signal         →      /topic/signal/{targetId}/{sessionId}    │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Why a per-user topic instead of the shared session topic:
 *   WebRTC signals are strictly point-to-point. Sending an SDP offer to
 *   the shared /topic/session/{id} would echo it back to the sender,
 *   causing the browser to process its own offer. The private topic
 *   /topic/signal/{targetId}/{sessionId} ensures only the intended
 *   recipient receives each signal.
 *
 * ── Full WebRTC negotiation flow ──────────────────────────────────────
 *
 *   Mentor (caller)                   Student (callee)
 *       │                                   │
 *       │── createOffer() ──────────────────┤
 *       │   setLocalDescription(offer)      │
 *       │── STOMP /app/signal (OFFER) ──────┤
 *       │                                   │── setRemoteDescription(offer)
 *       │                                   │   createAnswer()
 *       │                                   │   setLocalDescription(answer)
 *       │◄─ STOMP /app/signal (ANSWER) ─────┤
 *       │   setRemoteDescription(answer)    │
 *       │                                   │
 *       │◄──── ICE_CANDIDATE (both ways) ───┤
 *       │──── ICE_CANDIDATE (both ways) ───►│
 *       │                                   │
 *       │◄══════ P2P video/audio ══════════►│
 *
 * ── Frontend usage (@stomp/stompjs) ──────────────────────────────────
 *
 *   // 1. Subscribe to YOUR private signal topic on connect
 *   client.subscribe(`/topic/signal/${myUserId}/${sessionId}`, msg => {
 *     const signal = JSON.parse(msg.body);
 *     handleSignal(signal);  // see SessionRoom.jsx
 *   });
 *
 *   // 2. Send an SDP offer to the remote peer
 *   client.publish({
 *     destination: '/app/signal',
 *     body: JSON.stringify({
 *       type:      'OFFER',
 *       sessionId: session.id,
 *       senderId:  myUserId,
 *       targetId:  remoteUserId,
 *       sdp:       peerConnection.localDescription.sdp,
 *     }),
 *   });
 *
 *   // 3. Send an ICE candidate
 *   client.publish({
 *     destination: '/app/signal',
 *     body: JSON.stringify({
 *       type:          'ICE_CANDIDATE',
 *       sessionId:     session.id,
 *       senderId:      myUserId,
 *       targetId:      remoteUserId,
 *       candidate:     event.candidate.candidate,
 *       sdpMid:        event.candidate.sdpMid,
 *       sdpMLineIndex: event.candidate.sdpMLineIndex,
 *     }),
 *   });
 */
@Controller
public class SignalingController {

    private static final Logger log = LoggerFactory.getLogger(SignalingController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionService        sessionService;

    public SignalingController(SimpMessagingTemplate messagingTemplate,
                               SessionService        sessionService) {
        this.messagingTemplate = messagingTemplate;
        this.sessionService    = sessionService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // /app/signal  →  relay to /topic/signal/{targetId}/{sessionId}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Receives any WebRTC signal (OFFER, ANSWER, ICE_CANDIDATE) and
     * relays it directly to the target peer's private topic.
     *
     * Guards:
     *   - Session must exist and be ACTIVE
     *   - Signal type must be one of the three valid types
     *   - targetId must be present
     *
     * @param signal the incoming signaling payload
     */
    @MessageMapping("/signal")
    public void relaySignal(@Payload SignalMessage signal) {

        // ── Guard: validate session is active ─────────────────────────────
        if (!isSessionActive(signal.sessionId())) {
            log.warn("relaySignal: session {} is not active — dropping {} signal",
                    signal.sessionId(), signal.type());
            return;
        }

        // ── Guard: validate signal type ───────────────────────────────────
        if (!isValidType(signal.type())) {
            log.warn("relaySignal: unknown signal type '{}' — dropping",
                    signal.type());
            return;
        }

        // ── Guard: targetId must be present ───────────────────────────────
        if (signal.targetId() == null || signal.targetId().isBlank()) {
            log.warn("relaySignal: missing targetId — dropping {} signal",
                    signal.type());
            return;
        }

        log.debug("relaySignal: {} | session={} | sender={} → target={}",
                signal.type(), signal.sessionId(),
                signal.senderId(), signal.targetId());

        // ── Relay to the target peer's private topic ───────────────────────
        // Topic: /topic/signal/{targetId}/{sessionId}
        // Only the intended recipient is subscribed to this topic.
        String destination = "/topic/signal/"
                + signal.targetId() + "/"
                + signal.sessionId();

        messagingTemplate.convertAndSend(destination, signal);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isSessionActive(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return false;
        try {
            Session session = sessionService.getSession(sessionId);
            return session.isActive();
        } catch (IllegalArgumentException e) {
            log.warn("relaySignal: session {} not found", sessionId);
            return false;
        }
    }

    private boolean isValidType(String type) {
        return "OFFER".equals(type)
                || "ANSWER".equals(type)
                || "ICE_CANDIDATE".equals(type);
    }
}