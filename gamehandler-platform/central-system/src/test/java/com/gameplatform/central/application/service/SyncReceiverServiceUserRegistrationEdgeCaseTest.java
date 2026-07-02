package com.gameplatform.central.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import com.gameplatform.shared.dto.UserRegisteredEventDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncReceiverServiceUserRegistrationEdgeCaseTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private LocalServerRegistryPort localServerRegistryPort;

    @Mock
    private RegisterUserFromSyncUseCase registerUserFromSyncUseCase;

    private SyncReceiverService service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new SyncReceiverService(
                processedEventRepository, statisticsRepository, localServerRegistryPort,
                registerUserFromSyncUseCase, objectMapper);
    }

    private OutboxEventDto userRegisteredEvent(String eventId, String payloadJson) {
        return new OutboxEventDto(eventId, "USER_REGISTERED", payloadJson, Instant.now());
    }

    @Test
    @DisplayName("USER_REGISTERED with malformed JSON payload is isolated and marked processed without calling registerFromSync")
    void malformedPayloadIsMarkedProcessed() {
        SyncPayloadDto payload = new SyncPayloadDto("b-1", List.of(userRegisteredEvent("evt-bad", "{not json")));
        when(processedEventRepository.existsByEventId("evt-bad")).thenReturn(false);

        assertThatCode(() -> service.receiveSyncPayload(payload)).doesNotThrowAnyException();

        verify(registerUserFromSyncUseCase, never()).registerFromSync(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("USER_REGISTERED where registerFromSync throws is caught and the event is still marked processed")
    void registerFromSyncFailureIsCaught() throws Exception {
        UserRegisteredEventDto dto = new UserRegisteredEventDto(
                "u-1", "alice", "a@e.com", "h", List.of("USER"), Instant.now());
        SyncPayloadDto payload = new SyncPayloadDto("b-1",
                List.of(userRegisteredEvent("evt-1", objectMapper.writeValueAsString(dto))));

        when(processedEventRepository.existsByEventId("evt-1")).thenReturn(false);
        doThrow(new IllegalArgumentException("bad data")).when(registerUserFromSyncUseCase).registerFromSync(any());

        assertThatCode(() -> service.receiveSyncPayload(payload)).doesNotThrowAnyException();

        verify(registerUserFromSyncUseCase).registerFromSync(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("unknown event type is marked processed without invoking registerFromSync")
    void unknownEventTypeMarkedProcessed() {
        SyncPayloadDto payload = new SyncPayloadDto("b-1",
                List.of(new OutboxEventDto("evt-1", "SOME_OTHER_EVENT", "{}", Instant.now())));
        when(processedEventRepository.existsByEventId("evt-1")).thenReturn(false);

        assertThatCode(() -> service.receiveSyncPayload(payload)).doesNotThrowAnyException();

        verify(registerUserFromSyncUseCase, never()).registerFromSync(any());
    }

    @Test
    @DisplayName("duplicate USER_REGISTERED within the same batch is skipped on the second occurrence")
    void duplicateWithinBatchSkipped() throws Exception {
        UserRegisteredEventDto dto = new UserRegisteredEventDto(
                "u-1", "alice", "a@e.com", "h", List.of("USER"), Instant.now());
        String json = objectMapper.writeValueAsString(dto);
        SyncPayloadDto payload = new SyncPayloadDto("b-1",
                List.of(userRegisteredEvent("evt-dup", json), userRegisteredEvent("evt-dup", json)));

        when(processedEventRepository.existsByEventId("evt-dup")).thenReturn(false, true);

        service.receiveSyncPayload(payload);

        verify(registerUserFromSyncUseCase, times(1)).registerFromSync(any());
    }

    @Test
    @DisplayName("USER_REGISTERED with an empty/null events payload is a no-op")
    void emptyPayloadIsNoOp() {
        service.receiveSyncPayload(new SyncPayloadDto("b-1", List.of()));
        service.receiveSyncPayload(null);

        verify(registerUserFromSyncUseCase, never()).registerFromSync(any());
        verify(processedEventRepository, never()).save(any());
    }
}
