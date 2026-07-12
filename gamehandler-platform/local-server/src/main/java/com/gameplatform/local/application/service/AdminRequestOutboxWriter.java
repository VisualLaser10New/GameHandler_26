package com.gameplatform.local.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.AdminRequestLocal;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.shared.dto.AdminRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Helper {@link Component} that encapsulates the atomic write of a
 * {@code admin_requests_local} PENDING row plus the matching outbox
 * {@code *_REQUESTED} event (PIANO §7.B W use cases). The single UUID
 * {@code requestId} doubles as both the {@code admin_requests_local}
 * primary key and the outbox {@code eventId} — this enables the matching
 * {@code *SyncService} to {@code markCompleted(requestId)} when the
 * Central return-event arrives carrying {@code originatingRequestId}.
 *
 * <p>A {@code FAILED} admin request can also be persisted without an
 * outbox row (used by the W12e/W12f DRAFT pre-check). The whole write
 * is wrapped by the caller's {@code @Transactional} boundary so the
 * {@code AdminRequestLocal} and {@code OutboxEvent} rows are persisted
 * atomically (or not at all).</p>
 */
@Component
public class AdminRequestOutboxWriter {

    private static final Logger log = LoggerFactory.getLogger(AdminRequestOutboxWriter.class);

    private final AdminRequestRepository adminRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminRequestOutboxWriter(AdminRequestRepository adminRequestRepository,
                                     OutboxEventRepository outboxEventRepository,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.adminRequestRepository = adminRequestRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Atomically persists a PENDING {@link AdminRequestLocal} and the
     * matching PENDING {@link OutboxEvent} carrying the serialized
     * payload. The same UUID is used for {@code requestId} and
     * {@code eventId}.
     */
    public AdminRequestDto writePendingRequest(String eventType,
                                                String actingUserId,
                                                String actingRole,
                                                String buildingId,
                                                Object payload) {
        String requestId = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        String payloadJson = serialize(eventType, payload);
        AdminRequestLocal adminReq = new AdminRequestLocal(
                requestId, eventType, actingUserId, actingRole, buildingId,
                payloadJson, "PENDING", null, now, null, requestId);
        adminRequestRepository.save(adminReq);
        OutboxEvent outbox = new OutboxEvent(
                requestId, eventType, payloadJson, "PENDING", now, null, 0);
        outboxEventRepository.save(outbox);
        log.info("Admin request {} persisted as PENDING (eventType={}, user={}, building={})",
                requestId, eventType, actingUserId, buildingId);
        return toDto(adminReq);
    }

    /**
     * Persists a FAILED {@link AdminRequestLocal} WITHOUT writing the
     * matching outbox row. Used by the W12e/W12f DRAFT pre-check
     * (refuse immediately FAILED without outbox when the tournament is
     * not DRAFT). The {@code reason} is stored as the
     * {@code result_data} JSON.
     */
    public AdminRequestDto writeFailedRequest(String eventType,
                                               String actingUserId,
                                               String actingRole,
                                               String buildingId,
                                               Object payload,
                                               String reason) {
        String requestId = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        String payloadJson = serialize(eventType, payload);
        AdminRequestLocal adminReq = new AdminRequestLocal(
                requestId, eventType, actingUserId, actingRole, buildingId,
                payloadJson, "FAILED", reason, now, now, null);
        adminRequestRepository.save(adminReq);
        log.warn("Admin request {} persisted as FAILED (eventType={}, user={}, reason={})",
                requestId, eventType, actingUserId, reason);
        return toDto(adminReq);
    }

    private String serialize(String eventType, Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize outbox payload for event " + eventType, e);
        }
    }

    static AdminRequestDto toDto(AdminRequestLocal adminReq) {
        return new AdminRequestDto(
                adminReq.getRequestId(),
                adminReq.getEventType(),
                adminReq.getActingUserId(),
                adminReq.getActingRole(),
                adminReq.getBuildingId(),
                adminReq.getPayloadJson(),
                adminReq.getStatus(),
                adminReq.getResultDataJson(),
                adminReq.getCreatedAt(),
                adminReq.getCompletedAt(),
                adminReq.getOutboxEventId()
        );
    }
}