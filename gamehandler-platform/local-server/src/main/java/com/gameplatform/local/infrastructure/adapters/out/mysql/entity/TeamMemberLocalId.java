package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link TeamMemberLocalJpaEntity}
 * ({@code tournament_id}, {@code team_id}, {@code user_id}) — local mirror of
 * the Central {@code TournamentTeamMemberId} shape extended with
 * {@code tournament_id} (BUG-TEAM-3).
 */
public class TeamMemberLocalId implements Serializable {

    private String tournamentId;
    private String teamId;
    private String userId;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public TeamMemberLocalId() {
    }

    /**
     * Costruisce una chiave composita con i valori specificati.
     *
     * @param tournamentId identificativo del torneo
     * @param teamId       identificativo della squadra
     * @param userId       identificativo dell'utente membro
     */
    public TeamMemberLocalId(String tournamentId, String teamId, String userId) {
        this.tournamentId = tournamentId;
        this.teamId = teamId;
        this.userId = userId;
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
     * Restituisce l'identificativo della squadra.
     *
     * @return teamId
     */
    public String getTeamId() {
        return teamId;
    }

    /**
     * Imposta l'identificativo della squadra.
     *
     * @param teamId nuovo identificativo squadra
     */
    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    /**
     * Restituisce l'identificativo dell'utente membro.
     *
     * @return userId
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Imposta l'identificativo dell'utente membro.
     *
     * @param userId nuovo identificativo utente
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Confronta questa chiave con l'oggetto specificato per verificarne l'uguaglianza.
     *
     * @param o oggetto da confrontare
     * @return {@code true} se i due oggetti hanno gli stessi tournamentId, teamId e userId
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeamMemberLocalId that = (TeamMemberLocalId) o;
        return Objects.equals(tournamentId, that.tournamentId)
                && Objects.equals(teamId, that.teamId)
                && Objects.equals(userId, that.userId);
    }

    /**
     * Restituisce il codice hash basato su tournamentId, teamId e userId.
     *
     * @return codice hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, teamId, userId);
    }
}