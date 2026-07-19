package com.gameplatform.shared.domain.model;

/**
 * Enumera i motivi che determinano l'interruzione di una sessione di gioco.
 *
 * <p>Ogni costante rappresenta una causa distinta di arresto, utile per tracciare
 * e classificare il termine di una partita all'interno della piattaforma.</p>
 *
 * @see com.gameplatform.shared.domain.model.GameSession
 */
public enum StopReason {
    /** Indica che la sessione di gioco è stata completata con regolare terminazione. */
    COMPLETED,
    /** Indica che la sessione di gioco è stata interrotta anticipatamente dall'utente o dal sistema. */
    ABORTED,
    /** Indica che la sessione di gioco è terminata per scadenza del tempo disponibile. */
    TIMEOUT
}
