package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.DeadLetterEvent;

/**
 * Repository out-port per gli eventi non recapitabili (dead-letter).
 * <p>
 * Persiste gli eventi che hanno superato il numero massimo di tentativi di
 * consegna verso il sistema centrale, consentendone il monitoraggio e
 * l'ispezione manuale da parte degli amministratori.
 * </p>
 */
public interface DeadLetterRepository {

    /**
     * Salva un evento nella coda dead-letter.
     *
     * @param event l'evento non recapitabile da persistere
     */
    void save(DeadLetterEvent event);

    /**
     * Restituisce il numero totale di eventi presenti nella coda dead-letter.
     *
     * @return il conteggio degli eventi dead-letter
     */
    long count();
}
