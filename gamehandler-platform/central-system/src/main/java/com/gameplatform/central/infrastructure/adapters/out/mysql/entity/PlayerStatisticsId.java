package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Classe che rappresenta la chiave primaria composita per
 * {@link PlayerStatisticsJpaEntity}, coerente con la definizione SQL del PIANO
 * {@code PRIMARY KEY (user_id, game_type)} (FASE 3, &sect;2.3).
 *
 * <p>I nomi dei campi devono corrispondere esattamente ai nomi dei campi
 * {@code @Id} dell'entità affinché Hibernate possa valorizzarli tramite
 * reflection. La classe implementa {@link Serializable} e ridefinisce
 * {@link #equals(Object)} e {@link #hashCode()} basandosi sui due attributi
 * che compongono la chiave.</p>
 *
 * @see PlayerStatisticsJpaEntity
 */
public class PlayerStatisticsId implements Serializable {
    private String userId;
    private String gameType;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * della chiave primaria composita tramite reflection.
     */
    public PlayerStatisticsId() {
    }

    /**
     * Costruisce la chiave primaria composita a partire dai due identificativi.
     *
     * @param userId identificativo dell'utente; non deve essere {@code null}
     * @param gameType tipo di gioco di riferimento; non deve essere {@code null}
     */
    public PlayerStatisticsId(String userId, String gameType) {
        this.userId = userId;
        this.gameType = gameType;
    }

    /**
     * Restituisce l'identificativo dell'utente.
     *
     * @return l'identificativo dell'utente; non deve essere {@code null}
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Imposta l'identificativo dell'utente.
     *
     * @param userId nuovo identificativo dell'utente; non deve essere {@code null}
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Restituisce il tipo di gioco di riferimento.
     *
     * @return il tipo di gioco; non deve essere {@code null}
     */
    public String getGameType() {
        return gameType;
    }

    /**
     * Imposta il tipo di gioco di riferimento.
     *
     * @param gameType nuovo tipo di gioco; non deve essere {@code null}
     */
    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    /**
     * Verifica se questa chiave primaria composita è uguale a un altro oggetto.
     *
     * <p>Due chiavi sono considerate uguali se appartengono alla stessa classe e
     * se entrambi gli attributi {@code userId} e {@code gameType} risultano
     * equivalenti.</p>
     *
     * @param o l'oggetto da confrontare; può essere {@code null}
     * @return {@code true} se le chiavi sono equivalenti, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerStatisticsId that = (PlayerStatisticsId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(gameType, that.gameType);
    }

    /**
     * Restituisce il codice hash della chiave primaria composita.
     *
     * <p>Il valore è calcolato a partire dagli attributi {@code userId} e
     * {@code gameType} ed è coerente con il contratto di {@link #equals(Object)}.</p>
     *
     * @return il codice hash della chiave
     */
    @Override
    public int hashCode() {
        return Objects.hash(userId, gameType);
    }
}