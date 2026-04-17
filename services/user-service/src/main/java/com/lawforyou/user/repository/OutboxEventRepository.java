package com.lawforyou.user.repository;


import com.lawforyou.user.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(String status);

    /**
     * Finds FAILED events that still have retries remaining.
     * Used by the relay to re-queue events that were previously marked FAILED
     * but have since been reset (retry_count < maxRetries).
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'FAILED' AND o.retryCount < :maxRetries ORDER BY o.createdAt ASC LIMIT 100")
    List<OutboxEvent> findRetryableFailedEvents(@Param("maxRetries") int maxRetries);
}
