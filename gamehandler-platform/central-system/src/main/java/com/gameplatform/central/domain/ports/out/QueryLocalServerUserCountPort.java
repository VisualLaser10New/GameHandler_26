package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;

/**
 * Porta di uscita utilizzata dalla riconciliazione periodica
 * ({@code UserReplicationReconciliationService}) per interrogare il numero di
 * righe attualmente presenti nella tabella {@code replicated_users} del server
 * locale indicato.
 *
 * <p>Il valore restituito viene confrontato con il conteggio degli utenti
 * centrale (ottenuto da {@code UserService#getAllUsersForSync()}) per decidere
 * se la riconciliazione debba reinviare l'intero snapshot utente a quel
 * server.</p>
 *
 * <p><b>Contratto di fallimento:</b> le implementazioni DEVONO restituire
 * {@code -1L} quando il conteggio non può essere recuperato (fallimento di rete
 * transitorio dopo l'esaurimento dei tentativi, risposta non 2xx o qualunque
 * eccezione). Il servizio tratta {@code -1L} come "sconosciuto" — registra un
 * avviso e SALTA quel server nel ciclo corrente (un reinvio scatenato da un
 * server irraggiungibile a ogni ora sarebbe dispendioso e potrebbe accumularsi
 * se il server rimane fuori servizio a lungo).</p>
 *
 * @see RegisteredLocalServer
 * @see #COUNT_UNAVAILABLE
 */
public interface QueryLocalServerUserCountPort {

    /**
     * Valore sentinella restituito da
     * {@link #countReplicatedUsers(RegisteredLocalServer)} quando il conteggio
     * non può essere recuperato dopo l'esaurimento dei tentativi (server non
     * raggiungibile, risposta non 2xx, fallimento di rete transitorio). Il
     * servizio lo tratta come "sconosciuto" e SALTA quel server per il ciclo
     * corrente. Definito sulla porta (non sull'adattatore) affinché il servizio
     * applicativo vi faccia riferimento senza dipendere dal livello
     * infrastrutturale (regola di dipendenza esagonale).
     */
    long COUNT_UNAVAILABLE = -1L;

    /**
     * Restituisce il numero di utenti replicati attualmente presenti nel server
     * locale indicato.
     *
     * @param server il server locale di cui contare gli utenti replicati; non deve essere {@code null}
     * @return il numero di utenti replicati, o {@link #COUNT_UNAVAILABLE} ({@code -1L}) se il conteggio non può essere
     *         recuperato; mai negativo se diverso dalla sentinella
     * @throws IllegalArgumentException se {@code server} è {@code null}
     * @see #COUNT_UNAVAILABLE
     */
    long countReplicatedUsers(RegisteredLocalServer server);
}
