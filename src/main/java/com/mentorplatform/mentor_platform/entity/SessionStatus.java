package com.mentorplatform.mentor_platform.entity;

/**
 * Lifecycle states for a mentoring session.
 *
 *  WAITING  → created by mentor, waiting for student to join
 *  ACTIVE   → both participants connected, session in progress
 *  ENDED    → explicitly ended by mentor or by timeout
 */
public enum SessionStatus {
    WAITING,
    ACTIVE,
    ENDED
}