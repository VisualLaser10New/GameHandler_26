package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.GameMachineStatus;

/**
 * DTO che rappresenta lo stato di un gioco all'interno della piattaforma.
 *
 * <p>Contiene le informazioni essenziali per identificare un gioco, il relativo
 * edificio di appartenenza, il tipo, lo stato della macchina e i limiti di
 * giocatori supportati.</p>
 *
 * @see com.gameplatform.shared.domain.model.GameType
 * @see com.gameplatform.shared.domain.model.GameMachineStatus
 */
public record GameStateDto(
    String gameId,
    GameType gameType,
    String name,
    String buildingId,
    GameMachineStatus status,
    int minPlayers,
    int maxPlayers
) {
    /**
     * Costruisce un'istanza di {@code GameStateDto} per i chiamanti che non
     * specificano i limiti di giocatori.
     *
     * <p>Imposta {@code minPlayers} a {@code 1} e {@code maxPlayers} a
     * {@link Integer#MAX_VALUE}, garantendo la compatibilità con le versioni
     * precedenti dell'API.</p>
     *
     * @param gameId      identificativo univoco del gioco; non deve essere {@code null}
     * @param gameType    tipo di gioco; non deve essere {@code null}
     * @param name        nome del gioco; non deve essere {@code null} nè vuoto
     * @param buildingId  identificativo dell'edificio di appartenenza; non deve essere {@code null}
     * @param status      stato della macchina da gioco; non deve essere {@code null}
     *
     * @see #GameStateDto(String, GameType, String, String, GameMachineStatus, int, int)
     */
    public GameStateDto(String gameId, GameType gameType, String name, String buildingId, GameMachineStatus status) {
        this(gameId, gameType, name, buildingId, status, 1, Integer.MAX_VALUE);
    }
}
