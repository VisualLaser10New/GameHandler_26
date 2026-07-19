package com.gameplatform.shared.domain.model;

/**
 * Enumera i possibili stati di avanzamento di un gioco all'interno della piattaforma.
 *
 * <p>Ogni costante rappresenta una fase del ciclo di vita di una partita, dall'attesa
 * di giocatori fino al suo completamento o annullamento. Gli stati sono mutuamente
 * esclusivi e indicano lo stato corrente di una singola istanza di gioco.</p>
 *
 * @see com.gameplatform.shared.domain.model.Game
 */
public enum GameStatus {
    /**
     * Stato in cui il gioco è in attesa che i giocatori necessari si uniscano
     * prima dell'avvio effettivo della partita.
     */
    WAITING,

    /**
     * Stato in cui il gioco è in corso e le sue regole sono attive.
     */
    IN_PROGRESS,

    /**
     * Stato in cui il gioco è temporaneamente sospeso mantenendo lo stato
     * corrente della partita, che può essere ripristinato.
     */
    PAUSED,

    /**
     * Stato in cui il gioco è terminato regolarmente con un esito definito.
     */
    COMPLETED,

    /**
     * Stato in cui il gioco è stato interrotto anticipatamente senza esito
     * conclusivo.
     */
    ABORTED
}
