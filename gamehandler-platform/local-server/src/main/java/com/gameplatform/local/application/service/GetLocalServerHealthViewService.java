package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import com.gameplatform.local.domain.ports.in.GetLocalServerHealthViewUseCase;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.RegisteredLocalServerLocalRepository;
import com.gameplatform.shared.dto.ServerHealthDto;
import com.gameplatform.shared.dto.ServerHealthViewDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read use case (PIANO §7.B): aggregates this Local node's own
 * pending-outbox count with the registry of all known registered local
 * servers for the {@code GET /api/admin/servers/health} PLATFORM_ADMIN
 * endpoint. The "own node" view is sourced from the locally replicated
 * {@code registered_local_servers_local} row keyed by
 * {@code app.building-id} (the registry row is replicated from the
 * Central registry); the pending-outbox count comes from the local
 * {@code outbox_events} table.
 */
@Service
@Transactional(readOnly = true)
public class GetLocalServerHealthViewService implements GetLocalServerHealthViewUseCase {

    private static final long OUTBOX_LIMIT_CAP = 500L;

    private final RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final String myBuildingId;

    public GetLocalServerHealthViewService(RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository,
                                            OutboxEventRepository outboxEventRepository,
                                            @Value("${app.building-id}") String myBuildingId) {
        this.registeredLocalServerLocalRepository = registeredLocalServerLocalRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.myBuildingId = myBuildingId;
    }

    @Override
    public ServerHealthViewDto getHealthView() {
        Optional<RegisteredLocalServerLocal> myRow =
                registeredLocalServerLocalRepository.findById(myBuildingId);
        // The pending outbox count is capped (a precise count above the cap is
        // not needed for a health indicator); we deliberately ask the
        // repository for OUTBOX_LIMIT_CAP+1 PENDING rows so the count is
        // exact up to that cap.
        long outboxCount = outboxEventRepository.findPendingLimit((int) (OUTBOX_LIMIT_CAP + 1)).size();
        List<ServerHealthDto> registry = registeredLocalServerLocalRepository.findAll().stream()
                .map(GetLocalServerHealthViewService::toServerHealth)
                .collect(Collectors.toList());
        return new ServerHealthViewDto(
                myBuildingId,
                myRow.map(RegisteredLocalServerLocal::isActive).orElse(false),
                myRow.map(RegisteredLocalServerLocal::getLastSeenAt).orElse(null),
                outboxCount,
                registry
        );
    }

    private static ServerHealthDto toServerHealth(RegisteredLocalServerLocal server) {
        return new ServerHealthDto(
                server.getBuildingId().id(),
                server.getBaseUrl(),
                server.getLastSeenAt(),
                server.isActive(),
                0L
        );
    }
}