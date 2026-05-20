package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.LocalServerJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class LocalServerRepositoryAdapter implements LocalServerRegistryPort {

    private final LocalServerJpaRepository jpaRepository;

    public LocalServerRepositoryAdapter(LocalServerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
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
    public void register(RegisteredLocalServer server) {
        if (server == null) {
            return;
        }
        RegisteredLocalServerJpaEntity entity = new RegisteredLocalServerJpaEntity(
                server.getBuildingId() != null ? server.getBuildingId().id() : null,
                server.getBaseUrl(),
                server.getLastSeenAt(),
                server.isActive()
        );
        jpaRepository.save(entity);
    }
}
