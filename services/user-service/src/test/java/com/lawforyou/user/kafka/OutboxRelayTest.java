package com.lawforyou.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.user.entity.OutboxEvent;
import com.lawforyou.user.event.UserCreatedEvent;
import com.lawforyou.user.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OutboxRelay}.
 * Verifies PENDING event processing, status transitions, and retry logic.
 *
 * <p>Key rule: {@code outboxEvent.payload} must be JSON of the class named
 * by {@code outboxEvent.eventType}. The relay calls
 * {@code objectMapper.readValue(payload, Class.forName(eventType))},
 * so payload and eventType must always match.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OutboxRelay outboxRelay;

    // Real ObjectMapper — objectMapper.readValue() must actually deserialize the payload.
    // A @Mock would return null, causing kafkaTemplate.send(..., null).
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void injectObjectMapper() throws Exception {
        var field = OutboxRelay.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(outboxRelay, objectMapper);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a PENDING OutboxEvent with a real serialized {@link UserCreatedEvent} as payload.
     * eventType = UserCreatedEvent.class.getName() so the relay can deserialize it correctly.
     */
    private OutboxEvent pendingUserCreatedEvent(int retryCount) throws Exception {
        UserCreatedEvent event = new UserCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "johndoe",
                "john@acme.com",
                "John",
                "Doe",
                Set.of("CLIENT"),
                Instant.now()
        );
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("User")
                .aggregateId(event.userId().toString())
                .eventType(UserCreatedEvent.class.getName())  // must match payload type
                .payload(objectMapper.writeValueAsString(event))
                .status("PENDING")
                .retryCount(retryCount)
                .createdAt(Instant.now())
                .build();
    }

    private OutboxEvent pendingUserCreatedEvent() throws Exception {
        return pendingUserCreatedEvent(0);
    }

    @SuppressWarnings("unchecked")
    private void mockKafkaSuccess() {
        CompletableFuture<SendResult<String, Object>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);
    }

    private void mockKafkaFailure() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void relay_noPendingEvents_shouldDoNothing() {
        when(outboxEventRepository.findRetryableFailedEvents(anyInt())).thenReturn(List.of());
        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of());

        outboxRelay.relay();

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void relay_pendingEvent_shouldPublishAndMarkProcessed() throws Exception {
        OutboxEvent outboxEvent = pendingUserCreatedEvent();

        when(outboxEventRepository.findRetryableFailedEvents(anyInt())).thenReturn(List.of());
        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(outboxEvent));
        mockKafkaSuccess();

        outboxRelay.relay();

        assertThat(outboxEvent.getStatus()).isEqualTo("PROCESSED");
        assertThat(outboxEvent.getProcessedAt()).isNotNull();
        assertThat(outboxEvent.getRetryCount()).isZero();
        verify(kafkaTemplate).send(eq("user-events"), anyString(), any(UserCreatedEvent.class));
    }

    @Test
    void relay_kafkaFailure_shouldIncrementRetryCount() throws Exception {
        OutboxEvent outboxEvent = pendingUserCreatedEvent(); // retryCount = 0

        when(outboxEventRepository.findRetryableFailedEvents(anyInt())).thenReturn(List.of());
        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(outboxEvent));
        mockKafkaFailure();

        outboxRelay.relay();

        // retryCount incremented; status stays PENDING because 1 < MAX_RETRIES (5)
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void relay_maxRetriesReached_shouldMarkFailed() throws Exception {
        OutboxEvent outboxEvent = pendingUserCreatedEvent(4); // next failure → retryCount = 5 = MAX_RETRIES

        when(outboxEventRepository.findRetryableFailedEvents(anyInt())).thenReturn(List.of());
        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(outboxEvent));
        mockKafkaFailure();

        outboxRelay.relay();

        assertThat(outboxEvent.getRetryCount()).isEqualTo(5);
        assertThat(outboxEvent.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void relay_retryableFailedEvent_shouldRequeueToPending() throws Exception {
        OutboxEvent failedEvent = pendingUserCreatedEvent(2); // retryCount=2 < MAX_RETRIES(5)
        failedEvent.setStatus("FAILED");

        when(outboxEventRepository.findRetryableFailedEvents(5)).thenReturn(List.of(failedEvent));
        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of());

        outboxRelay.relay();

        assertThat(failedEvent.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void relay_multiplePendingEvents_shouldPublishAll() throws Exception {
        OutboxEvent outbox1 = pendingUserCreatedEvent();
        OutboxEvent outbox2 = pendingUserCreatedEvent();

        when(outboxEventRepository.findRetryableFailedEvents(anyInt())).thenReturn(List.of());
        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(outbox1, outbox2));
        mockKafkaSuccess();

        outboxRelay.relay();

        assertThat(outbox1.getStatus()).isEqualTo("PROCESSED");
        assertThat(outbox2.getStatus()).isEqualTo("PROCESSED");
        verify(kafkaTemplate, times(2)).send(eq("user-events"), anyString(), any(UserCreatedEvent.class));
    }
}
