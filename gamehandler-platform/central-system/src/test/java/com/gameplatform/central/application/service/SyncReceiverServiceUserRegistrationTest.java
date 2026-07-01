package com.gameplatform.central.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class SyncReceiverServiceUserRegistrationTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private LocalServerRegistryPort localServerRegistryPort;

    @Mock
    private RegisterUserFromSyncUseCase registerUserFromSyncUseCase;

    private SyncReceiverService syncReceiverService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        syncReceiverService = new SyncReceiverService(
                processedEventRepository,
                statisticsRepository,
                localServerRegistryPort,
                registerUserFromSyncUseCase,
                objectMapper
        );
    }

    @Test
    void shouldDelegateUserRegisteredEventToUseCaseAndRecordProcessedEvent() throws Exception {
        UserRegisteredEventDto dto = new UserRegisteredEventDto(
                "user-123", "alice", "alice@example.com", "hashed_pw", List.of("USER"), Instant.now()
        );
        String payload = objectMapper.writeValueAsString(dto);
        OutboxEventDto event = new OutboxEventDto(
                "evt-user-registered", "USER_REGISTERED", payload, Instant.now()
        );
        SyncPayloadDto syncPayload = new SyncPayloadDto("building-1", List.of(event));

        when(processedEventRepository.existsByEventId("evt-user-registered")).thenReturn(false);

        assertThatCode(() -> syncReceiverService.receiveSyncPayload(syncPayload))
                .doesNotThrowAnyException();

        ArgumentCaptor<UserRegisteredEventDto> dtoCaptor = ArgumentCaptor.forClass(UserRegisteredEventDto.class);
        verify(registerUserFromSyncUseCase).registerFromSync(dtoCaptor.capture());
        UserRegisteredEventDto captured = dtoCaptor.getValue();
        assertThat(captured.userId()).isEqualTo("user-123");
        assertThat(captured.username()).isEqualTo("alice");
        assertThat(captured.email()).isEqualTo("alice@example.com");

        ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(processedCaptor.capture());
        assertThat(processedCaptor.getValue().getEventId()).isEqualTo("evt-user-registered");
    }

    @Test
    void shouldSkipDuplicateUserRegisteredEvents() {
        OutboxEventDto event = new OutboxEventDto(
                "evt-user-registered", "USER_REGISTERED", "{}", Instant.now()
        );
        SyncPayloadDto syncPayload = new SyncPayloadDto("building-1", List.of(event));

        when(processedEventRepository.existsByEventId("evt-user-registered")).thenReturn(true);

        assertThatCode(() -> syncReceiverService.receiveSyncPayload(syncPayload))
                .doesNotThrowAnyException();

        verify(registerUserFromSyncUseCase, never()).registerFromSync(any());
        verify(processedEventRepository, never()).save(any());
    }
}
