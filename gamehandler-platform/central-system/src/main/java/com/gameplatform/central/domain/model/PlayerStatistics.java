package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Modello di lettura di dominio che aggrega lo storico delle partite di un
 * singolo giocatore per ciascun tipo di gioco, mantenendo il numero di partite
 * giocate, di quelle vinte e l'istante dell'ultima partita. L'identità è
 * determinata dalla coppia (identificativo utente, tipo di gioco). L'entità è
 * immutabile: gli incrementi producono una nuova istanza.
 *
 * @see UserId
 * @see GameType
 * @see #mergeIncrement(boolean, Instant)
 */
public class PlayerStatistics {
    private final UserId userId;
    private final GameType gameType;
    private final int matchesPlayed;
    private final int matchesWon;
    private final Instant lastPlayedAt;

    /**
     * Costruisce un'aggregazione di statistiche per un giocatore con i valori
     * specificati.
     *
     * @param userId identificativo dell'utente; non può essere {@code null}
     * @param gameType tipo di gioco a cui si riferiscono le statistiche; non può essere {@code null}
     * @param matchesPlayed numero di partite giocate; non può essere negativo
     * @param matchesWon numero di partite vinte; non può essere negativo né superiore a {@code matchesPlayed}
     * @param lastPlayedAt istante dell'ultima partita giocata; può essere {@code null} se nessuna partita è ancora stata registrata
     * @throws IllegalArgumentException se uno dei vincoli sui parametri non è rispettato
     */
    public PlayerStatistics(UserId userId, GameType gameType, int matchesPlayed, int matchesWon, Instant lastPlayedAt) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (matchesPlayed < 0) {
            throw new IllegalArgumentException("matchesPlayed cannot be negative");
        }
        if (matchesWon < 0) {
            throw new IllegalArgumentException("matchesWon cannot be negative");
        }
        if (matchesWon > matchesPlayed) {
            throw new IllegalArgumentException("matchesWon cannot exceed matchesPlayed");
        }
        this.userId = userId;
        this.gameType = gameType;
        this.matchesPlayed = matchesPlayed;
        this.matchesWon = matchesWon;
        this.lastPlayedAt = lastPlayedAt;
    }

    /**
     * Restituisce una nuova istanza di statistiche che rappresenta quella
     * corrente incrementata di una partita aggiuntiva, aggiornando il numero di
     * partite giocate, eventualmente quelle vinte e l'istante dell'ultima
     * partita al più recente tra il valore esistente e {@code endedAt}.
     * L'istanza corrente non viene modificata.
     *
     * @param won indica se il giocatore ha vinto la partita aggiuntiva
     * @param endedAt istante di conclusione della partita aggiuntiva; non può essere {@code null}
     * @return una nuova istanza di {@code PlayerStatistics} con i contatori aggiornati
     * @throws IllegalArgumentException se {@code endedAt} è {@code null}
     */
    public PlayerStatistics mergeIncrement(boolean won, Instant endedAt) {
        if (endedAt == null) {
            throw new IllegalArgumentException("endedAt cannot be null");
        }
        Instant newLastPlayedAt = (lastPlayedAt == null || endedAt.isAfter(lastPlayedAt)) ? endedAt : lastPlayedAt;
        return new PlayerStatistics(
                userId,
                gameType,
                matchesPlayed + 1,
                matchesWon + (won ? 1 : 0),
                newLastPlayedAt);
    }

    /**
     * Restituisce l'identificativo dell'utente.
     *
     * @return l'identificativo dell'utente, mai {@code null}
     */
    public UserId getUserId() {
        return userId;
    }

    /**
     * Restituisce il tipo di gioco a cui si riferiscono le statistiche.
     *
     * @return il tipo di gioco, mai {@code null}
     */
    public GameType getGameType() {
        return gameType;
    }

    /**
     * Restituisce il numero di partite giocate.
     *
     * @return il numero di partite giocate, sempre maggiore o uguale a zero
     */
    public int getMatchesPlayed() {
        return matchesPlayed;
    }

    /**
     * Restituisce il numero di partite vinte.
     *
     * @return il numero di partite vinte, sempre maggiore o uguale a zero e non superiore alle partite giocate
     */
    public int getMatchesWon() {
        return matchesWon;
    }

    /**
     * Restituisce l'istante dell'ultima partita giocata.
     *
     * @return l'istante dell'ultima partita, oppure {@code null} se nessuna partita è stata registrata
     */
    public Instant getLastPlayedAt() {
        return lastPlayedAt;
    }

    /**
     * Confronta queste statistiche con un altro oggetto verificandone
     * l'uguaglianza sulla base della coppia utente e tipo di gioco.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è un {@code PlayerStatistics} con lo stesso utente e lo stesso tipo di gioco, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerStatistics that = (PlayerStatistics) o;
        return Objects.equals(userId, that.userId) && Objects.equals(gameType, that.gameType);
    }

    /**
     * Restituisce il codice hash calcolato sulla coppia utente e tipo di gioco.
     *
     * @return il codice hash delle statistiche
     */
    @Override
    public int hashCode() {
        return Objects.hash(userId, gameType);
    }
}