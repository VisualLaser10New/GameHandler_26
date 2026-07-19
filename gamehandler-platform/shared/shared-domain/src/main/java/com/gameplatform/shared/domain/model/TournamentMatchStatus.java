package com.gameplatform.shared.domain.model;

/**
 * Enumera gli stati possibili di un incontro all'interno di un torneo.
 *
 * <p>Ogni costante rappresenta una fase del ciclo di vita di un match, dall'attesa di inizio
 * alla conclusione o all'abbandono, includendo il caso particolare di un turno non giocato.</p>
 *
 * @see TournamentMatch
 */
public enum TournamentMatchStatus {
    /**
     * Indica che l'incontro è stato pianificato ma non è ancora iniziato.
     */
    SCHEDULED,

    /**
     * Indica che l'incontro è attualmente in corso di svolgimento.
     */
    IN_PROGRESS,

    /**
     * Indica che l'incontro è terminato regolarmente e ha prodotto un risultato.
     */
    COMPLETED,

    /**
     * Indica che l'incontro è stato interrotto prima del termine senza un risultato valido.
     */
    ABANDONED,

    /**
     * Indica un turno automaticamente assegnato a un giocatore in assenza di un avversario.
     */
    BYE
}