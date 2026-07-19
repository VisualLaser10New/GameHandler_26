package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code registered_local_servers_local} (PIANO §7.B).
 * Read-only replica updated only by sync; PK {@code building_id}; no
 * {@code @OneToMany}, no {@code @Version} (mirror of
 * {@code GameDefinitionLocalJpaEntity}).
 */
@Entity
@Table(name = "registered_local_servers_local")
public class RegisteredLocalServerLocalJpaEntity {

    @Id
    @Column(name = "building_id", length = 64, nullable = false)
    private String buildingId;

    @Column(name = "base_url", length = 255, nullable = false)
    private String baseUrl;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public RegisteredLocalServerLocalJpaEntity() {
    }

    /**
     * Costruisce una nuova istanza di server locale registrato.
     *
     * @param buildingId identificativo dell'edificio (PK)
     * @param baseUrl    URL di base del server locale
     * @param lastSeenAt istante dell'ultimo contatto (può essere {@code null})
     * @param active     indica se il server è attivo
     * @param updatedAt  istante dell'ultimo aggiornamento
     */
    public RegisteredLocalServerLocalJpaEntity(String buildingId, String baseUrl, Instant lastSeenAt,
                                               Boolean active, Instant updatedAt) {
        this.buildingId = buildingId;
        this.baseUrl = baseUrl;
        this.lastSeenAt = lastSeenAt;
        this.active = active;
        this.updatedAt = updatedAt;
    }

    /**
     * Restituisce l'identificativo dell'edificio.
     *
     * @return buildingId
     */
    public String getBuildingId() {
        return buildingId;
    }

    /**
     * Imposta l'identificativo dell'edificio.
     *
     * @param buildingId nuovo identificativo edificio
     */
    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    /**
     * Restituisce l'URL di base del server locale.
     *
     * @return baseUrl
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Imposta l'URL di base del server locale.
     *
     * @param baseUrl nuovo URL di base
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Restituisce l'istante dell'ultimo contatto con il server.
     *
     * @return lastSeenAt (può essere {@code null})
     */
    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    /**
     * Imposta l'istante dell'ultimo contatto.
     *
     * @param lastSeenAt nuovo istante ultimo contatto
     */
    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    /**
     * Indica se il server locale è attivo.
     *
     * @return {@code true} se attivo
     */
    public Boolean getActive() {
        return active;
    }

    /**
     * Imposta lo stato di attività del server.
     *
     * @param active {@code true} per attivare
     */
    public void setActive(Boolean active) {
        this.active = active;
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
