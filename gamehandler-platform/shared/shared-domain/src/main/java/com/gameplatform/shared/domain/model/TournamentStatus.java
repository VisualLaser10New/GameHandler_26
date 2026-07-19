package com.gameplatform.shared.domain.model;

/**
 * Enumera gli stati possibili in cui si pu&ograve; trovare un torneo nel suo ciclo di vita.
 *
 * <p>Ciascun valore rappresenta una fase ben definita, dall'ideazione alla chiusura o
 * all'annullamento dell'evento. Gli stati sono mutuamente esclusivi e descrivono
 * l'avanzamento delle iscrizioni e dello svolgimento delle partite.</p>
 *
 * @see com.gameplatform.shared.domain.model.Tournament
 */
public enum TournamentStatus {
    /**
     * Indica che il torneo &egrave; in fase di bozza e non &egrave; ancora pubblico.
     *
     * <p>In questo stato il torneo non accetta iscrizioni e non &egrave; visibile ai giocatori.</p>
     */
    DRAFT,

    /**
     * Indica che il torneo &egrave; aperto alle registrazioni dei partecipanti.
     *
     * <p>In questo stato le iscrizioni sono consentite e il torneo non &egrave; ancora iniziato.</p>
     */
    OPEN_REGISTRATION,

    /**
     * Indica che il torneo &egrave; in corso di svolgimento.
     *
     * <p>In questo stato le iscrizioni sono chiuse e le partite sono in fase di esecuzione.</p>
     */
    IN_PROGRESS,

    /**
     * Indica che il torneo &egrave; stato completato con successo.
     *
     * <p>In questo stato tutte le partite sono terminate e il vincitore &egrave; stato determinato.</p>
     */
    COMPLETED,

    /**
     * Indica che il torneo &egrave; stato annullato prima del completamento.
     *
     * <p>In questo stato il torneo non &egrave; pi&ugrave; attivo e nessuna ulteriore operazione &egrave; prevista.</p>
     */
    CANCELLED
}