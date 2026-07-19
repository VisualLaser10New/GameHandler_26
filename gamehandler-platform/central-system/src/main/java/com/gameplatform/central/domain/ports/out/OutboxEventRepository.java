package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.OutboxEvent;
import java.util.List;

/**
 * Porta di persistenza per gli eventi dell'outbox di replicazione.
 *
 * <p>Gestisce gli eventi in attesa di invio verso i server locali e il loro
 * stato di avanzamento (inviato, fallito), supportando il pattern outbox e la
 * visualizzazione del backlog di replica per-server.</p>
 *
 * @see OutboxEvent
 */
public interface OutboxEventRepository {

    /**
     * Salva o aggiorna un evento dell'outbox.
     *
     * @param event l'evento da persistere; non deve essere {@code null}
     * @return l'evento salvato, eventualmente arricchito di metadati di persistenza
     * @throws IllegalArgumentException se {@code event} è {@code null}
     */
    OutboxEvent save(OutboxEvent event);

    /**
     * Restituisce tutti gli eventi attualmente in attesa di invio.
     *
     * @return la lista degli eventi pendenti; mai {@code null}, eventualmente vuota
     */
    List<OutboxEvent> findPending();

    /**
     * Restituisce al massimo {@code limit} eventi pendenti, ordinati per istante
     * di creazione crescente.
     *
     * @param limit il numero massimo di eventi da restituire; deve essere strettamente positivo
     * @return la lista degli eventi pendenti, al più di dimensione {@code limit}; mai {@code null}
     * @throws IllegalArgumentException se {@code limit} è minore o uguale a {@code 0}
     */
    List<OutboxEvent> findPendingLimit(int limit);

    /**
     * Marca l'evento identificato dall'id come inviato.
     *
     * <p>Se l'id non corrisponde ad alcun evento, l'operazione non ha effetto.</p>
     *
     * @param id l'identificativo dell'evento; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code id} è {@code null}
     */
    void markAsSent(String id);

    /**
     * Marca l'evento identificato dall'id come fallito.
     *
     * <p>Se l'id non corrisponde ad alcun evento, l'operazione non ha effetto.</p>
     *
     * @param id l'identificativo dell'evento; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code id} è {@code null}
     */
    void markAsFailed(String id);

    /**
     * Conta gli eventi di replica utente ({@code USER_REGISTERED} / {@code USER_UPDATED})
     * non ancora inviati per i quali non è stato registrato alcun avanzamento di
     * replica verso il server indicato.
     *
     * <p>Rappresenta il backlog di replica pendente, evidenziato per-server nella
     * vista di salute amministrativa.</p>
     *
     * @param serverId l'identificativo dell'edificio del server locale; non deve essere {@code null}
     * @return il numero di eventi pendenti, sempre non negativo
     * @throws IllegalArgumentException se {@code serverId} è {@code null}
     */
    long countPendingReplicationForServer(String serverId);
}
