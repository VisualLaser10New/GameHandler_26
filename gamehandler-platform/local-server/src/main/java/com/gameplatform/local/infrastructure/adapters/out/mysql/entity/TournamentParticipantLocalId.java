package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link TournamentParticipantLocalJpaEntity}
 * ({@code tournament_id}, {@code participant_id}) — mirror of the Central
 * {@code TournamentParticipantId} shape.
 */
public class TournamentParticipantLocalId implements Serializable {

    private String tournamentId;
    private String participantId;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public TournamentParticipantLocalId() {
    }

    /**
     * Costruisce una chiave composita con i valori specificati.
     *
     * @param tournamentId identificativo del torneo
     * @param participantId identificativo del partecipante
     */
    public TournamentParticipantLocalId(String tournamentId, String participantId) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
    }

    /**
     * Restituisce l'identificativo del torneo.
     *
     * @return tournamentId
     */
    public String getTournamentId() {
        return tournamentId;
    }

    /**
     * Imposta l'identificativo del torneo.
     *
     * @param tournamentId nuovo identificativo torneo
     */
    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    /**
     * Restituisce l'identificativo del partecipante.
     *
     * @return participantId
     */
    public String getParticipantId() {
        return participantId;
    }

    /**
     * Imposta l'identificativo del partecipante.
     *
     * @param participantId nuovo identificativo partecipante
     */
    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    /**
     * Confronta questa chiave con l'oggetto specificato per verificarne l'uguaglianza.
     *
     * @param o oggetto da confrontare
     * @return {@code true} se i due oggetti hanno gli stessi tournamentId e participantId
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentParticipantLocalId that = (TournamentParticipantLocalId) o;
        return Objects.equals(tournamentId, that.tournamentId)
                && Objects.equals(participantId, that.participantId);
    }

    /**
     * Restituisce il codice hash basato su tournamentId e participantId.
     *
     * @return codice hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, participantId);
    }
}
