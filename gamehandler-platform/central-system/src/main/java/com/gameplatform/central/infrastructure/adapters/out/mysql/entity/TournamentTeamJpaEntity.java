package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entità JPA per la tabella {@code tournament_teams} del database MySQL.
 *
 * <p>Rappresenta una squadra iscritta a un torneo, identificata univocamente e
 * associata al torneo di appartenenza. La chiave primaria è l'identificativo
 * della squadra. Non sono dichiarate relazioni JPA: il torneo è referenziato
 * tramite il proprio identificativo testuale, secondo la convenzione esagonale
 * adottata nel progetto. I componenti della squadra sono modellati dalla tabella
 * {@code tournament_team_members}.</p>
 *
 * @see TournamentTeamMemberJpaEntity
 * @see TournamentJpaEntity
 */
@Entity
@Table(name = "tournament_teams")
public class TournamentTeamJpaEntity {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;
    @Column(name = "name", length = 200, nullable = false)
    private String name;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public TournamentTeamJpaEntity() {
    }

    /**
     * Costruisce una squadra di torneo con i dati forniti.
     *
     * @param id identificativo univoco della squadra; non deve essere {@code null}
     * @param tournamentId identificativo del torneo di appartenenza; non deve essere {@code null}
     * @param name nome della squadra; non deve essere {@code null}
     * @param createdAt istante di creazione della squadra; non deve essere {@code null}
     */
    public TournamentTeamJpaEntity(String id, String tournamentId, String name, Instant createdAt) {
        this.id = id;
        this.tournamentId = tournamentId;
        this.name = name;
        this.createdAt = createdAt;
    }

    /**
     * Restituisce l'identificativo univoco della squadra.
     *
     * @return l'identificativo della squadra; non deve essere {@code null}
     */
    public String getId() { return id; }

    /**
     * Imposta l'identificativo univoco della squadra.
     *
     * @param id nuovo identificativo della squadra; può essere {@code null}
     */
    public void setId(String id) { this.id = id; }

    /**
     * Restituisce l'identificativo del torneo di appartenenza.
     *
     * @return l'identificativo del torneo; non deve essere {@code null}
     */
    public String getTournamentId() { return tournamentId; }

    /**
     * Imposta l'identificativo del torneo di appartenenza.
     *
     * @param tournamentId nuovo identificativo del torneo; non deve essere {@code null}
     */
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

    /**
     * Restituisce il nome della squadra.
     *
     * @return il nome della squadra; non deve essere {@code null}
     */
    public String getName() { return name; }

    /**
     * Imposta il nome della squadra.
     *
     * @param name nuovo nome della squadra; non deve essere {@code null}
     */
    public void setName(String name) { this.name = name; }

    /**
     * Restituisce l'istante di creazione della squadra.
     *
     * @return l'istante di creazione; non deve essere {@code null}
     */
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Imposta l'istante di creazione della squadra.
     *
     * @param createdAt nuovo istante di creazione; non deve essere {@code null}
     */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}