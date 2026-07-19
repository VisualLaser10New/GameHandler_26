package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Classe che rappresenta la chiave primaria composita per
 * {@link PlayerMatchFactJpaEntity}, coerente con la definizione SQL del PIANO
 * {@code PRIMARY KEY (session_id, user_id)} (FASE 3, &sect;2.3).
 *
 * <p>I nomi dei campi devono corrispondere esattamente ai nomi dei campi
 * {@code @Id} dell'entità affinché Hibernate possa valorizzarli tramite
 * reflection. La classe implementa {@link Serializable} e ridefinisce
 * {@link #equals(Object)} e {@link #hashCode()} basandosi sui due attributi
 * che compongono la chiave.</p>
 *
 * @see PlayerMatchFactJpaEntity
 */
public class PlayerMatchFactId implements Serializable {
    private String sessionId;
    private String userId;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * della chiave primaria composita tramite reflection.
     */
    public PlayerMatchFactId() {
    }

    /**
     * Costruisce la chiave primaria composita a partire dai due identificativi.
     *
     * @param sessionId identificativo della sessione di gioco; non deve essere {@code null}
     * @param userId identificativo dell'utente partecipante; non deve essere {@code null}
     */
    public PlayerMatchFactId(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
    }

    /**
     * Restituisce l'identificativo della sessione di gioco.
     *
     * @return l'identificativo della sessione; non deve essere {@code null}
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Imposta l'identificativo della sessione di gioco.
     *
     * @param sessionId nuovo identificativo della sessione; non deve essere {@code null}
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Restituisce l'identificativo dell'utente partecipante.
     *
     * @return l'identificativo dell'utente; non deve essere {@code null}
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Imposta l'identificativo dell'utente partecipante.
     *
     * @param userId nuovo identificativo dell'utente; non deve essere {@code null}
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Verifica se questa chiave primaria composita è uguale a un altro oggetto.
     *
     * <p>Due chiavi sono considerate uguali se appartengono alla stessa classe e
     * se entrambi gli attributi {@code sessionId} e {@code userId} risultano
     * equivalenti.</p>
     *
     * @param o l'oggetto da confrontare; può essere {@code null}
     * @return {@code true} se le chiavi sono equivalenti, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerMatchFactId that = (PlayerMatchFactId) o;
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(userId, that.userId);
    }

    /**
     * Restituisce il codice hash della chiave primaria composita.
     *
     * <p>Il valore è calcolato a partire dagli attributi {@code sessionId} e
     * {@code userId} ed è coerente con il contratto di {@link #equals(Object)}.</p>
     *
     * @return il codice hash della chiave
     */
    @Override
    public int hashCode() {
        return Objects.hash(sessionId, userId);
    }
}