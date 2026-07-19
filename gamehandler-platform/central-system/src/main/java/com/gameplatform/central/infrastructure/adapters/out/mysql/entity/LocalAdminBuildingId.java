package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Classe che rappresenta la chiave primaria composita per
 * {@link LocalAdminBuildingJpaEntity}, coerente con la definizione SQL del PIANO
 * {@code PRIMARY KEY (user_id, building_id)}.
 *
 * <p>I nomi dei campi devono corrispondere esattamente ai nomi dei campi
 * {@code @Id} dell'entità affinché Hibernate possa valorizzarli tramite
 * reflection. La classe implementa {@link Serializable} e ridefinisce
 * {@link #equals(Object)} e {@link #hashCode()} basandosi sui due attributi
 * che compongono la chiave.</p>
 *
 * @see LocalAdminBuildingJpaEntity
 */
public class LocalAdminBuildingId implements Serializable {
    private String userId;
    private String buildingId;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * della chiave primaria composita tramite reflection.
     */
    public LocalAdminBuildingId() {
    }

    /**
     * Costruisce la chiave primaria composita a partire dai due identificativi.
     *
     * @param userId identificativo dell'utente amministratore locale; non deve essere {@code null}
     * @param buildingId identificativo dell'edificio associato; non deve essere {@code null}
     */
    public LocalAdminBuildingId(String userId, String buildingId) {
        this.userId = userId;
        this.buildingId = buildingId;
    }

    /**
     * Restituisce l'identificativo dell'utente amministratore locale.
     *
     * @return l'identificativo dell'utente; non deve essere {@code null}
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Imposta l'identificativo dell'utente amministratore locale.
     *
     * @param userId nuovo identificativo dell'utente; non deve essere {@code null}
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Restituisce l'identificativo dell'edificio associato.
     *
     * @return l'identificativo dell'edificio; non deve essere {@code null}
     */
    public String getBuildingId() {
        return buildingId;
    }

    /**
     * Imposta l'identificativo dell'edificio associato.
     *
     * @param buildingId nuovo identificativo dell'edificio; non deve essere {@code null}
     */
    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    /**
     * Verifica se questa chiave primaria composita è uguale a un altro oggetto.
     *
     * <p>Due chiavi sono considerate uguali se appartengono alla stessa classe e
     * se entrambi gli attributi {@code userId} e {@code buildingId} risultano
     * equivalenti.</p>
     *
     * @param o l'oggetto da confrontare; può essere {@code null}
     * @return {@code true} se le chiavi sono equivalenti, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalAdminBuildingId that = (LocalAdminBuildingId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(buildingId, that.buildingId);
    }

    /**
     * Restituisce il codice hash della chiave primaria composita.
     *
     * <p>Il valore è calcolato a partire dagli attributi {@code userId} e
     * {@code buildingId} ed è coerente con il contratto di {@link #equals(Object)}.</p>
     *
     * @return il codice hash della chiave
     */
    @Override
    public int hashCode() {
        return Objects.hash(userId, buildingId);
    }
}