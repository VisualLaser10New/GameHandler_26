package com.gameplatform.shared.dto;

/**
 * DTO che rappresenta la classifica di un partecipante all'interno di un torneo.
 * Contiene i dati identificativi del giocatore, il conteggio delle vittorie e delle
 * sconfitte, i punti totali accumulati e la posizione in classifica (opzionale).
 *
 * @see com.gameplatform.shared.dto.TournamentSummaryDto
 */
public record TournamentStandingDto(
        /**
         * Restituisce l'identificativo univoco del partecipante.
         *
         * @return l'ID del partecipante
         */
        String participantId,
        /**
         * Restituisce il nome visualizzato del partecipante.
         *
         * @return il nome mostrato in classifica
         */
        String displayName,
        /**
         * Restituisce il numero di vittorie ottenute dal partecipante.
         *
         * @return il conteggio delle vittorie
         */
        int wins,
        /**
         * Restituisce il numero di sconfitte subite dal partecipante.
         *
         * @return il conteggio delle sconfitte
         */
        int losses,
        /**
         * Restituisce i punti totali accumulati dal partecipante nel torneo.
         *
         * @return il punteggio complessivo
         */
        int points,
        /**
         * Restituisce la posizione in classifica del partecipante.
         *
         * @return il rank del partecipante, o {@code null} se non ancora assegnato
         */
        Integer rank
) {
}
