package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.domain.ports.out.ReplicationProgressRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.UserSyncDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserReplicationSchedulerPoisonedOutboxBugTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private LocalServerRegistryPort localServerRegistryPort;

    @Mock
    private PushUserToLocalServersPort pushUserToLocalServersPort;

    @Mock
    private ReplicationProgressRepository replicationProgressRepository;

    private ObjectMapper objectMapper;
    private UserReplicationSchedulerService service;
    private RegisteredLocalServer server;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new UserReplicationSchedulerService(
                outboxEventRepository,
                localServerRegistryPort,
                pushUserToLocalServersPort,
                replicationProgressRepository,
                objectMapper,
                Runnable::run // synchronous executor keeps this bug test single-threaded & deterministic
        );
        server = new RegisteredLocalServer(new BuildingId("building-repl"), "http://local-server", Instant.now(), true);
        lenient().when(replicationProgressRepository.findByEventId(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("BUG-REPL-01: one poisoned user outbox event must not block later valid user events")
    void malformedUserReplicationEvent_doesNotAbortSchedulerRun() throws Exception {
        OutboxEvent poisoned = new OutboxEvent(
                "evt-poisoned",
                "USER_REGISTERED",
                "{not-json",
                OutboxEventStatus.PENDING,
                Instant.parse("2026-01-01T10:00:00Z"),
                null
        );
        UserSyncDto validUser = new UserSyncDto(
                "user-valid",
                "valid-user",
                "$2a$10$validHashPlaceholder123456789012345678901234567890",
                List.of("USER")
        );
        OutboxEvent valid = new OutboxEvent(
                "evt-valid",
                "USER_UPDATED",
                objectMapper.writeValueAsString(validUser),
                OutboxEventStatus.PENDING,
                Instant.parse("2026-01-01T10:01:00Z"),
                null
        );

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(poisoned, valid));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server));

        assertThatCode(() -> service.replicateUsers())
                .as("A bad outbox payload should be quarantined/logged without stopping later events")
                .doesNotThrowAnyException();

        verify(pushUserToLocalServersPort).pushUsers(List.of(validUser), server);
        verify(outboxEventRepository).markAsSent("evt-valid");
        verify(outboxEventRepository, never()).markAsSent("evt-poisoned");
        verify(outboxEventRepository).markAsFailed("evt-poisoned");
    }
}
