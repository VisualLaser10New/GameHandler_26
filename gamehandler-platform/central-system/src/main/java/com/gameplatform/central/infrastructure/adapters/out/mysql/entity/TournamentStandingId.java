package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Classe che rappresenta la chiave primaria composita per
 * {@link TournamentStandingJpaEntity}, coerente con la classifica di un
 * partecipante all'interno di un torneo.
 *
 * <p>I nomi dei campi devono corrispondere esattamente ai nomi dei campi
 * {@code @Id} dell'entità affinché Hibernate possa valorizzarli tramite
 * reflection. La classe implementa {@link Serializable} e ridefinisce
 * {@link #equals(Object)} e {@link #hashCode()} basandosi sui due attributi
 * che compongono la chiave.</p>
 *
 * @see TournamentStandingJpaEntity
 */
public class TournamentStandingId implements Serializable {
    private String tournamentId;
    private String participantId;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * della chiave primaria composita tramite reflection.
     */
    public TournamentStandingId() {
    }

    /**
     * Costruisce la chiave primaria composita a partire dai due identificativi.
     *
     * @param tournamentId identificativo del torneo; non deve essere {@code null}
     * @param participantId identificativo del partecipante; non deve essere {@code null}
     */
    public TournamentStandingId(String tournamentId, String participantId) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
    }

    /**
     * Restituisce l'identificativo del torneo.
     *
     * @return l'identificativo del torneo; non deve essere {@code null}
     */
    public String getTournamentId() { return tournamentId; }

    /**
     * Imposta l'identificativo del torneo.
     *
     * @param tournamentId nuovo identificativo del torneo; non deve essere {@code null}
     */
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

    /**
     * Restituisce l'identificativo del partecipante.
     *
     * @return l'identificativo del partecipante; non deve essere {@code null}
     */
    public String getParticipantId() { return participantId; }

    /**
     * Imposta l'identificativo del partecipante.
     *
     * @param participantId nuovo identificativo del partecipante; non deve essere {@code null}
     */
    public void setParticipantId(String participantId) { this.participantId = participantId; }

    /**
     * Verifica se questa chiave primaria composita è uguale a un altro oggetto.
     *
     * <p>Due chiavi sono considerate uguali se appartengono alla stessa classe e
     * se entrambi gli attributi {@code tournamentId} e {@code participantId}
     * risultano equivalenti.</p>
     *
     * @param o l'oggetto da confrontare; può essere {@code null}
     * @return {@code true} se le chiavi sono equivalenti, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentStandingId that = (TournamentStandingId) o;
        return Objects.equals(tournamentId, that.tournamentId) && Objects.equals(participantId, that.participantId);
    }

    /**
     * Restituisce il codice hash della chiave primaria composita.
     *
     * <p>Il valore è calcolato a partire dagli attributi {@code tournamentId} e
     * {@code participantId} ed è coerente con il contratto di {@link #equals(Object)}.</p>
     *
     * @return il codice hash della chiave
     */
    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, participantId);
    }
}