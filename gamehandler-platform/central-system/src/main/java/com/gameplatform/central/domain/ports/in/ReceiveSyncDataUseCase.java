package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.dto.SyncPayloadDto;

/**
 * Caso d'uso che riceve ed elabora un payload di sincronizzazione in arrivo
 * da un Local Server, integrandone i dati nel sistema centrale.
 */
public interface ReceiveSyncDataUseCase {

    /**
     * Elabora il payload di sincronizzazione ricevuto applicandone il contenuto al sistema centrale.
     *
     * @param payload il payload contenente i dati di sincronizzazione; non deve essere {@code null}
     * @throws IllegalArgumentException se il payload è {@code null} o non valido
     */
    void receiveSyncPayload(SyncPayloadDto payload);
}

