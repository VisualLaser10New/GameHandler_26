package com.gameplatform.shared.domain.model;

/**
 * Enumera i formati di torneo supportati dalla piattaforma.
 *
 * <p>Ogni costante rappresenta una modalità di svolgimento delle competizioni:
 * l'eliminazione diretta e il girone all'italiana. Il formato determina la
 * struttura degli incontri e la logica di assegnazione dei risultati.</p>
 *
 * @see Tournament
 */
public enum TournamentFormat {
    /**
     * Formato a eliminazione diretta: ogni sconfitta determina l'eliminazione
     * del partecipante dal torneo.
     */
    SINGLE_ELIMINATION,

    /**
     * Formato a girone all'italiana: ogni partecipante affronta tutti gli altri
     * almeno una volta e il risultato finale dipende dalla classifica complessiva.
     */
    ROUND_ROBIN
}