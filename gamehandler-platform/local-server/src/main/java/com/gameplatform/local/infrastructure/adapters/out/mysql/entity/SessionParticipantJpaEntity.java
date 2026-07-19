package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * JPA entity per la tabella {@code session_participants}.
 * Associa un utente a una sessione di gioco tramite chiave composita
 * {@link SessionParticipantId} su (sessionId, userId).
 *
 * @see SessionParticipantId
 * @see GameSessionJpaEntity
 */
@Entity
@Table(name = "session_participants")
@IdClass(SessionParticipantId.class)
public class SessionParticipantJpaEntity {

    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public SessionParticipantJpaEntity() {
    }

    /**
     * Costruisce una nuova associazione partecipante-sessione.
     *
     * @param sessionId identificativo della sessione
     * @param userId    identificativo dell'utente partecipante
     */
    public SessionParticipantJpaEntity(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
    }

    /**
     * Restituisce l'identificativo della sessione.
     *
     * @return sessionId
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Imposta l'identificativo della sessione.
     *
     * @param sessionId nuovo identificativo sessione
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Restituisce l'identificativo dell'utente partecipante.
     *
     * @return userId
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Imposta l'identificativo dell'utente partecipante.
     *
     * @param userId nuovo identificativo utente
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }
}
