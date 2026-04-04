package com.lawforyou.user.kafka;

import com.lawforyou.user.entity.Role;
import com.lawforyou.user.entity.User;
import com.lawforyou.user.event.UserCreatedEvent;
import com.lawforyou.user.event.UserUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Publishes user domain events to Kafka.
 * Message key = userId (ensures ordering per user within the partition).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventProducer {

    private static final String TOPIC_USER_EVENTS = "user-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserCreated(User user) {
        var event = new UserCreatedEvent(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
                Instant.now()
        );
        kafkaTemplate.send(TOPIC_USER_EVENTS, user.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserCreatedEvent for user {}: {}",
                                user.getId(), ex.getMessage());
                    } else {
                        log.debug("Published UserCreatedEvent for user {}", user.getId());
                    }
                });
    }

    public void publishUserUpdated(User user) {
        var event = new UserUpdatedEvent(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getEmail(),
                user.isActive(),
                Instant.now()
        );
        kafkaTemplate.send(TOPIC_USER_EVENTS, user.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserUpdatedEvent for user {}: {}",
                                user.getId(), ex.getMessage());
                    } else {
                        log.debug("Published UserUpdatedEvent for user {}", user.getId());
                    }
                });
    }
}

