package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Entità JPA per la tabella {@code tournament_team_members} del database MySQL.
 *
 * <p>Rappresenta l'appartenenza di un utente a una squadra di torneo, modellata
 * come tabella di legame. Utilizza una chiave primaria composita
 * ({@code team_id}, {@code user_id}) tramite {@link IdClass}. Non sono dichiarate
 * relazioni JPA: squadra e utente sono referenziati tramite i propri
 * identificativi testuali, secondo la convenzione esagonale adottata nel
 * progetto.</p>
 *
 * @see TournamentTeamMemberId
 * @see TournamentTeamJpaEntity
 */
@Entity
@Table(name = "tournament_team_members")
@IdClass(TournamentTeamMemberId.class)
public class TournamentTeamMemberJpaEntity {
    @Id
    @Column(name = "team_id", length = 36, nullable = false)
    private String teamId;
    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public TournamentTeamMemberJpaEntity() {
    }

    /**
     * Costruisce l'associazione tra una squadra e un utente membro.
     *
     * @param teamId identificativo della squadra; non deve essere {@code null}
     * @param userId identificativo dell'utente membro; non deve essere {@code null}
     */
    public TournamentTeamMemberJpaEntity(String teamId, String userId) {
        this.teamId = teamId;
        this.userId = userId;
    }

    /**
     * Restituisce l'identificativo della squadra di appartenenza.
     *
     * @return l'identificativo della squadra; non deve essere {@code null}
     */
    public String getTeamId() { return teamId; }

    /**
     * Imposta l'identificativo della squadra di appartenenza.
     *
     * @param teamId nuovo identificativo della squadra; non deve essere {@code null}
     */
    public void setTeamId(String teamId) { this.teamId = teamId; }

    /**
     * Restituisce l'identificativo dell'utente membro.
     *
     * @return l'identificativo dell'utente; non deve essere {@code null}
     */
    public String getUserId() { return userId; }

    /**
     * Imposta l'identificativo dell'utente membro.
     *
     * @param userId nuovo identificativo dell'utente; non deve essere {@code null}
     */
    public void setUserId(String userId) { this.userId = userId; }
}