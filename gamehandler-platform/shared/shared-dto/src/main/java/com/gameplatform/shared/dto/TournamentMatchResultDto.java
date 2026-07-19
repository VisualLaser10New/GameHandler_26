package com.gameplatform.shared.dto;

/**
 * DTO che rappresenta il risultato di un incontro di torneo.
 *
 * <p>Contiene le informazioni essenziali sull'esito di una partita, identificandola
 * univocamente, riportando il vincitore, i dati dettagliati del risultato e lo stato
 * dell'incontro.</p>
 *
 * @see com.gameplatform.shared.dto
 */
public record TournamentMatchResultDto(
        /**
         * Restituisce l'identificativo univoco dell'incontro.
         *
         * @return l'ID della partita; non deve essere {@code null} né vuoto.
         */
        String matchId,
        /**
         * Restituisce l'identificativo del giocatore o della squadra vincitrice.
         *
         * @return il vincitore dell'incontro; può essere {@code null} in caso di
         *         pareggio o di incontro non ancora concluso.
         */
        String winner,
        /**
         * Restituisce i dati dettagliati del risultato dell'incontro.
         *
         * @return i dati aggiuntivi del risultato (es. punteggi, statistiche);
         *         può essere {@code null} o vuoto se non disponibili.
         */
        String resultData,
        /**
         * Restituisce lo stato corrente dell'incontro.
         *
         * @return lo stato della partita (es. completato, in corso, annullato);
         *         non deve essere {@code null} né vuoto.
         */
        String status
) {
}
