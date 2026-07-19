package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.shared.domain.model.GameType;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza per le definizioni di gioco.
 *
 * <p>Espone le operazioni di salvataggio, ricerca e cancellazione delle
 * definizioni di gioco identificate univocamente per tipo di gioco, utili al
 * dominio per gestire il catalogo dei giochi disponibili sulla piattaforma.</p>
 *
 * @see GameDefinition
 * @see GameType
 */
public interface GameDefinitionRepository {

    /**
     * Salva o aggiorna la definizione di gioco fornita.
     *
     * @param gameDefinition la definizione di gioco da persistere; non deve essere {@code null}
     * @return la definizione di gioco salvata, eventualmente arricchita di metadati di persistenza
     * @throws IllegalArgumentException se {@code gameDefinition} è {@code null}
     */
    GameDefinition save(GameDefinition gameDefinition);

    /**
     * Ricerca la definizione di gioco associata al tipo di gioco indicato.
     *
     * @param gameType il tipo di gioco di cui cercare la definizione; non deve essere {@code null}
     * @return un {@link Optional} contenente la definizione trovata, o vuoto se nessuna definizione esiste per il tipo
     * @throws IllegalArgumentException se {@code gameType} è {@code null}
     */
    Optional<GameDefinition> findByGameType(GameType gameType);

    /**
     * Restituisce tutte le definizioni di gioco persistite.
     *
     * @return la lista delle definizioni di gioco; mai {@code null}, eventualmente vuota se non ce ne sono
     */
    List<GameDefinition> findAll();

    /**
     * Verifica l'esistenza di una definizione di gioco per il tipo indicato.
     *
     * @param gameType il tipo di gioco da verificare; non deve essere {@code null}
     * @return {@code true} se esiste una definizione per il tipo, {@code false} altrimenti
     * @throws IllegalArgumentException se {@code gameType} è {@code null}
     */
    boolean existsByGameType(GameType gameType);

    /**
     * Elimina la definizione di gioco associata al tipo indicato, se presente.
     *
     * <p>Se nessuna definizione esiste per il tipo di gioco, l'operazione non ha effetto.</p>
     *
     * @param gameType il tipo di gioco di cui eliminare la definizione; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code gameType} è {@code null}
     */
    void deleteByGameType(GameType gameType);
}
