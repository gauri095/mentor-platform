package com.mentorplatform.mentor_platform.repository;

import com.mentorplatform.mentor_platform.entity.Session;
import com.mentorplatform.mentor_platform.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {

    /** Used by the join flow — student clicks the invite link. */
    Optional<Session> findByJoinLink(String joinLink);

    /** Mentor's session history / dashboard. */
    List<Session> findByMentorIdOrderByCreatedAtDesc(String mentorId);

    /** Student's session history. */
    List<Session> findByStudentIdOrderByCreatedAtDesc(String studentId);

    /** Used by SessionService to prevent a mentor from having two ACTIVE sessions. */
    boolean existsByMentorIdAndStatus(String mentorId, SessionStatus status);
}