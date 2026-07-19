package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Entità di dominio che rappresenta l'associazione tra un utente amministratore
 * locale e un edificio da esso gestito. Registra quale amministratore è
 * responsabile di quale edificio; l'identità è determinata dalla coppia
 * (identificativo utente, identificativo edificio), poiché un utente può gestire
 * più edifici e un edificio può avere più amministratori.
 *
 * @see UserId
 * @see BuildingId
 */
public class LocalAdminBuilding {
    private final UserId userId;
    private final BuildingId buildingId;
    private final Instant assignedAt;

    /**
     * Costruisce un'associazione tra un amministratore locale e un edificio.
     *
     * @param userId identificativo dell'utente amministratore; non può essere {@code null}
     * @param buildingId identificativo dell'edificio gestito; non può essere {@code null}
     * @param assignedAt istante in cui è stata effettuata l'associazione; non può essere {@code null}
     * @throws IllegalArgumentException se uno dei parametri è {@code null}
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
     * Restituisce l'identificativo dell'utente amministratore.
     *
     * @return l'identificativo dell'utente, mai {@code null}
     */
    public UserId getUserId() {
        return userId;
    }

    /**
     * Restituisce l'identificativo dell'edificio gestito.
     *
     * @return l'identificativo dell'edificio, mai {@code null}
     */
    public BuildingId getBuildingId() {
        return buildingId;
    }

    /**
     * Restituisce l'istante in cui è stata effettuata l'associazione.
     *
     * @return l'istante di assegnazione, mai {@code null}
     */
    public Instant getAssignedAt() {
        return assignedAt;
    }

    /**
     * Confronta questa associazione con un altro oggetto verificandone
     * l'uguaglianza sulla base della coppia utente ed edificio.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è un {@code LocalAdminBuilding} con lo stesso utente e lo stesso edificio, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalAdminBuilding that = (LocalAdminBuilding) o;
        return Objects.equals(userId, that.userId) && Objects.equals(buildingId, that.buildingId);
    }

    /**
     * Restituisce il codice hash calcolato sulla coppia utente ed edificio.
     *
     * @return il codice hash dell'associazione
     */
    @Override
    public int hashCode() {
        return Objects.hash(userId, buildingId);
    }
}