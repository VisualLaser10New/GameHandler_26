package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.application.service.LateRegistrationCatchUpService;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.LocalServerJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        boolean isNewRegistration = existing.isEmpty();
        RegisteredLocalServerJpaEntity entity = existing.orElseGet(() -> new RegisteredLocalServerJpaEntity(
                        buildingId,
                        server.getBaseUrl(),
                        server.getLastSeenAt(),
                        server.isActive()
                ));
        entity.setBaseUrl(server.getBaseUrl());
        entity.setLastSeenAt(server.getLastSeenAt());
        entity.setActive(server.isActive());
        jpaRepository.save(entity);

        if (isNewRegistration) {
            lateRegistrationCatchUpService.catchUpNewlyRegisteredServer(server);
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
}
