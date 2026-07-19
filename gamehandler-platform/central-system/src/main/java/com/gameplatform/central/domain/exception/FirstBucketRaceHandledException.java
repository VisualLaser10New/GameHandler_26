package com.gameplatform.central.domain.exception;

import com.gameplatform.central.application.service.StatisticsFirstBucketRaceRetryHelper;
import com.gameplatform.central.application.service.SyncEventProcessor;

/**
 * Eccezione sentinella lanciata dai metodi {@code update*Stats} dopo che una
 * race condition in fase di inserimento del primo bucket e' stata risolta in una
 * transazione {@code REQUIRES_NEW} separata gestita da
 * {@link StatisticsFirstBucketRaceRetryHelper}.
 *
 * <p>{@link SyncEventProcessor#processOne} intercetta questa eccezione separatamente
 * da {@code DataIntegrityViolationException} e restituisce {@code true} senza
 * ripersistere {@code processed_events} (la transazione dell'helper ha gia'
 * reso persistente il record di deduplicazione).</p>
 *
 * @see StatisticsFirstBucketRaceRetryHelper
 * @see SyncEventProcessor
 */
public class FirstBucketRaceHandledException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione sentinella con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public FirstBucketRaceHandledException(String message) {
        super(message);
    }
}
