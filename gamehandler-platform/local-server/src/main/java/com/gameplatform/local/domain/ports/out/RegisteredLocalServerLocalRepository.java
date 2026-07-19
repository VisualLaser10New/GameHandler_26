package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;

import java.util.List;
import java.util.Optional;

/**
 * Out-port for the {@code registered_local_servers_local} read-only
 * replica (PIANO §7.B). {@code save} is an idempotent upsert by PK
 * {@code buildingId}.
 */
public interface RegisteredLocalServerLocalRepository {

    /**
     * Salva o aggiorna un server locale registrato. Operazione idempotente
     * basata sulla chiave primaria {@code buildingId}.
     *
     * @param server il server locale registrato da persistere
     * @return il server locale registrato persistito
     */
    RegisteredLocalServerLocal save(RegisteredLocalServerLocal server);

    /**
     * Cerca un server locale registrato in base all'identificativo dell'edificio.
     *
     * @param buildingId l'identificativo dell'edificio
     * @return un {@code Optional} contenente il server locale, vuoto se non trovato
     */
    Optional<RegisteredLocalServerLocal> findById(String buildingId);

    /**
     * Restituisce tutti i server locali registrati.
     *
     * @return la lista completa dei server locali registrati
     */
    List<RegisteredLocalServerLocal> findAll();

    /**
     * Elimina un server locale registrato in base all'identificativo dell'edificio.
     *
     * @param buildingId l'identificativo dell'edificio del server da eliminare
     */
    void deleteById(String buildingId);

    /**
     * Imposta il flag {@code active} per il server locale identificato da
     * {@code buildingId}. Restituisce il server aggiornato, o vuoto se
     * l'edificio non esiste nella replica locale.
     *
     * @param buildingId l'identificativo dell'edificio
     * @param active     il nuovo valore del flag di attivazione
     * @return un {@code Optional} contenente il server aggiornato, vuoto se non trovato
     */
    Optional<RegisteredLocalServerLocal> setActive(String buildingId, boolean active);
}