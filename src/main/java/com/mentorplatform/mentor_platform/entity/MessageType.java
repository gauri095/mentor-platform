package com.mentorplatform.mentor_platform.entity;

/**
 * Type of a persisted chat message.
 *
 * CHAT   — sent by a mentor or student during a session
 * SYSTEM — automated event message (join, leave, session ended)
 */
public enum MessageType {
    CHAT,
    SYSTEM
}