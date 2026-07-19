package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.domain.model.BuildingId;
import java.time.Instant;
import java.util.List;

/**
 * Porta di uscita per la gestione del registro dei server locali.
 *
 * <p>Espone le operazioni di consultazione, registrazione e disattivazione dei
 * server locali noti al sistema centrale, oltre all'aggiornamento del loro stato
 * di heartbeat, a supporto dello scheduler di replica e della vista di salute.</p>
 *
 * @see RegisteredLocalServer
 * @see #getActiveLocalServers()
 * @see #deactivate(BuildingId)
 */
public interface LocalServerRegistryPort {

    /**
     * Restituisce l'elenco dei server locali attualmente attivi.
     *
     * @return la lista dei server attivi; mai {@code null}, eventualmente vuota se non vi sono server attivi
     * @see #deactivate(BuildingId)
     */
    List<RegisteredLocalServer> getActiveLocalServers();

    /**
     * Registra un nuovo server locale o riattiva un server precedentemente disattivato.
     *
     * @param server il server da registrare; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code server} è {@code null}
     */
    void register(RegisteredLocalServer server);

    /**
     * Aggiorna il timestamp di ultimo contatto per il server identificato dall'edificio indicato.
     *
     * @param buildingId  l'identificativo dell'edificio del server; non deve essere {@code null}
     * @param lastSeenAt  il nuovo istante di ultimo contatto; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code buildingId} o {@code lastSeenAt} sono {@code null}
     */
    void updateLastSeenAt(BuildingId buildingId, Instant lastSeenAt);

    /**
     * Restituisce tutti i server locali registrati, attivi e non attivi, ordinati
     * per istante di ultimo contatto più recente per primo.
     *
     * <p>Utilizzato dall'endpoint amministrativo {@code /internal/servers} per
     * costruire la vista di salute per-server.</p>
     *
     * @return la lista completa dei server registrati; mai {@code null}, eventualmente vuota
     * @see #getActiveLocalServers()
     */
    List<RegisteredLocalServer> findAll();

    /**
     * Disattiva in modo atomico il server locale identificato dall'edificio indicato.
     *
     * <p>Una volta disattivato, il server non è più restituito da
     * {@link #getActiveLocalServers()} e lo scheduler di replica sospende gli
     * invii verso di esso. Una nuova registrazione tramite {@code register(...)}
     * lo riporta allo stato attivo.</p>
     *
     * @param buildingId l'identificativo dell'edificio del server da disattivare; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code buildingId} è {@code null}
     * @see #register(RegisteredLocalServer)
     */
    void deactivate(BuildingId buildingId);
}
