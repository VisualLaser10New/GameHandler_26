package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Classe che rappresenta la chiave primaria composita per
 * {@link TournamentBuildingJpaEntity}, coerente con la relazione tra torneo ed
 * edificio a cui è associato.
 *
 * <p>I nomi dei campi devono corrispondere esattamente ai nomi dei campi
 * {@code @Id} dell'entità affinché Hibernate possa valorizzarli tramite
 * reflection. La classe implementa {@link Serializable} e ridefinisce
 * {@link #equals(Object)} e {@link #hashCode()} basandosi sui due attributi
 * che compongono la chiave.</p>
 *
 * @see TournamentBuildingJpaEntity
 */
public class TournamentBuildingId implements Serializable {
    private String tournamentId;
    private String buildingId;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * della chiave primaria composita tramite reflection.
     */
    public TournamentBuildingId() {
    }

    /**
     * Costruisce la chiave primaria composita a partire dai due identificativi.
     *
     * @param tournamentId identificativo del torneo; non deve essere {@code null}
     * @param buildingId identificativo dell'edificio associato; non deve essere {@code null}
     */
    public TournamentBuildingId(String tournamentId, String buildingId) {
        this.tournamentId = tournamentId;
        this.buildingId = buildingId;
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
     * Restituisce l'identificativo dell'edificio associato.
     *
     * @return l'identificativo dell'edificio; non deve essere {@code null}
     */
    public String getBuildingId() { return buildingId; }

    /**
     * Imposta l'identificativo dell'edificio associato.
     *
     * @param buildingId nuovo identificativo dell'edificio; non deve essere {@code null}
     */
    public void setBuildingId(String buildingId) { this.buildingId = buildingId; }

    /**
     * Verifica se questa chiave primaria composita è uguale a un altro oggetto.
     *
     * <p>Due chiavi sono considerate uguali se appartengono alla stessa classe e
     * se entrambi gli attributi {@code tournamentId} e {@code buildingId}
     * risultano equivalenti.</p>
     *
     * @param o l'oggetto da confrontare; può essere {@code null}
     * @return {@code true} se le chiavi sono equivalenti, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentBuildingId that = (TournamentBuildingId) o;
        return Objects.equals(tournamentId, that.tournamentId) && Objects.equals(buildingId, that.buildingId);
    }

    /**
     * Restituisce il codice hash della chiave primaria composita.
     *
     * <p>Il valore è calcolato a partire dagli attributi {@code tournamentId} e
     * {@code buildingId} ed è coerente con il contratto di {@link #equals(Object)}.</p>
     *
     * @return il codice hash della chiave
     */
    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, buildingId);
    }
}