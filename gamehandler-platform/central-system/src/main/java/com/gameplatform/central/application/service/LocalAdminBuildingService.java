package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.UserNotFoundException;
import com.gameplatform.central.domain.model.LocalAdminBuilding;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.AssignLocalAdminBuildingsUseCase;
import com.gameplatform.central.domain.ports.in.GetLocalAdminBuildingsUseCase;
import com.gameplatform.central.domain.ports.out.LocalAdminBuildingRepository;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.LocalAdminBuildingEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for binding LOCAL_ADMIN users to buildings (central
 * Source-of-Truth) and replicating the bindings to every active Local Server
 * via the outbox.
 *
 * <p>Implements {@link AssignLocalAdminBuildingsUseCase} (assign / revoke) and
 * {@link GetLocalAdminBuildingsUseCase} (query). Operations are idempotent: an
 * assign of an already-bound building is a silent no-op, as is a revoke of a
 * non-existent binding. Each effective mutation writes a single outbox event
 * atomically in the same tx as the persistence mutation, mirroring the
 * {@code UserService.saveUserOnDB} pattern.</p>
 *
 * <p>The outbox event id and the {@link LocalAdminBuildingEventDto#eventId()}
 * share one UUID so the local side can dedupe and the central
 * {@code replication_progress} bookkeeping (which always tracks the outbox
 * event id) is consistent across both replication flows.</p>
 */
@Service
@Transactional
public class LocalAdminBuildingService implements AssignLocalAdminBuildingsUseCase, GetLocalAdminBuildingsUseCase {

    private static final String LOCAL_ADMIN_BUILDING_ASSIGNED_EVENT = "LOCAL_ADMIN_BUILDING_ASSIGNED";
    private static final String LOCAL_ADMIN_BUILDING_REVOKED_EVENT = "LOCAL_ADMIN_BUILDING_REVOKED";

    private final LocalAdminBuildingRepository localAdminBuildingRepository;
    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LocalAdminBuildingService(LocalAdminBuildingRepository localAdminBuildingRepository,
                                     UserRepository userRepository,
                                     OutboxEventRepository outboxEventRepository,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.localAdminBuildingRepository = localAdminBuildingRepository;
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void assignBuildings(String userId, List<String> buildingIds) {
        validateRequest(userId, buildingIds);
        User user = userRepository.findById(new UserId(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        for (String buildingId : buildingIds) {
            if (buildingId == null || buildingId.isBlank()) {
                throw new IllegalArgumentException("buildingId cannot be null or blank");
            }
            UserId uid = new UserId(userId);
            BuildingId bid = new BuildingId(buildingId);
            if (localAdminBuildingRepository.existsByUserIdAndBuildingId(uid, bid)) {
                // Idempotent: binding already present — skip silently.
                continue;
            }
            Instant assignedAt = Instant.now(clock);
            localAdminBuildingRepository.save(new LocalAdminBuilding(uid, bid, assignedAt));
            writeOutboxEvent(LOCAL_ADMIN_BUILDING_ASSIGNED_EVENT, userId, buildingId, assignedAt);
        }
    }

    @Override
    public void revokeBuildings(String userId, List<String> buildingIds) {
        validateRequest(userId, buildingIds);
        User user = userRepository.findById(new UserId(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        for (String buildingId : buildingIds) {
            if (buildingId == null || buildingId.isBlank()) {
                throw new IllegalArgumentException("buildingId cannot be null or blank");
            }
            UserId uid = new UserId(userId);
            BuildingId bid = new BuildingId(buildingId);
            if (!localAdminBuildingRepository.existsByUserIdAndBuildingId(uid, bid)) {
                // Idempotent: nothing to revoke — skip silently.
                continue;
            }
            localAdminBuildingRepository.deleteByUserIdAndBuildingId(uid, bid);
            writeOutboxEvent(LOCAL_ADMIN_BUILDING_REVOKED_EVENT, userId, buildingId, null);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getBuildingsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        return localAdminBuildingRepository.findByUserId(new UserId(userId)).stream()
                .map(b -> b.getBuildingId().id())
                .collect(Collectors.toList());
    }

    /**
     * Serialises a metadata event and writes it to the outbox. Mirrors the inline
     * outbox-write in {@code UserService.saveUserOnDB}: a single UUID is shared by
     * the outbox event id and the {@link LocalAdminBuildingEventDto#eventId()} so
     * the local side can dedupe and the central {@code replication_progress}
     * (which always tracks the outbox event id) is consistent across flows.
     */
    private void writeOutboxEvent(String eventType, String userId, String buildingId, Instant assignedAt) {
        String eventId = UUID.randomUUID().toString();
        LocalAdminBuildingEventDto dto = new LocalAdminBuildingEventDto(
                eventId, eventType, userId, buildingId, assignedAt);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize metadata event to JSON: ", e);
        }

        OutboxEvent event = new OutboxEvent(
                eventId, eventType, payload, OutboxEventStatus.PENDING, Instant.now(clock), null);
        outboxEventRepository.save(event);
    }

    private static void validateRequest(String userId, List<String> buildingIds) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        if (buildingIds == null || buildingIds.isEmpty()) {
            throw new IllegalArgumentException("buildingIds cannot be null or empty");
        }
    }
}