package com.lawforyou.document.repository;

import com.lawforyou.document.model.OutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

/**
 * MongoDB repository for the transactional outbox.
 */
public interface OutboxEventRepository extends MongoRepository<OutboxEvent, String> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(String status);

    @Query("{ 'status': 'FAILED', 'retryCount': { $lt: ?0 } }")
    List<OutboxEvent> findRetryableFailedEvents(int maxRetries);
}

