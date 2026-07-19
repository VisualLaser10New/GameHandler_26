package com.gameplatform.local.domain.ports.out;

import com.gameplatform.shared.dto.SyncPayloadDto;

/**
 * Porta outbound per la sincronizzazione con il sistema centrale.
 * <p>
 * Definisce il contratto per la comunicazione verso il sistema centrale,
 * consentendo di verificarne la raggiungibilit&agrave; e di inviare
 * payload di sincronizzazione contenenti aggiornamenti sullo stato
 * della sede locale.
 * </p>
 *
 * @see SyncPayloadDto
 */
public interface SyncCentralSystemPort {
    /**
     * Verifica se il sistema centrale &egrave; raggiungibile.
     *
     * @return {@code true} se il sistema centrale risponde, {@code false} altrimenti
     */
    boolean isReachable();

    /**
     * Invia un payload di sincronizzazione al sistema centrale.
     *
     * @param payload il payload contenente i dati da sincronizzare
     * @return {@code true} se l'invio ha avuto successo, {@code false} altrimenti
     */
    boolean sendSyncPayload(SyncPayloadDto payload);
}
