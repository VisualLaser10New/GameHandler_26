package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import com.gameplatform.local.domain.ports.in.ToggleLocalServerActiveUseCase;
import com.gameplatform.local.domain.ports.out.RegisteredLocalServerLocalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Feature 3 — caso d'uso in scrittura per l'endpoint
 * {@code PATCH /api/admin/servers/{buildingId}/active} (PLATFORM_ADMIN).
 * Aggiorna il flag {@code active} sulla riga replicata localmente
 * {@code registered_local_servers_local}. La proiezione e' una replica
 * Central→Local, quindi un toggle manuale e' osservato localmente fino
 * al prossimo ciclo di sincronizzazione del registro.
 *
 * @see ToggleLocalServerActiveUseCase
 * @see RegisteredLocalServerLocalRepository
 */
@Service
@Transactional
public class ToggleLocalServerActiveService implements ToggleLocalServerActiveUseCase {

    private final RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository;

    /**
     * Costruisce il servizio con il repository dei server locali registrati.
     *
     * @param registeredLocalServerLocalRepository il repository per l'accesso
     *                                             ai server locali (non null)
     */
    public ToggleLocalServerActiveService(RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository) {
        this.registeredLocalServerLocalRepository = registeredLocalServerLocalRepository;
    }

    /**
     * Imposta il flag active per un server locale registrato.
     *
     * @param buildingId l'identificativo del building del server (non blank)
     * @param active     il nuovo valore del flag active
     * @return un Optional contenente il server aggiornato, o vuoto se buildingId e' blank
     */
    @Override
    public Optional<RegisteredLocalServerLocal> setActive(String buildingId, boolean active) {
        if (buildingId == null || buildingId.isBlank()) {
            return Optional.empty();
        }
        return registeredLocalServerLocalRepository.setActive(buildingId, active);
    }
}