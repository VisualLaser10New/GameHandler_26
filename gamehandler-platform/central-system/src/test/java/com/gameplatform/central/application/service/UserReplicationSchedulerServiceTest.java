package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.UserSyncDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserReplicationSchedulerService}, covering:
 * <ul>
 *   <li>Server-failure isolation: one failing server does NOT abort the loop</li>
 *   <li>markAsSent only called when ALL servers received the event</li>
 *   <li>Uses {@code findPendingLimit(50)} instead of {@code findPending()}</li>
 *   <li>No-op when there are no pending events or no active servers</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserReplicationSchedulerServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private LocalServerRegistryPort localServerRegistryPort;

    @Mock
    private PushUserToLocalServersPort pushUserToLocalServersPort;

    private UserReplicationSchedulerService schedulerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        schedulerService = new UserReplicationSchedulerService(
                outboxEventRepository,
                localServerRegistryPort,
                pushUserToLocalServersPort,
                objectMapper
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Uses findPendingLimit, not findPending
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateUsers_shouldCallFindPendingLimit_notFindPending() {
        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of());

        schedulerService.replicateUsers();

        verify(outboxEventRepository).findPendingLimit(50);
        verify(outboxEventRepository, never()).findPending();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // No-op cases
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateUsers_shouldDoNothing_whenNoPendingEvents() {
        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of());

        schedulerService.replicateUsers();

        verify(pushUserToLocalServersPort, never()).pushUsers(any(), any());
        verify(outboxEventRepository, never()).markAsSent(any());
    }

    @Test
    void replicateUsers_shouldDoNothing_whenNoActiveServers() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of());

        schedulerService.replicateUsers();

        verify(pushUserToLocalServersPort, never()).pushUsers(any(), any());
        verify(outboxEventRepository, never()).markAsSent(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Happy path – all servers succeed → markAsSent
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateUsers_shouldMarkAsSent_whenAllServersReceiveEventSuccessfully() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("server-1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("server-2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));
        doNothing().when(pushUserToLocalServersPort).pushUsers(any(), any());

        schedulerService.replicateUsers();

        verify(outboxEventRepository).markAsSent(event.getId());
    }

    @Test
    void replicateUsers_shouldPushToEachServer() {
        OutboxEvent event = buildUserEvent("USER_UPDATED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));
        doNothing().when(pushUserToLocalServersPort).pushUsers(any(), any());

        schedulerService.replicateUsers();

        verify(pushUserToLocalServersPort).pushUsers(any(), eq(server1));
        verify(pushUserToLocalServersPort).pushUsers(any(), eq(server2));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Server failure isolation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateUsers_shouldContinueToNextServer_whenOneServerFails() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));

        // server1 fails with a RuntimeException; server2 should still receive the event
        doThrow(new RuntimeException("Network error")).when(pushUserToLocalServersPort).pushUsers(any(), eq(server1));
        doNothing().when(pushUserToLocalServersPort).pushUsers(any(), eq(server2));

        schedulerService.replicateUsers();

        // Server2 must still have been called despite server1's failure
        verify(pushUserToLocalServersPort).pushUsers(any(), eq(server2));
    }

    @Test
    void replicateUsers_shouldNotMarkAsSent_whenAtLeastOneServerFails() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));

        // server1 fails, server2 succeeds
        doThrow(new RuntimeException("Timeout")).when(pushUserToLocalServersPort).pushUsers(any(), eq(server1));
        doNothing().when(pushUserToLocalServersPort).pushUsers(any(), eq(server2));

        schedulerService.replicateUsers();

        // Event must NOT be marked as sent because server1 failed
        verify(outboxEventRepository, never()).markAsSent(event.getId());
    }

    @Test
    void replicateUsers_shouldProcessAllEvents_evenWhenSomeServersFail() {
        OutboxEvent event1 = buildUserEvent("USER_REGISTERED");
        OutboxEvent event2 = buildUserEvent("USER_UPDATED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event1, event2));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1));

        // First event: server1 fails
        doThrow(new RuntimeException("Timeout"))
                .doNothing()   // Second event: server1 succeeds
                .when(pushUserToLocalServersPort).pushUsers(any(), eq(server1));

        schedulerService.replicateUsers();

        // event1 not marked (failed), event2 must be marked
        verify(outboxEventRepository, never()).markAsSent(event1.getId());
        verify(outboxEventRepository).markAsSent(event2.getId());
    }

    @Test
    void replicateUsers_shouldIgnoreNonUserReplicationEvents() {
        // A GAME_SESSION_COMPLETED event must not be replicated by this scheduler
        OutboxEvent irrelevantEvent = new OutboxEvent(
                UUID.randomUUID().toString(),
                "GAME_SESSION_COMPLETED",
                "{\"dummy\":true}",
                OutboxEventStatus.PENDING,
                Instant.now(),
                null
        );

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(irrelevantEvent));

        schedulerService.replicateUsers();

        verify(pushUserToLocalServersPort, never()).pushUsers(any(), any());
        verify(outboxEventRepository, never()).markAsSent(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────

    private OutboxEvent buildUserEvent(String eventType) {
        UserSyncDto dto = new UserSyncDto(UUID.randomUUID().toString(), "alice",
                "$2a$10$fakehashedpassword1234567890", List.of("USER"));
        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new OutboxEvent(
                UUID.randomUUID().toString(),
                eventType,
                payload,
                OutboxEventStatus.PENDING,
                Instant.now(),
                null
        );
    }

    private RegisteredLocalServer buildServer(String buildingId, String baseUrl) {
        return new RegisteredLocalServer(new BuildingId(buildingId), baseUrl, Instant.now(), true);
    }
}
