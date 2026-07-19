package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entità JPA per la tabella di associazione {@code local_admin_buildings}.
 *
 * <p>Utilizza una chiave primaria composita ({@code user_id}, {@code building_id})
 * tramite {@link IdClass} in modo che il mapping JPA rispecchi esattamente la
 * definizione SQL del PIANO {@code PRIMARY KEY (user_id, building_id)}. Non sono
 * dichiarate relazioni JPA: le chiavi esterne sono mantenute come semplici
 * colonne di tipo {@code String}, secondo la convenzione esagonale adottata nel
 * progetto. Non espone metodi che lanciano eccezioni checked; le violazioni di
 * vincolo (es. coppia duplicata) sono rilevate al momento della persistenza.</p>
 *
 * @see LocalAdminBuildingId
 * @see UserJpaEntity
 */
@Entity
@Table(name = "local_admin_buildings")
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
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public LocalAdminBuildingJpaEntity() {
    }

    /**
     * Costruisce un'associazione tra un amministratore locale e un edificio.
     *
     * @param userId identificativo dell'utente amministratore locale; non deve essere {@code null}
     * @param buildingId identificativo dell'edificio associato; non deve essere {@code null}
     * @param assignedAt istante in cui è stata creata l'associazione; non deve essere {@code null}
     */
    public LocalAdminBuildingJpaEntity(String userId, String buildingId, Instant assignedAt) {
        this.userId = userId;
        this.buildingId = buildingId;
        this.assignedAt = assignedAt;
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
     * Restituisce l'istante in cui è stata creata l'associazione.
     *
     * @return l'istante di assegnazione; non deve essere {@code null}
     */
    public Instant getAssignedAt() {
        return assignedAt;
    }

    /**
     * Imposta l'istante in cui è stata creata l'associazione.
     *
     * @param assignedAt nuovo istante di assegnazione; non deve essere {@code null}
     */
    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }
}