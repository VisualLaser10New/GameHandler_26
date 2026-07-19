package com.gameplatform.central.domain.ports.in;

import com.gameplatform.central.domain.model.GameDefinition;

/**
 * Caso d'uso che crea o aggiorna una definizione di gioco nel sistema centrale.
 */
public interface UpsertGameDefinitionUseCase {

    /**
     * Inserisce o aggiorna la definizione di gioco fornita.
     *
     * @param gameDefinition la definizione di gioco da creare o aggiornare; non deve essere {@code null}
     * @param originatingRequestId l'identificativo della richiesta origine per la tracciabilità; può essere {@code null} nel caso di chiamata REST diretta
     * @return la {@link GameDefinition} persistita risultante dall'operazione
     * @throws IllegalArgumentException se la definizione di gioco è {@code null}
     * @see #upsert(GameDefinition)
     */
    GameDefinition upsert(GameDefinition gameDefinition, String originatingRequestId);

    /**
     * Inserisce o aggiorna la definizione di gioco tramite chiamata REST diretta, senza identificativo di origine.
     *
     * @param gameDefinition la definizione di gioco da creare o aggiornare; non deve essere {@code null}
     * @return la {@link GameDefinition} persistita risultante dall'operazione
     * @throws IllegalArgumentException se la definizione di gioco è {@code null}
     * @see #upsert(GameDefinition, String)
     */
    default GameDefinition upsert(GameDefinition gameDefinition) {
        return upsert(gameDefinition, null);
    }
}