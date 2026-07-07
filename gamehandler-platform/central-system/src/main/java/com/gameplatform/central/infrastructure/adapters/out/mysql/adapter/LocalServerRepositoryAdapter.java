package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.application.service.LateRegistrationCatchUpService;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.LocalServerJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class LocalServerRepositoryAdapter implements LocalServerRegistryPort {

    private final LocalServerJpaRepository jpaRepository;
    private final LateRegistrationCatchUpService lateRegistrationCatchUpService;

    public LocalServerRepositoryAdapter(LocalServerJpaRepository jpaRepository,
                                        LateRegistrationCatchUpService lateRegistrationCatchUpService) {
        this.jpaRepository = jpaRepository;
        this.lateRegistrationCatchUpService = lateRegistrationCatchUpService;
    }

    @Override
    public List<RegisteredLocalServer> getActiveLocalServers() {
        return jpaRepository.findByIsActiveTrue().stream()
                .map(entity -> new RegisteredLocalServer(
                        new BuildingId(entity.getBuildingId()),
                        entity.getBaseUrl(),
                        entity.getLastSeenAt(),
                        entity.isActive()
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void register(RegisteredLocalServer server) {
        if (server == null || server.getBuildingId() == null) {
            return;
        }
        String buildingId = server.getBuildingId().id();
        Optional<RegisteredLocalServerJpaEntity> existing = jpaRepository.findById(buildingId);
        // Fire catch-up whenever the server is new OR is transitioning back to active
        // (was deactivated by LocalServerHealthMonitorService after a stale window).
        // A server re-registering while still active does NOT need catch-up: any events
        // it missed while briefly down stayed PENDING (pushes failed -> allSucceeded=false
        // -> not marked SENT), so the 5-min UserReplicationSchedulerService will retry them.
        // The only events a reactivating server can have missed are SENT events that were
        // sent to OTHER active servers during its deactivation — those need catch-up.
        boolean wasInactive = existing.isEmpty() || !existing.get().isActive();
        RegisteredLocalServerJpaEntity entity = existing.orElseGet(() -> new RegisteredLocalServerJpaEntity(
                        buildingId,
                        server.getBaseUrl(),
                        server.getLastSeenAt(),
                        server.isActive()
                ));
        entity.setBaseUrl(server.getBaseUrl());
        entity.setLastSeenAt(server.getLastSeenAt());
        entity.setActive(server.isActive());
        try {
            jpaRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // Concurrent registration race: another thread inserted the row
            // between our findById and save. Reload the now-stable row, copy
            // the incoming fields onto the managed entity, and save again.
            // The winning thread owns the M8 catch-up; we skip it (wasInactive=false).
            RegisteredLocalServerJpaEntity reloaded = jpaRepository.findById(buildingId)
                    .orElseThrow(() -> e);
            reloaded.setBaseUrl(server.getBaseUrl());
            reloaded.setLastSeenAt(server.getLastSeenAt());
            reloaded.setActive(server.isActive());
            jpaRepository.save(reloaded);
            wasInactive = false;
        }

        if (wasInactive) {
            // M8: decouple the catch-up REST + progress writes from the registration tx.
            // Running in afterCommit eliminates phantom replication_progress rows if the
            // registration rolls back, and removes REST latency from the registration
            // call. The catch-up opens its own REQUIRES_NEW tx after the outer commit.
            // The hook fires on first registration AND on reactivation (deactivated ->
            // active) — both are cases where the server may be missing SENT events that
            // were replicated to other servers while it was absent. Catch-up is fully
            // idempotent (replication_progress unique key on event_id+server_id +
            // pre-push existsByEventIdAndServerId check + local-side stale-event guard),
            // so firing it on a reactivation that needs no replay is a no-op.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    lateRegistrationCatchUpService.catchUpNewlyRegisteredServer(server);
                }
            });
        }
    }

    @Override
    @Transactional
    public void updateLastSeenAt(BuildingId buildingId, Instant lastSeenAt) {
        if (buildingId == null || lastSeenAt == null) {
            return;
        }
        jpaRepository.findById(buildingId.id()).ifPresent(entity -> {
            entity.setLastSeenAt(lastSeenAt);
            jpaRepository.save(entity);
        });
    }

    @Override
    public List<RegisteredLocalServer> findAll() {
        return jpaRepository.findAllByOrderByLastSeenAtDesc().stream()
                .map(entity -> new RegisteredLocalServer(
                        new BuildingId(entity.getBuildingId()),
                        entity.getBaseUrl(),
                        entity.getLastSeenAt(),
                        entity.isActive()
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deactivate(BuildingId buildingId) {
        if (buildingId == null) {
            return;
        }
        // Atomic UPDATE — the @Modifying query flushes within this tx.
        jpaRepository.deactivateByBuildingId(buildingId.id());
    }
}
