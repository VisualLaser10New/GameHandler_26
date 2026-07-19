package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.WinCondition;

/**
 * DTO che rappresenta una sessione di gioco all'interno della piattaforma.
 * Trasporta i dati essenziali relativi a un'iniziativa di gioco, al suo stato,
 * al risultato e ai partecipanti coinvolti.
 *
 * @see com.gameplatform.shared.domain.model.GameType
 * @see com.gameplatform.shared.domain.model.GameStatus
 * @see com.gameplatform.shared.domain.model.WinCondition
 */
public record GameSessionDto(
    String id,
    String gameId,
    GameType gameType,
    GameStatus status,
    Instant startedAt,
    Instant endedAt,
    Integer durationSeconds,
    String winnerId,
    WinCondition winCondition,
    String resultData,
    List<String> participants
) {
    /**
     * Costruttore canonico della sessione di gioco.
     * Normalizza il parametro {@code participants}: se questo è {@code null},
     * lo sostituisce con una lista vuota, in modo che la sessione possegga
     * sempre una collezione non {@code null} di partecipanti.
     *
     * @param id               identificativo univoco della sessione; non deve essere {@code null}
     * @param gameId           identificativo del gioco di riferimento; non deve essere {@code null}
     * @param gameType         tipologia di gioco associata alla sessione; non deve essere {@code null}
     * @param status           stato corrente della sessione; non deve essere {@code null}
     * @param startedAt        istante di avvio della sessione; può essere {@code null} se non ancora avviata
     * @param endedAt          istante di conclusione della sessione; può essere {@code null} se ancora in corso
     * @param durationSeconds  durata della sessione in secondi; può essere {@code null} se non disponibile
     * @param winnerId         identificativo del vincitore; può essere {@code null} in caso di nessun vincitore
     * @param winCondition     condizione di vittoria applicata; può essere {@code null} se non definita
     * @param resultData       dati aggiuntivi sul risultato; può essere {@code null} o vuoto
     * @param participants     lista dei partecipanti alla sessione; se {@code null} viene impostata a lista vuota
     */
    public GameSessionDto {
        if (participants == null) {
            participants = List.of();
        }
    }

    /**
     * Costruttore di comodo che crea una sessione di gioco senza specificare
     * alcun partecipante. Equivale a invocare il costruttore canonico passando
     * una lista vuota per i partecipanti.
     *
     * @param id               identificativo univoco della sessione; non deve essere {@code null}
     * @param gameId           identificativo del gioco di riferimento; non deve essere {@code null}
     * @param gameType         tipologia di gioco associata alla sessione; non deve essere {@code null}
     * @param status           stato corrente della sessione; non deve essere {@code null}
     * @param startedAt        istante di avvio della sessione; può essere {@code null} se non ancora avviata
     * @param endedAt          istante di conclusione della sessione; può essere {@code null} se ancora in corso
     * @param durationSeconds  durata della sessione in secondi; può essere {@code null} se non disponibile
     * @param winnerId         identificativo del vincitore; può essere {@code null} in caso di nessun vincitore
     * @param winCondition     condizione di vittoria applicata; può essere {@code null} se non definita
     * @param resultData       dati aggiuntivi sul risultato; può essere {@code null} o vuoto
     * @see #GameSessionDto(String, String, GameType, GameStatus, Instant, Instant, Integer, String, WinCondition, String, List)
     */
    public GameSessionDto(String id, String gameId, GameType gameType, GameStatus status,
                          Instant startedAt, Instant endedAt, Integer durationSeconds,
                          String winnerId, WinCondition winCondition, String resultData) {
        this(id, gameId, gameType, status, startedAt, endedAt, durationSeconds,
                winnerId, winCondition, resultData, List.of());
    }
}
