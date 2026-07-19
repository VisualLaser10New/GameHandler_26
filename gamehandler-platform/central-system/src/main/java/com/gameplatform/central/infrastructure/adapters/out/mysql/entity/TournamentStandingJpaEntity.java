package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Entità JPA per la tabella {@code tournament_standings} del database MySQL.
 *
 * <p>Rappresenta la classifica di un partecipante all'interno di un torneo, con
 * il conteggio di vittorie, sconfitte, punti e la posizione in classifica.
 * Utilizza una chiave primaria composita ({@code tournament_id},
 * {@code participant_id}) tramite {@link IdClass}. La posizione ({@code rank})
 * può essere {@code null} finché la classifica non è stata determinata. Non sono
 * dichiarate relazioni JPA: torneo e partecipante sono referenziati tramite i
 * propri identificativi testuali, secondo la convenzione esagonale adottata nel
 * progetto.</p>
 *
 * @see TournamentStandingId
 * @see TournamentParticipantJpaEntity
 */
@Entity
@Table(name = "tournament_standings")
@IdClass(TournamentStandingId.class)
public class TournamentStandingJpaEntity {
    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;
    @Id
    @Column(name = "participant_id", length = 36, nullable = false)
    private String participantId;
    @Column(name = "wins", nullable = false)
    private Integer wins;
    @Column(name = "losses", nullable = false)
    private Integer losses;
    @Column(name = "points", nullable = false)
    private Integer points;
    @Column(name = "`rank`")
    private Integer rank;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public TournamentStandingJpaEntity() {
    }

    /**
     * Costruisce una voce di classifica per un partecipante all'interno di un torneo.
     *
     * @param tournamentId identificativo del torneo; non deve essere {@code null}
     * @param participantId identificativo del partecipante; non deve essere {@code null}
     * @param wins numero di vittorie; non deve essere {@code null} e non negativo
     * @param losses numero di sconfitte; non deve essere {@code null} e non negativo
     * @param points punteggio totale; non deve essere {@code null} e non negativo
     * @param rank posizione in classifica; può essere {@code null} se non ancora determinata
     */
    public TournamentStandingJpaEntity(String tournamentId, String participantId, Integer wins, Integer losses,
                                       Integer points, Integer rank) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.wins = wins;
        this.losses = losses;
        this.points = points;
        this.rank = rank;
    }

    /**
     * Restituisce l'identificativo del torneo di riferimento.
     *
     * @return l'identificativo del torneo; non deve essere {@code null}
     */
    public String getTournamentId() { return tournamentId; }

    /**
     * Imposta l'identificativo del torneo di riferimento.
     *
     * @param tournamentId nuovo identificativo del torneo; non deve essere {@code null}
     */
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

    /**
     * Restituisce l'identificativo del partecipante di riferimento.
     *
     * @return l'identificativo del partecipante; non deve essere {@code null}
     */
    public String getParticipantId() { return participantId; }

    /**
     * Imposta l'identificativo del partecipante di riferimento.
     *
     * @param participantId nuovo identificativo del partecipante; non deve essere {@code null}
     */
    public void setParticipantId(String participantId) { this.participantId = participantId; }

    /**
     * Restituisce il numero di vittorie del partecipante nel torneo.
     *
     * @return il numero di vittorie; non deve essere {@code null} e non negativo
     */
    public Integer getWins() { return wins; }

    /**
     * Imposta il numero di vittorie del partecipante nel torneo.
     *
     * @param wins nuovo numero di vittorie; non deve essere {@code null} e non negativo
     */
    public void setWins(Integer wins) { this.wins = wins; }

    /**
     * Restituisce il numero di sconfitte del partecipante nel torneo.
     *
     * @return il numero di sconfitte; non deve essere {@code null} e non negativo
     */
    public Integer getLosses() { return losses; }

    /**
     * Imposta il numero di sconfitte del partecipante nel torneo.
     *
     * @param losses nuovo numero di sconfitte; non deve essere {@code null} e non negativo
     */
    public void setLosses(Integer losses) { this.losses = losses; }

    /**
     * Restituisce il punteggio totale del partecipante nel torneo.
     *
     * @return il punteggio totale; non deve essere {@code null} e non negativo
     */
    public Integer getPoints() { return points; }

    /**
     * Imposta il punteggio totale del partecipante nel torneo.
     *
     * @param points nuovo punteggio totale; non deve essere {@code null} e non negativo
     */
    public void setPoints(Integer points) { this.points = points; }

    /**
     * Restituisce la posizione in classifica del partecipante.
     *
     * @return la posizione in classifica; può essere {@code null} se non ancora determinata
     */
    public Integer getRank() { return rank; }

    /**
     * Imposta la posizione in classifica del partecipante.
     *
     * @param rank nuova posizione in classifica; può essere {@code null}
     */
    public void setRank(Integer rank) { this.rank = rank; }
}