package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.OutboxEvent;
import java.util.List;

/**
 * Repository out-port per la gestione degli eventi nella coda outbox.
 * <p>
 * Implementa il pattern transactional outbox per garantire l'invio affidabile
 * degli eventi verso il sistema centrale. Gli eventi vengono persistiti
 * atomicamente con le operazioni di dominio e successivamente inviati da un
 * processo asincrono di pubblicazione.
 * </p>
 *
 * @see OutboxEvent
 */
public interface OutboxEventRepository {
    /**
     * Salva un nuovo evento nella coda outbox.
     *
     * @param event l'evento da accodare
     * @return l'evento persistito
     */
    OutboxEvent save(OutboxEvent event);

    /**
     * Restituisce tutti gli eventi in attesa di essere inviati (stato PENDING).
     *
     * @return la lista degli eventi in attesa di invio
     */
    List<OutboxEvent> findPending();

    /**
     * Restituisce un numero limitato di eventi in attesa di essere inviati.
     *
     * @param limit il numero massimo di eventi da restituire
     * @return la lista degli eventi in attesa di invio, limitata al numero specificato
     */
    List<OutboxEvent> findPendingLimit(int limit);

    /**
     * Marca un evento come inviato con successo (stato SENT).
     *
     * @param id l'identificativo dell'evento da marcare
     */
    void markAsSent(String id);

    /**
     * Marca un evento come fallito (stato FAILED).
     *
     * @param id l'identificativo dell'evento da marcare
     */
    void markAsFailed(String id);

    /**
     * Incrementa il contatore dei tentativi di invio per un evento.
     *
     * @param id l'identificativo dell'evento
     */
    void incrementRetry(String id);

    /**
     * Marca atomicamente tutti gli eventi specificati come inviati (stato SENT)
     * in un'unica operazione bulk.
     *
     * @param ids la lista degli identificativi degli eventi da marcare come inviati
     */
    void markAsSentBatch(List<String> ids);

    /**
     * Incrementa atomicamente il contatore dei tentativi per tutti gli eventi
     * specificati in un'unica operazione bulk.
     *
     * @param ids la lista degli identificativi degli eventi
     */
    void incrementRetryBatch(List<String> ids);
}
