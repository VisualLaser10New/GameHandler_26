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
 * Caso d'uso in lettura (PIANO §7.B): aggrega il conteggio degli outbox
 * in attesa del nodo locale con il registro di tutti i server locali
 * conosciuti per l'endpoint {@code GET /api/admin/servers/health}
 * (PLATFORM_ADMIN). La vista del "proprio nodo" proviene dalla riga
 * {@code registered_local_servers_local} replicata localmente, chiave
 * {@code app.building-id}; il conteggio outbox PENDING proviene dalla
 * tabella locale {@code outbox_events}.
 *
 * @see GetLocalServerHealthViewUseCase
 * @see RegisteredLocalServerLocalRepository
 * @see OutboxEventRepository
 */
@Service
@Transactional(readOnly = true)
public class GetLocalServerHealthViewService implements GetLocalServerHealthViewUseCase {

    private static final long OUTBOX_LIMIT_CAP = 500L;

    private final RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final String myBuildingId;

    /**
     * Costruisce il servizio con le dipendenze per la consultazione
     * della salute del server locale.
     *
     * @param registeredLocalServerLocalRepository il repository dei server locali registrati
     * @param outboxEventRepository               il repository per il conteggio degli outbox in attesa
     * @param myBuildingId                        l'identificativo dell'edificio locale (da configurazione)
     */
    public GetLocalServerHealthViewService(RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository,
                                            OutboxEventRepository outboxEventRepository,
                                            @Value("${app.building-id}") String myBuildingId) {
        this.registeredLocalServerLocalRepository = registeredLocalServerLocalRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.myBuildingId = myBuildingId;
    }

    /**
     * Recupera la vista completa della salute del server locale,
     * includendo il conteggio degli outbox PENDING (cappato a 500+1)
     * e la lista completa del registro dei server locali.
     *
     * @return il DTO con la vista della salute del server
     */
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

    /**
     * Converte un {@link RegisteredLocalServerLocal} nel corrispondente
     * {@link ServerHealthDto}.
     *
     * @param server l'entita' del modello di dominio (non null)
     * @return il DTO con buildingId, baseUrl, lastSeenAt e active
     */
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