package com.gameplatform.central.domain.ports.in;

import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.shared.domain.model.GameType;
import java.util.List;
import java.util.Optional;

/**
 * Caso d'uso di lettura che espone le definizioni di gioco disponibili
 * nel sistema centrale.
 */
public interface ListGameDefinitionsUseCase {

    /**
     * Restituisce l'elenco completo delle definizioni di gioco.
     *
     * @return la lista di {@link GameDefinition} disponibili; la lista è vuota se non è definito alcun gioco
     */
    List<GameDefinition> findAll();

    /**
     * Restituisce la definizione di gioco corrispondente al tipo indicato.
     *
     * @param gameType il tipo di gioco di cui recuperare la definizione; non deve essere {@code null}
     * @return un {@link Optional} contenente la {@link GameDefinition} se esiste, altrimenti un {@link Optional} vuoto
     */
    Optional<GameDefinition> findByGameType(GameType gameType);
}
