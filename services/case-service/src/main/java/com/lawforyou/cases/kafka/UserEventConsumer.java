package com.lawforyou.cases.kafka;

import com.lawforyou.cases.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link UserCreatedEvent} messages from the {@code user-events} topic.
 *
 * <p>Currently logs the event for observability. Future enhancements:
 * <ul>
 *   <li>Cache user metadata locally (avoid cross-service calls on case queries)</li>
 *   <li>Auto-create a default case for CLIENT users</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    @KafkaListener(
            topics   = "user-events",
            groupId  = "case-service-user-events",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserCreated(ConsumerRecord<String, UserCreatedEvent> record) {
        UserCreatedEvent event = record.value();

        if (event == null) {
            log.warn("UserEventConsumer: received null payload on user-events offset={}", record.offset());
            return;
        }

        log.info("UserEventConsumer: new user registered userId={} email={} tenant={} roles={}",
                event.userId(), event.email(), event.tenantId(), event.roles());

        // TODO Phase 2: persist a lightweight user-shadow record for fast case-client joins
    }
}

