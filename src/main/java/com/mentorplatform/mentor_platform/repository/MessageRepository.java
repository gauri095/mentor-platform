package com.mentorplatform.mentor_platform.repository;

import com.mentorplatform.mentor_platform.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    /**
     * Full history — oldest → newest.
     * Used by getHistory() for short sessions.
     */
    List<Message> findBySessionIdOrderByTimestampAsc(String sessionId);

    /**
     * Pageable query — used by getLastN() to fetch newest N messages.
     * Sort direction is controlled by the Pageable passed in.
     * e.g. PageRequest.of(0, 50, Sort.by(DESC, "timestamp"))
     */
    List<Message> findBySessionId(String sessionId, Pageable pageable);

    /**
     * Fixed top-50 shorthand — used by getRecentMessages().
     */
    List<Message> findTop50BySessionIdOrderByTimestampDesc(String sessionId);

    /**
     * Cleanup — called when a session is permanently deleted.
     */
    void deleteBySessionId(String sessionId);
}