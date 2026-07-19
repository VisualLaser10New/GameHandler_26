package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;

import java.time.Instant;
import java.util.Objects;

/**
 * Entità di dominio che rappresenta un server locale registrato presso il
 * sistema centrale, identificato dall'edificio che gestisce. Mantiene l'URL di
 * base per la comunicazione, l'istante dell'ultimo contatto e lo stato di
 * attività del server.
 *
 * @see BuildingId
 */
public class RegisteredLocalServer {
    private BuildingId buildingId;
    private String baseUrl;
    private Instant lastSeenAt;
    private boolean isActive;

    /**
     * Costruisce un server locale registrato con i valori specificati.
     *
     * @param buildingId identificativo dell'edificio gestito dal server; non può essere {@code null}
     * @param baseUrl URL di base per la comunicazione con il server; non può essere {@code null} né vuoto
     * @param lastSeenAt istante dell'ultimo contatto con il server; può essere {@code null}
     * @param isActive indica se il server è attivo
     * @throws IllegalArgumentException se {@code buildingId} è {@code null} oppure se {@code baseUrl} è {@code null} o vuoto
     */
    public RegisteredLocalServer(BuildingId buildingId, String baseUrl, Instant lastSeenAt, boolean isActive) {
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL cannot be null, empty or blank");
        }
        this.buildingId = buildingId;
        this.baseUrl = baseUrl;
        this.lastSeenAt = lastSeenAt;
        this.isActive = isActive;
    }

    /**
     * Aggiorna l'istante dell'ultimo contatto con il server locale.
     *
     * @param lastSeenAt nuovo istante dell'ultimo contatto; non può essere {@code null}
     * @throws IllegalArgumentException se {@code lastSeenAt} è {@code null}
     */
    public void updateLastSeen(Instant lastSeenAt) {
        if (lastSeenAt == null) {
            throw new IllegalArgumentException("lastSeenAt cannot be null");
        }
        this.lastSeenAt = lastSeenAt;
    }

    /**
     * Imposta lo stato di attività del server locale.
     *
     * @param active {@code true} per contrassegnare il server come attivo, {@code false} altrimenti
     */
    public void setActive(boolean active) {
        this.isActive = active;
    }

    /**
     * Restituisce l'identificativo dell'edificio gestito dal server.
     *
     * @return l'identificativo dell'edificio, mai {@code null}
     */
    public BuildingId getBuildingId() {
        return buildingId;
    }
    /**
     * Restituisce l'URL di base per la comunicazione con il server.
     *
     * @return l'URL di base, mai {@code null} né vuoto
     */
    public String getBaseUrl() {
        return baseUrl;
    }
    /**
     * Restituisce l'istante dell'ultimo contatto con il server.
     *
     * @return l'istante dell'ultimo contatto, oppure {@code null} se non ancora registrato
     * @see #updateLastSeen(Instant)
     */
    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
    /**
     * Indica se il server locale è attualmente attivo.
     *
     * @return {@code true} se il server è attivo, {@code false} altrimenti
     * @see #setActive(boolean)
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Confronta questo server registrato con un altro oggetto verificandone
     * l'uguaglianza sulla base dell'identificativo dell'edificio.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è un {@code RegisteredLocalServer} con lo stesso edificio, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisteredLocalServer that = (RegisteredLocalServer) o;
        return Objects.equals(buildingId, that.buildingId);
    }

    /**
     * Restituisce il codice hash calcolato sull'identificativo dell'edificio.
     *
     * @return il codice hash del server registrato
     */
    @Override
    public int hashCode() {
        return Objects.hash(buildingId);
    }
}

