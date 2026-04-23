package com.lawforyou.document.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawforyou.document.kafka.event.DocumentUploadedEvent;
import com.lawforyou.document.model.DocumentCategory;
import com.lawforyou.document.model.OutboxEvent;
import com.lawforyou.document.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock private OutboxEventRepository         outboxEventRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private ObjectMapper                  objectMapper;
    @InjectMocks private OutboxRelay            outboxRelay;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CASE   = UUID.randomUUID();

    @Test
    void relay_pendingEvent_publishedAndMarkedProcessed() throws Exception {
        DocumentUploadedEvent event = new DocumentUploadedEvent(
                "doc-1", TENANT, CASE, "file.pdf", DocumentCategory.CONTRACT, "user-1", 1, Instant.now());
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(event);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id("evt-1")
                .aggregateId("doc-1")
                .eventType(DocumentUploadedEvent.class.getName())
                .payload(json)
                .status("PENDING")
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        when(outboxEventRepository.findRetryableFailedEvents(anyInt())).thenReturn(List.of());
        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(outboxEvent));
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(event);

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        when(future.get()).thenReturn(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        outboxRelay.relay();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, atLeastOnce()).save(captor.capture());
        OutboxEvent saved = captor.getAllValues().stream()
                .filter(e -> "PROCESSED".equals(e.getStatus())).findFirst().orElseThrow();
        assertThat(saved.getProcessedAt()).isNotNull();
    }

    @Test
    void relay_kafkaFailure_incrementsRetryCount() throws Exception {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id("evt-2")
                .aggregateId("doc-2")
                .eventType(DocumentUploadedEvent.class.getName())
                .payload("{}")
                .status("PENDING")
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        when(outboxEventRepository.findRetryableFailedEvents(anyInt())).thenReturn(List.of());
        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(outboxEvent));
        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenThrow(new RuntimeException("kafka down"));

        outboxRelay.relay();

        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        verify(outboxEventRepository).save(outboxEvent);
    }

    @Test
    void relay_maxRetriesReached_markedFailed() throws Exception {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id("evt-3")
                .aggregateId("doc-3")
                .eventType(DocumentUploadedEvent.class.getName())
                .payload("{}")
                .status("PENDING")
                .retryCount(4)   // one more → equals MAX_RETRIES (5)
                .createdAt(Instant.now())
                .build();

        when(outboxEventRepository.findRetryableFailedEvents(anyInt())).thenReturn(List.of());
        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(outboxEvent));
        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenThrow(new RuntimeException("still down"));

        outboxRelay.relay();

        assertThat(outboxEvent.getStatus()).isEqualTo("FAILED");
        assertThat(outboxEvent.getRetryCount()).isEqualTo(5);
    }
}

