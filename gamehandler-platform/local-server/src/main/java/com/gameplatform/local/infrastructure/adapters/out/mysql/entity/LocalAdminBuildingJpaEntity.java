package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity per la tabella {@code local_admin_buildings_local}.
 * Associa un utente amministratore a uno o più edifici di cui è
 * responsabile. Utilizza una chiave composita {@link LocalAdminBuildingId}
 * su (userId, buildingId).
 *
 * @see LocalAdminBuildingId
 * @see LocalUserJpaEntity
 */
@Entity
@Table(name = "local_admin_buildings_local")
@IdClass(LocalAdminBuildingId.class)
public class LocalAdminBuildingJpaEntity {
    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;
    @Id
    @Column(name = "building_id", length = 100, nullable = false)
    private String buildingId;
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public LocalAdminBuildingJpaEntity() {
    }

    /**
     * Costruisce una nuova associazione amministratore-edificio.
     *
     * @param userId     identificativo dell'utente amministratore
     * @param buildingId identificativo dell'edificio
     * @param assignedAt istante di assegnazione
     */
    public LocalAdminBuildingJpaEntity(String userId, String buildingId, Instant assignedAt) {
        this.userId = userId;
        this.buildingId = buildingId;
        this.assignedAt = assignedAt;
    }

    /**
     * Restituisce l'identificativo dell'utente amministratore.
     *
     * @return userId
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Imposta l'identificativo dell'utente amministratore.
     *
     * @param userId nuovo identificativo utente
     */
    public void setUserId(String userId) {
        this.userId = userId;
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
     * Restituisce l'istante di assegnazione dell'amministratore all'edificio.
     *
     * @return assignedAt
     */
    public Instant getAssignedAt() {
        return assignedAt;
    }

    /**
     * Imposta l'istante di assegnazione.
     *
     * @param assignedAt nuovo istante di assegnazione
     */
    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }
}
