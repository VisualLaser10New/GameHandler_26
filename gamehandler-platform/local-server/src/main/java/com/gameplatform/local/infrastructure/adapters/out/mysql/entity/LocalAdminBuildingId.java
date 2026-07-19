package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary-key class for {@link LocalAdminBuildingJpaEntity}, matching
 * the SQL {@code PRIMARY KEY (user_id, building_id)}.
 *
 * <p>Field names MUST match the entity's {@code @Id} field names so Hibernate
 * can populate them via reflection.
 */
public class LocalAdminBuildingId implements Serializable {
    private String userId;
    private String buildingId;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public LocalAdminBuildingId() {
    }

    /**
     * Costruisce una chiave composita con i valori specificati.
     *
     * @param userId     identificativo dell'utente amministratore
     * @param buildingId identificativo dell'edificio
     */
    public LocalAdminBuildingId(String userId, String buildingId) {
        this.userId = userId;
        this.buildingId = buildingId;
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
     * Confronta questa chiave con l'oggetto specificato per verificarne l'uguaglianza.
     *
     * @param o oggetto da confrontare
     * @return {@code true} se i due oggetti hanno gli stessi userId e buildingId
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalAdminBuildingId that = (LocalAdminBuildingId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(buildingId, that.buildingId);
    }

    /**
     * Restituisce il codice hash basato su userId e buildingId.
     *
     * @return codice hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(userId, buildingId);
    }
}