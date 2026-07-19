package com.gameplatform.shared.dto;

/**
 * DTO (Data Transfer Object) che rappresenta un partecipante a un torneo
 * all'interno della piattaforma di gioco.
 *
 * <p>Incapsula le informazioni essenziali di un partecipante, distinguendo
 * se si tratta di un singolo giocatore o di una squadra, e ne fornisce
 * il nome da visualizzare.</p>
 *
 * @see com.gameplatform.shared.dto
 */
public record TournamentParticipantDto(
        /**
         * Identificativo univoco del partecipante al torneo.
         */
        String participantId,
        /**
         * Flag che indica se il partecipante rappresenta una squadra ({@code true})
         * oppure un singolo giocatore ({@code false}).
         */
        boolean isTeam,
        /**
         * Nome da visualizzare del partecipante, utilizzato nell'interfaccia utente.
         */
        String displayName
) {

    /**
     * Costruisce un nuovo {@code TournamentParticipantDto} a partire dai valori
     * dei suoi componenti.
     *
     * @param participantId identificativo univoco del partecipante
     * @param isTeam        {@code true} se il partecipante &egrave; una squadra,
     *                      {@code false} se &egrave; un singolo giocatore
     * @param displayName   nome da visualizzare del partecipante
     */
    public TournamentParticipantDto {
    }

    /**
     * Restituisce l'identificativo univoco del partecipante al torneo.
     *
     * @return l'identificativo del partecipante
     */
    public String participantId() {
        return participantId;
    }

    /**
     * Indica se il partecipante rappresenta una squadra o un singolo giocatore.
     *
     * @return {@code true} se il partecipante &egrave; una squadra,
     *         {@code false} se &egrave; un singolo giocatore
     */
    public boolean isTeam() {
        return isTeam;
    }

    /**
     * Restituisce il nome da visualizzare del partecipante.
     *
     * @return il nome da visualizzare
     */
    public String displayName() {
        return displayName;
    }
}
