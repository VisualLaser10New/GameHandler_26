package com.gameplatform.shared.domain.model;

/**
 * Enumera i tipi di gioco supportati dalla piattaforma.
 *
 * <p>Ogni costante rappresenta una categoria di gioco distinta gestita dal sistema,
 * utilizzata per identificare, classificare e instradare le richieste verso il
 * gestore di gioco appropriato.</p>
 *
 * @see com.gameplatform.shared.domain.model.Game
 */
public enum GameType {
    CHESS,
    FOOSBALL,
    DARTS,
    MONOPOLY,
    RISK,
    SLOT_MACHINE,
    ROULETTE
}
