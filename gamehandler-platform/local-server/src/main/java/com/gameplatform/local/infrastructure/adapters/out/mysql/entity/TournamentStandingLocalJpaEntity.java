package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code tournament_standings_local} (PIANO §7.B).
 * Read-only replica updated only by sync; composite PK
 * ({@code tournamentId}, {@code participantId}) via
 * {@link TournamentStandingLocalId}; no {@code @OneToMany}, no
 * {@code @Version} (mirror of {@code TournamentMatchLocalJpaEntity}).
 */
@Entity
@Table(name = "tournament_standings_local", indexes = {
        @Index(name = "idx_tsl_tournament", columnList = "tournament_id")
})
@IdClass(TournamentStandingLocalId.class)
public class TournamentStandingLocalJpaEntity {

    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;

    @Id
    @Column(name = "participant_id", length = 64, nullable = false)
    private String participantId;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Column(name = "wins", nullable = false)
    private Integer wins;

    @Column(name = "losses", nullable = false)
    private Integer losses;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "`rank`")
    private Integer rank;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public TournamentStandingLocalJpaEntity() {
    }

    /**
     * Costruisce una nuova classifica torneo locale con tutti i campi.
     *
     * @param tournamentId  identificativo del torneo
     * @param participantId identificativo del partecipante
     * @param displayName   nome visualizzato del partecipante
     * @param wins          numero di vittorie
     * @param losses        numero di sconfitte
     * @param points        punteggio totale
     * @param rank          posizione in classifica (può essere {@code null})
     * @param updatedAt     istante dell'ultimo aggiornamento
     */
    public TournamentStandingLocalJpaEntity(String tournamentId, String participantId, String displayName,
                                            Integer wins, Integer losses, Integer points, Integer rank,
                                            Instant updatedAt) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.displayName = displayName;
        this.wins = wins;
        this.losses = losses;
        this.points = points;
        this.rank = rank;
        this.updatedAt = updatedAt;
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
     * Restituisce il nome visualizzato del partecipante.
     *
     * @return displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Imposta il nome visualizzato del partecipante.
     *
     * @param displayName nuovo nome visualizzato
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Restituisce il numero di vittorie.
     *
     * @return wins
     */
    public Integer getWins() {
        return wins;
    }

    /**
     * Imposta il numero di vittorie.
     *
     * @param wins nuovo numero vittorie
     */
    public void setWins(Integer wins) {
        this.wins = wins;
    }

    /**
     * Restituisce il numero di sconfitte.
     *
     * @return losses
     */
    public Integer getLosses() {
        return losses;
    }

    /**
     * Imposta il numero di sconfitte.
     *
     * @param losses nuovo numero sconfitte
     */
    public void setLosses(Integer losses) {
        this.losses = losses;
    }

    /**
     * Restituisce il punteggio totale.
     *
     * @return points
     */
    public Integer getPoints() {
        return points;
    }

    /**
     * Imposta il punteggio totale.
     *
     * @param points nuovo punteggio
     */
    public void setPoints(Integer points) {
        this.points = points;
    }

    /**
     * Restituisce la posizione in classifica.
     *
     * @return rank (può essere {@code null})
     */
    public Integer getRank() {
        return rank;
    }

    /**
     * Imposta la posizione in classifica.
     *
     * @param rank nuova posizione
     */
    public void setRank(Integer rank) {
        this.rank = rank;
    }

    /**
     * Restituisce l'istante dell'ultimo aggiornamento.
     *
     * @return updatedAt
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Imposta l'istante dell'ultimo aggiornamento.
     *
     * @param updatedAt nuovo istante di aggiornamento
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
