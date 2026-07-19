package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import java.util.Optional;

/**
 * Use case per l'attivazione o disattivazione del flag di attivit&agrave;
 * di un server locale. Modifica lo stato del server replicato localmente
 * identificato dalla struttura.
 *
 * @see com.gameplatform.local.domain.model.RegisteredLocalServerLocal
 */
public interface ToggleLocalServerActiveUseCase {

    /**
     * Applica il flag {@code active} richiesto alla riga di proiezione del server locale.
     *
     * @param buildingId identificativo della struttura del server
     * @param active     valore booleano del flag di attivit&agrave;
     * @return un {@code Optional} contenente il server locale aggiornato, oppure vuoto se non trovato
     */
    Optional<RegisteredLocalServerLocal> setActive(String buildingId, boolean active);
}