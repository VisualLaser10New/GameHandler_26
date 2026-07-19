package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entità JPA per la tabella {@code local_servers} del database MySQL.
 *
 * <p>Rappresenta un server locale registrato presso il sistema centrale, associato
 * a un edificio e raggiungibile tramite un URL base. Tiene traccia dell'ultimo
 * istante in cui il server ha comunicato la propria presenza e del relativo stato
 * di attività. La chiave primaria è l'identificativo dell'edificio. Non sono
 * dichiarate relazioni JPA: l'edificio è referenziato tramite il proprio
 * identificativo testuale.</p>
 *
 * @see LocalAdminBuildingJpaEntity
 */
@Entity
@Table(name = "local_servers")
public class RegisteredLocalServerJpaEntity {

    @Id
    @Column(name = "building_id", length = 50)
    private String buildingId;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public RegisteredLocalServerJpaEntity() {
    }

    /**
     * Costruisce la registrazione di un server locale con i dati forniti.
     *
     * @param buildingId identificativo dell'edificio associato al server; non deve essere {@code null}
     * @param baseUrl URL base per raggiungere il server; non deve essere {@code null}
     * @param lastSeenAt istante dell'ultimo contatto con il server; non deve essere {@code null}
     * @param isActive indica se il server è attualmente attivo; non deve essere {@code null}
     */
    public RegisteredLocalServerJpaEntity(String buildingId, String baseUrl, Instant lastSeenAt, boolean isActive) {
        this.buildingId = buildingId;
        this.baseUrl = baseUrl;
        this.lastSeenAt = lastSeenAt;
        this.isActive = isActive;
    }

    /**
     * Restituisce l'identificativo dell'edificio associato al server.
     *
     * @return l'identificativo dell'edificio; non deve essere {@code null}
     */
    public String getBuildingId() {
        return buildingId;
    }

    /**
     * Imposta l'identificativo dell'edificio associato al server.
     *
     * @param buildingId nuovo identificativo dell'edificio; può essere {@code null}
     */
    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    /**
     * Restituisce l'URL base per raggiungere il server locale.
     *
     * @return l'URL base del server; non deve essere {@code null}
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Imposta l'URL base per raggiungere il server locale.
     *
     * @param baseUrl nuovo URL base del server; non deve essere {@code null}
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Restituisce l'istante dell'ultimo contatto con il server locale.
     *
     * @return l'istante dell'ultimo contatto; non deve essere {@code null}
     */
    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    /**
     * Imposta l'istante dell'ultimo contatto con il server locale.
     *
     * @param lastSeenAt nuovo istante dell'ultimo contatto; non deve essere {@code null}
     */
    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    /**
     * Indica se il server locale è attualmente attivo.
     *
     * @return {@code true} se il server è attivo, {@code false} altrimenti
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Imposta lo stato di attività del server locale.
     *
     * @param active nuovo stato di attività del server
     */
    public void setActive(boolean active) {
        isActive = active;
    }
}
