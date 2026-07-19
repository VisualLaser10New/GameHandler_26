package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.time.Instant;
import java.util.Objects;

/**
 * Modello di lettura di dominio che rappresenta una singola partita giocata da
 * un utente all'interno di una sessione di gioco. Per ogni sessione viene
 * generato un fatto distinto per ciascun partecipante; l'identità è determinata
 * dalla coppia (identificativo sessione, identificativo utente). Il riferimento
 * al torneo è opzionale e valorizzato solo quando la sessione è collegata a una
 * partita di torneo.
 *
 * @see UserId
 * @see BuildingId
 * @see GameType
 * @see WinCondition
 */
public class PlayerMatchFact {
    private final String sessionId;
    private final UserId userId;
    private final BuildingId buildingId;
    private final GameType gameType;
    private final String tournamentId;
    private final boolean won;
    private final WinCondition winCondition;
    private final Instant endedAt;

    /**
     * Costruisce un fatto di partita per un utente con i valori specificati.
     *
     * @param sessionId identificativo della sessione di gioco; non può essere {@code null} né vuoto
     * @param userId identificativo dell'utente partecipante; non può essere {@code null}
     * @param buildingId identificativo dell'edificio in cui si è svolta la partita; non può essere {@code null}
     * @param gameType tipo di gioco della partita; non può essere {@code null}
     * @param tournamentId identificativo del torneo associato; può essere {@code null} se la partita non appartiene a un torneo
     * @param won indica se l'utente ha vinto la partita
     * @param winCondition condizione di vittoria della partita; può essere {@code null}
     * @param endedAt istante di conclusione della partita; non può essere {@code null}
     * @throws IllegalArgumentException se uno dei vincoli sui parametri obbligatori non è rispettato
     */
    public PlayerMatchFact(String sessionId,
                           UserId userId,
                           BuildingId buildingId,
                           GameType gameType,
                           String tournamentId,
                           boolean won,
                           WinCondition winCondition,
                           Instant endedAt) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be null or blank");
        }
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (endedAt == null) {
            throw new IllegalArgumentException("endedAt cannot be null");
        }
        this.sessionId = sessionId;
        this.userId = userId;
        this.buildingId = buildingId;
        this.gameType = gameType;
        this.tournamentId = tournamentId;
        this.won = won;
        this.winCondition = winCondition;
        this.endedAt = endedAt;
    }

    /**
     * Restituisce l'identificativo della sessione di gioco.
     *
     * @return l'identificativo della sessione, mai {@code null}
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Restituisce l'identificativo dell'utente partecipante.
     *
     * @return l'identificativo dell'utente, mai {@code null}
     */
    public UserId getUserId() {
        return userId;
    }

    /**
     * Restituisce l'identificativo dell'edificio in cui si è svolta la partita.
     *
     * @return l'identificativo dell'edificio, mai {@code null}
     */
    public BuildingId getBuildingId() {
        return buildingId;
    }

    /**
     * Restituisce il tipo di gioco della partita.
     *
     * @return il tipo di gioco, mai {@code null}
     */
    public GameType getGameType() {
        return gameType;
    }

    /**
     * Restituisce l'identificativo del torneo associato alla partita.
     *
     * @return l'identificativo del torneo, oppure {@code null} se la partita non appartiene a un torneo
     */
    public String getTournamentId() {
        return tournamentId;
    }

    /**
     * Indica se l'utente ha vinto la partita.
     *
     * @return {@code true} se l'utente ha vinto, {@code false} altrimenti
     */
    public boolean isWon() {
        return won;
    }

    /**
     * Restituisce la condizione di vittoria della partita.
     *
     * @return la condizione di vittoria, oppure {@code null} se non specificata
     */
    public WinCondition getWinCondition() {
        return winCondition;
    }

    /**
     * Restituisce l'istante di conclusione della partita.
     *
     * @return l'istante di conclusione, mai {@code null}
     */
    public Instant getEndedAt() {
        return endedAt;
    }

    /**
     * Confronta questo fatto di partita con un altro oggetto verificandone
     * l'uguaglianza sulla base della coppia sessione e utente.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è un {@code PlayerMatchFact} con la stessa sessione e lo stesso utente, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerMatchFact that = (PlayerMatchFact) o;
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(userId, that.userId);
    }

    /**
     * Restituisce il codice hash calcolato sulla coppia sessione e utente.
     *
     * @return il codice hash del fatto di partita
     */
    @Override
    public int hashCode() {
        return Objects.hash(sessionId, userId);
    }
}