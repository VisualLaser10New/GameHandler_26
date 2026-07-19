package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entità JPA per la tabella read-model {@code player_statistics} (FASE 3, PIANO
 * &sect;2.3). Contiene contatori aggregati per ciascun giocatore e tipo di gioco.
 *
 * <p>Utilizza una chiave primaria composita ({@code user_id}, {@code game_type})
 * tramite {@link IdClass}, coerente con la definizione SQL del PIANO
 * {@code PRIMARY KEY (user_id, game_type)}. Non sono dichiarate relazioni JPA:
 * ogni chiave esterna è mantenuta come semplice colonna di tipo {@code String},
 * secondo la convenzione esagonale adottata nel progetto. Il campo
 * {@code lastPlayedAt} può essere {@code null} se il giocatore non ha ancora
 * disputato alcun match per il tipo di gioco considerato.</p>
 *
 * @see PlayerStatisticsId
 */
@Entity
@Table(name = "player_statistics")
@IdClass(PlayerStatisticsId.class)
public class PlayerStatisticsJpaEntity {

    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Id
    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;

    @Column(name = "matches_played", nullable = false)
    private Integer matchesPlayed;

    @Column(name = "matches_won", nullable = false)
    private Integer matchesWon;

    @Column(name = "last_played_at")
    private Instant lastPlayedAt;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public PlayerStatisticsJpaEntity() {
    }

    /**
     * Costruisce lo schema di statistiche aggregate di un giocatore per un tipo di gioco.
     *
     * @param userId identificativo dell'utente; non deve essere {@code null}
     * @param gameType tipo di gioco di riferimento; non deve essere {@code null}
     * @param matchesPlayed numero di match disputati; non deve essere {@code null} e non negativo
     * @param matchesWon numero di match vinti; non deve essere {@code null} e non negativo
     * @param lastPlayedAt istante dell'ultimo match disputato; può essere {@code null}
     */
    public PlayerStatisticsJpaEntity(String userId, String gameType, Integer matchesPlayed,
                                     Integer matchesWon, Instant lastPlayedAt) {
        this.userId = userId;
        this.gameType = gameType;
        this.matchesPlayed = matchesPlayed;
        this.matchesWon = matchesWon;
        this.lastPlayedAt = lastPlayedAt;
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
     * Restituisce il numero di match disputati dal giocatore per il tipo di gioco.
     *
     * @return il numero di match disputati; non deve essere {@code null} e non negativo
     */
    public Integer getMatchesPlayed() {
        return matchesPlayed;
    }

    /**
     * Imposta il numero di match disputati dal giocatore per il tipo di gioco.
     *
     * @param matchesPlayed nuovo numero di match disputati; non deve essere {@code null} e non negativo
     */
    public void setMatchesPlayed(Integer matchesPlayed) {
        this.matchesPlayed = matchesPlayed;
    }

    /**
     * Restituisce il numero di match vinti dal giocatore per il tipo di gioco.
     *
     * @return il numero di match vinti; non deve essere {@code null} e non negativo
     */
    public Integer getMatchesWon() {
        return matchesWon;
    }

    /**
     * Imposta il numero di match vinti dal giocatore per il tipo di gioco.
     *
     * @param matchesWon nuovo numero di match vinti; non deve essere {@code null} e non negativo
     */
    public void setMatchesWon(Integer matchesWon) {
        this.matchesWon = matchesWon;
    }

    /**
     * Restituisce l'istante dell'ultimo match disputato dal giocatore.
     *
     * @return l'istante dell'ultimo match; può essere {@code null} se nessun match è stato disputato
     */
    public Instant getLastPlayedAt() {
        return lastPlayedAt;
    }

    /**
     * Imposta l'istante dell'ultimo match disputato dal giocatore.
     *
     * @param lastPlayedAt nuovo istante dell'ultimo match; può essere {@code null}
     */
    public void setLastPlayedAt(Instant lastPlayedAt) {
        this.lastPlayedAt = lastPlayedAt;
    }
}