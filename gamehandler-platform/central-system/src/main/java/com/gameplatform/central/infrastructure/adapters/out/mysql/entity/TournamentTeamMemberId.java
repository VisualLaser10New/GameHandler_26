package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Classe che rappresenta la chiave primaria composita per
 * {@link TournamentTeamMemberJpaEntity}, coerente con l'appartenenza di un
 * utente a una squadra di torneo.
 *
 * <p>I nomi dei campi devono corrispondere esattamente ai nomi dei campi
 * {@code @Id} dell'entità affinché Hibernate possa valorizzarli tramite
 * reflection. La classe implementa {@link Serializable} e ridefinisce
 * {@link #equals(Object)} e {@link #hashCode()} basandosi sui due attributi
 * che compongono la chiave.</p>
 *
 * @see TournamentTeamMemberJpaEntity
 */
public class TournamentTeamMemberId implements Serializable {
    private String teamId;
    private String userId;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * della chiave primaria composita tramite reflection.
     */
    public TournamentTeamMemberId() {
    }

    /**
     * Costruisce la chiave primaria composita a partire dai due identificativi.
     *
     * @param teamId identificativo della squadra; non deve essere {@code null}
     * @param userId identificativo dell'utente membro; non deve essere {@code null}
     */
    public TournamentTeamMemberId(String teamId, String userId) {
        this.teamId = teamId;
        this.userId = userId;
    }

    /**
     * Restituisce l'identificativo della squadra.
     *
     * @return l'identificativo della squadra; non deve essere {@code null}
     */
    public String getTeamId() { return teamId; }

    /**
     * Imposta l'identificativo della squadra.
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

    /**
     * Verifica se questa chiave primaria composita è uguale a un altro oggetto.
     *
     * <p>Due chiavi sono considerate uguali se appartengono alla stessa classe e
     * se entrambi gli attributi {@code teamId} e {@code userId} risultano
     * equivalenti.</p>
     *
     * @param o l'oggetto da confrontare; può essere {@code null}
     * @return {@code true} se le chiavi sono equivalenti, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentTeamMemberId that = (TournamentTeamMemberId) o;
        return Objects.equals(teamId, that.teamId) && Objects.equals(userId, that.userId);
    }

    /**
     * Restituisce il codice hash della chiave primaria composita.
     *
     * <p>Il valore è calcolato a partire dagli attributi {@code teamId} e
     * {@code userId} ed è coerente con il contratto di {@link #equals(Object)}.</p>
     *
     * @return il codice hash della chiave
     */
    @Override
    public int hashCode() {
        return Objects.hash(teamId, userId);
    }
}