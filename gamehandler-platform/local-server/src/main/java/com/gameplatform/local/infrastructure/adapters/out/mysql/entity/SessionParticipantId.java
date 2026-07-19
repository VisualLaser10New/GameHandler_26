package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary-key class for {@link SessionParticipantJpaEntity}, matching
 * the SQL {@code PRIMARY KEY (session_id, user_id)}.
 * Field names MUST match the entity's {@code @Id} field names for Hibernate
 * reflection-based population.
 *
 * @see SessionParticipantJpaEntity
 */
public class SessionParticipantId implements Serializable {
    private String sessionId;
    private String userId;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public SessionParticipantId() {
    }

    /**
     * Costruisce una chiave composita con i valori specificati.
     *
     * @param sessionId identificativo della sessione
     * @param userId    identificativo dell'utente partecipante
     */
    public SessionParticipantId(String sessionId, String userId) {
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

    /**
     * Confronta questa chiave con l'oggetto specificato per verificarne l'uguaglianza.
     *
     * @param o oggetto da confrontare
     * @return {@code true} se i due oggetti hanno gli stessi sessionId e userId
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionParticipantId that = (SessionParticipantId) o;
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(userId, that.userId);
    }

    /**
     * Restituisce il codice hash basato su sessionId e userId.
     *
     * @return codice hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(sessionId, userId);
    }
}
