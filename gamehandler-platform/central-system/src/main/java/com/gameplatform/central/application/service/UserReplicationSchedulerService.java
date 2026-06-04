package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.shared.dto.UserSyncDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserReplicationSchedulerService {
    private static final String USER_REGISTERED_EVENT = "USER_REGISTERED";
    private static final String USER_UPDATED_EVENT = "USER_UPDATED";

    private final OutboxEventRepository outboxEventRepository;
    private final LocalServerRegistryPort localServerRegistryPort;
    private final PushUserToLocalServersPort pushUserToLocalServersPort;
    private final ObjectMapper objectMapper;

    public UserReplicationSchedulerService(
            OutboxEventRepository outboxEventRepository,
            LocalServerRegistryPort localServerRegistryPort,
            PushUserToLocalServersPort pushUserToLocalServersPort,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.localServerRegistryPort = localServerRegistryPort;
        this.pushUserToLocalServersPort = pushUserToLocalServersPort;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRate = 300_000)
    public void replicateUsers() {
        List<OutboxEvent> pendingUserEvents = outboxEventRepository.findPending().stream()
                .filter(this::isUserReplicationEvent)
                .toList();

        if (pendingUserEvents.isEmpty()) {
            return;
        }

        List<RegisteredLocalServer> activeLocalServers = localServerRegistryPort.getActiveLocalServers();

        if (activeLocalServers.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pendingUserEvents) {
            UserSyncDto user = deserializeUser(event);

            for (RegisteredLocalServer server : activeLocalServers) {
                pushUserToLocalServersPort.pushUsers(List.of(user), server);
            }

            outboxEventRepository.markAsSent(event.getId());
        }
    }

    private boolean isUserReplicationEvent(OutboxEvent event) {
        return USER_REGISTERED_EVENT.equals(event.getEventType())
                || USER_UPDATED_EVENT.equals(event.getEventType());
    }

    private UserSyncDto deserializeUser(OutboxEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), UserSyncDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize user replication event: " + event.getId(), e);
        }
    }
}