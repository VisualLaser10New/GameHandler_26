package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import com.gameplatform.central.infrastructure.adapters.out.mysql.converter.JsonStringUnwrappingConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entità JPA per la tabella {@code outbox_events} del pattern transazionale outbox.
 *
 * <p>Memorizza gli eventi di dominio da propagare in modo affidabile verso i
 * sistemi esterni, garantendo che la scrittura dell'evento avvenga nella stessa
 * transazione dei dati di business. La tabella è indicizzata sulla coppia
 * {@code (status, created_at)} per ottimizzare il polling degli eventi da
 * inviare. Il payload è memorizzato come JSON e convertito tramite
 * {@link JsonStringUnwrappingConverter}. Non sono dichiarate relazioni JPA.</p>
 *
 * @see JsonStringUnwrappingConverter
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status_created_at", columnList = "status, created_at")
})
public class OutboxEventJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    @Convert(converter = JsonStringUnwrappingConverter.class)
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public OutboxEventJpaEntity() {
    }

    /**
     * Costruisce un evento outbox con i dati forniti, compreso l'istante di invio.
     *
     * @param id identificativo univoco dell'evento; non deve essere {@code null}
     * @param eventType tipo dell'evento di dominio; non deve essere {@code null}
     * @param payload contenuto dell'evento in formato JSON; non deve essere {@code null}
     * @param status stato dell'evento (es. pendente, inviato); non deve essere {@code null}
     * @param createdAt istante di creazione dell'evento; non deve essere {@code null}
     * @param sentAt istante di invio dell'evento; può essere {@code null} se non ancora inviato
     */
    public OutboxEventJpaEntity(String id, String eventType, String payload, String status, Instant createdAt, Instant sentAt) {
        this.id = id;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
    }

    /**
     * Restituisce l'identificativo univoco dell'evento.
     *
     * @return l'identificativo dell'evento; non deve essere {@code null}
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificativo univoco dell'evento.
     *
     * @param id nuovo identificativo dell'evento; può essere {@code null}
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Restituisce il tipo dell'evento di dominio.
     *
     * @return il tipo dell'evento; non deve essere {@code null}
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Imposta il tipo dell'evento di dominio.
     *
     * @param eventType nuovo tipo dell'evento; non deve essere {@code null}
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * Restituisce il contenuto dell'evento in formato JSON.
     *
     * @return il payload dell'evento; non deve essere {@code null}
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Imposta il contenuto dell'evento in formato JSON.
     *
     * @param payload nuovo payload dell'evento; non deve essere {@code null}
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * Restituisce lo stato corrente dell'evento.
     *
     * @return lo stato dell'evento; non deve essere {@code null}
     */
    public String getStatus() {
        return status;
    }

    /**
     * Imposta lo stato corrente dell'evento.
     *
     * @param status nuovo stato dell'evento; non deve essere {@code null}
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Restituisce l'istante di creazione dell'evento.
     *
     * @return l'istante di creazione; non deve essere {@code null}
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Imposta l'istante di creazione dell'evento.
     *
     * @param createdAt nuovo istante di creazione; non deve essere {@code null}
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Restituisce l'istante di invio dell'evento.
     *
     * @return l'istante di invio; può essere {@code null} se l'evento non è ancora stato inviato
     */
    public Instant getSentAt() {
        return sentAt;
    }

    /**
     * Imposta l'istante di invio dell'evento.
     *
     * @param sentAt nuovo istante di invio; può essere {@code null}
     */
    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}
