package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Associazione locale tra un amministratore e un edificio, che replica
 * i dati di assegnazione degli amministratori agli edifici. POJO immutabile,
 * identità composta da {@link UserId} e {@link BuildingId}.
 *
 * @see User
 * @see BuildingId
 */
public class LocalAdminBuilding {
    private final UserId userId;
    private final BuildingId buildingId;
    private final Instant assignedAt;

    /**
     * Costruisce una nuova associazione amministratore-edificio.
     *
     * @param userId     identificatore dell'amministratore (non null)
     * @param buildingId identificatore dell'edificio (non null)
     * @param assignedAt istante di assegnazione (non null)
     * @throws IllegalArgumentException se uno qualsiasi dei parametri è null
     */
    public LocalAdminBuilding(UserId userId, BuildingId buildingId, Instant assignedAt) {
        if (userId == null) throw new IllegalArgumentException("UserId cannot be null");
        if (buildingId == null) throw new IllegalArgumentException("BuildingId cannot be null");
        if (assignedAt == null) throw new IllegalArgumentException("assignedAt cannot be null");
        this.userId = userId;
        this.buildingId = buildingId;
        this.assignedAt = assignedAt;
    }

    /**
     * Restituisce l'identificatore dell'amministratore.
     *
     * @return userId
     */
    public UserId getUserId() { return userId; }

    /**
     * Restituisce l'identificatore dell'edificio.
     *
     * @return buildingId
     */
    public BuildingId getBuildingId() { return buildingId; }

    /**
     * Restituisce l'istante di assegnazione.
     *
     * @return assignedAt
     */
    public Instant getAssignedAt() { return assignedAt; }

    /**
     * Confronta questa associazione con un altro oggetto per uguaglianza
     * basata su userId e buildingId.
     *
     * @param o l'oggetto da confrontare
     * @return true se gli oggetti sono uguali
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalAdminBuilding that = (LocalAdminBuilding) o;
        return Objects.equals(userId, that.userId) && Objects.equals(buildingId, that.buildingId);
    }

    /**
     * Restituisce l'hash code basato su userId e buildingId.
     *
     * @return hash code
     */
    @Override
    public int hashCode() { return Objects.hash(userId, buildingId); }
}
