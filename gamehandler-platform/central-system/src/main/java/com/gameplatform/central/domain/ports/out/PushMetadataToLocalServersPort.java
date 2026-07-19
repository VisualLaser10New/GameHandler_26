package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.LocalAdminBuildingEventDto;

import java.util.List;

/**
 * Porta di uscita per l'invio di un batch di eventi di metadati relativi
 * all'associazione LOCAL_ADMIN&harr;edificio all'endpoint
 * {@code PUT /internal/metadata/sync} di un singolo server locale.
 *
 * <p>Simmetrica a {@link PushUserToLocalServersPort} ma relativa ai metadati.
 * Non è previsto alcun contratto di ack: l'upsert o la cancellazione locale è
 * idempotente per chiave primaria composta, quindi un fallimento transitorio di
 * trasporto viene ritentato tramite l'outbox al ciclo successivo dello
 * scheduler, senza mai produrre un evento "avvelenato".</p>
 *
 * @see PushUserToLocalServersPort
 * @see LocalAdminBuildingEventDto
 */
public interface PushMetadataToLocalServersPort {

    /**
     * Invia un batch di eventi di metadati al server locale indicato.
     *
     * @param events il batch di eventi di metadati da inviare; non deve essere {@code null}
     * @param server il server locale attivo di destinazione; non deve essere {@code null}
     * @throws IllegalArgumentException in caso di parametri {@code null}
     * @throws com.gameplatform.central.domain.exception.TransientPushException
     *         in caso di fallimento transitorio di trasporto (il chiamante ritenta tramite l'outbox)
     */
    void pushMetadata(List<LocalAdminBuildingEventDto> events, RegisteredLocalServer server);
}
