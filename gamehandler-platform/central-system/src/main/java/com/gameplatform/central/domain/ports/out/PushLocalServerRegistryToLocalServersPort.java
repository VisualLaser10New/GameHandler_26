package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.LocalServerRegistryEventDto;

import java.util.List;

/**
 * Porta di uscita per l'invio di un batch di eventi
 * {@code LOCAL_SERVER_REGISTRY_UPSERTED} all'endpoint
 * {@code PUT /internal/servers/sync} di un singolo server locale. Gemello
 * strutturale di {@link PushTournamentSummaryToLocalServersPort}.
 *
 * <p>Non è previsto alcun contratto di ack o di isolamento dei messaggi
 * avvelenati: l'upsert locale è idempotente per chiave primaria
 * {@code (buildingId)}, pertanto un fallimento transitorio di trasporto viene
 * semplicemente ritentato tramite l'outbox al ciclo successivo dello scheduler.
 * Esporre il registro dei server locali a ogni server consente a un client
 * {@code PLATFORM_ADMIN} collegato a qualunque server locale di vedere
 * l'intero registro senza una chiamata diretta al sistema centrale.</p>
 *
 * @throws com.gameplatform.central.domain.exception.TransientPushException
 *         in caso di fallimento transitorio di trasporto (il chiamante ritenta tramite l'outbox)
 * @see PushTournamentSummaryToLocalServersPort
 * @see LocalServerRegistryEventDto
 */
public interface PushLocalServerRegistryToLocalServersPort {

    /**
     * Invia un batch di eventi di registro dei server locali a un singolo server locale.
     *
     * @param events il batch di DTO di registro da inviare; non deve essere {@code null}
     * @param server il singolo server locale attivo di destinazione; non deve essere {@code null}
     * @throws IllegalArgumentException in caso di parametri {@code null}
     * @throws com.gameplatform.central.domain.exception.TransientPushException
     *         in caso di fallimento transitorio di trasporto
     */
    void push(List<LocalServerRegistryEventDto> events, RegisteredLocalServer server);
}
