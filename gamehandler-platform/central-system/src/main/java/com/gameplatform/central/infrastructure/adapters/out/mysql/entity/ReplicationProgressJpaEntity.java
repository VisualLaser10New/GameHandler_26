package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entità JPA per la tabella {@code replication_progress} del database MySQL.
 *
 * <p>Tiene traccia dello stato di avanzamento della replicazione degli eventi
 * verso i singoli server locali, consentendo di riprendere la sincronizzazione
 * dal punto interrotto. Ogni riga associa un evento a un server ed è protetta da
 * un vincolo di unicità sulla coppia {@code (event_id, server_id)}. Non sono
 * dichiarate relazioni JPA: evento e server sono referenziati tramite i propri
 * identificativi testuali.</p>
 *
 * @see OutboxEventJpaEntity
 * @see RegisteredLocalServerJpaEntity
 */
@Entity
@Table(name = "replication_progress", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_id", "server_id"})
})
public class ReplicationProgressJpaEntity {

    @Id
    @Column(name = "id", length = 100)
    private String id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "server_id", nullable = false, length = 50)
    private String serverId;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public ReplicationProgressJpaEntity() {
    }

    /**
     * Costruisce lo stato di avanzamento della replicazione di un evento verso un server.
     *
     * @param id identificativo univoco del record di avanzamento; non deve essere {@code null}
     * @param eventId identificativo dell'evento replicato; non deve essere {@code null}
     * @param serverId identificativo del server destinatario; non deve essere {@code null}
     */
    public ReplicationProgressJpaEntity(String id, String eventId, String serverId) {
        this.id = id;
        this.eventId = eventId;
        this.serverId = serverId;
    }

    /**
     * Restituisce l'identificativo univoco del record di avanzamento.
     *
     * @return l'identificativo del record; non deve essere {@code null}
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificativo univoco del record di avanzamento.
     *
     * @param id nuovo identificativo del record; può essere {@code null}
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Restituisce l'identificativo dell'evento replicato.
     *
     * @return l'identificativo dell'evento; non deve essere {@code null}
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Imposta l'identificativo dell'evento replicato.
     *
     * @param eventId nuovo identificativo dell'evento; non deve essere {@code null}
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Restituisce l'identificativo del server destinatario della replicazione.
     *
     * @return l'identificativo del server; non deve essere {@code null}
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Imposta l'identificativo del server destinatario della replicazione.
     *
     * @param serverId nuovo identificativo del server; non deve essere {@code null}
     */
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
}
