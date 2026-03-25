package com.mentorplatform.mentor_platform.service;

import com.mentorplatform.mentor_platform.dto.ChatMessage;
import com.mentorplatform.mentor_platform.entity.Message;
import com.mentorplatform.mentor_platform.entity.MessageType;
import com.mentorplatform.mentor_platform.repository.MessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * Persists and retrieves chat messages for a session.
 *
 * Called by CodeSyncController after broadcasting a chat message
 * so it is saved to the DB for history replay on rejoin.
 */
@Service
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Persists a chat message received via STOMP.
     * Called after the WebSocket broadcast so persistence never
     * blocks the real-time delivery.
     *
     * @param chatMessage the incoming STOMP payload
     * @return the persisted {@link Message} with DB-assigned id and timestamp
     */
    public Message save(ChatMessage chatMessage) {
        Message message = Message.builder()
                .sessionId(chatMessage.sessionId())
                .senderId(chatMessage.senderId())
                .senderEmail(chatMessage.senderEmail())
                .content(chatMessage.message())
                .type(toEntityType(chatMessage.type()))
                .build();
        return messageRepository.save(message);
    }

    // ── Retrieve ──────────────────────────────────────────────────────────────

    /**
     * Returns the full chat history for a session, oldest → newest.
     * Use when the session is short and history volume is manageable.
     *
     * @param sessionId UUID of the session
     * @return all messages ordered by timestamp ascending
     */
    @Transactional(readOnly = true)
    public List<Message> getHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    /**
     * Returns the last N messages for a session, ordered oldest → newest.
     *
     * Strategy:
     *   1. Fetch N messages newest → oldest  (DB uses the session_id index)
     *   2. Reverse in memory                 (no second DB round-trip)
     *
     * This is what the frontend calls on join:
     *   GET /api/messages/{sessionId}?limit=50
     *
     * @param sessionId UUID of the session
     * @param n         max number of messages to return
     * @return up to N messages ordered oldest → newest
     */
    @Transactional(readOnly = true)
    public List<Message> getLastN(String sessionId, int n) {
        // Clamp n to a safe maximum to prevent abuse
        int limit = Math.min(n, 200);

        List<Message> newest = messageRepository.findBySessionId(
                sessionId,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp"))
        );

        // Reverse so the list is oldest → newest for the frontend
        Collections.reverse(newest);
        return newest;
    }

    /**
     * Returns the 50 most recent messages for a session, newest → oldest.
     * Convenience wrapper around getLastN(sessionId, 50).
     */
    @Transactional(readOnly = true)
    public List<Message> getRecentMessages(String sessionId) {
        return getLastN(sessionId, 50);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private MessageType toEntityType(ChatMessage.Type type) {
        if (type == null) return MessageType.CHAT;
        return switch (type) {
            case SYSTEM -> MessageType.SYSTEM;
            default     -> MessageType.CHAT;
        };
    }
}