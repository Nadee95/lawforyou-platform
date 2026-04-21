package com.lawforyou.cases.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.cases.entity.OutboxEvent;
import com.lawforyou.cases.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls {@code outbox_events} for PENDING events and publishes them to Kafka.
 * Mirrors user-service OutboxRelay exactly — only TOPIC differs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final String TOPIC            = "case-events";
    private static final int    MAX_RETRIES      = 5;
    private static final String STATUS_PENDING   = "PENDING";
    private static final String STATUS_PROCESSED = "PROCESSED";
    private static final String STATUS_FAILED    = "FAILED";

    private final OutboxEventRepository         outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:2000}")
    @Transactional
    public void relay() {
        // Re-queue FAILED events that still have retries remaining
        outboxEventRepository.findRetryableFailedEvents(MAX_RETRIES)
                .forEach(e -> {
                    e.setStatus(STATUS_PENDING);
                    log.info("OutboxRelay: re-queuing FAILED event {} (retryCount={})", e.getId(), e.getRetryCount());
                });

        List<OutboxEvent> pending =
                outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(STATUS_PENDING);

        if (pending.isEmpty()) return;

        log.debug("OutboxRelay: processing {} pending event(s)", pending.size());

        for (OutboxEvent event : pending) {
            try {
                Object payload = objectMapper.readValue(
                        event.getPayload(),
                        Class.forName(event.getEventType()));

                kafkaTemplate.send(TOPIC, event.getAggregateId(), payload).get();

                event.setStatus(STATUS_PROCESSED);
                event.setProcessedAt(Instant.now());
                log.debug("OutboxRelay: published {} [{}]", event.getEventType(), event.getId());

            } catch (Exception ex) {
                int retries = event.getRetryCount() + 1;
                event.setRetryCount(retries);

                if (retries >= MAX_RETRIES) {
                    event.setStatus(STATUS_FAILED);
                    log.error("OutboxRelay: FAILED event {} after {} retries — cause: {}",
                            event.getId(), retries,
                            ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                } else {
                    log.warn("OutboxRelay: retry {}/{} for event {} — cause: {}",
                            retries, MAX_RETRIES, event.getId(),
                            ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                }
            }
        }
    }
}

