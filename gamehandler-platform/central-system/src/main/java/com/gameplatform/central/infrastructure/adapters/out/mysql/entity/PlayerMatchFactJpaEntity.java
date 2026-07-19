package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entità JPA per la tabella read-model {@code player_match_facts} (FASE 3, PIANO
 * &sect;2.3). Contiene una riga per ogni coppia (sessione, partecipante).
 *
 * <p>Utilizza una chiave primaria composita ({@code session_id}, {@code user_id})
 * tramite {@link IdClass} in modo che il mapping JPA rispecchi esattamente la
 * definizione SQL del PIANO {@code PRIMARY KEY (session_id, user_id)}. Non sono
 * dichiarate relazioni JPA: ogni chiave esterna è mantenuta come semplice
 * colonna di tipo {@code String}, secondo la convenzione esagonale adottata nel
 * progetto. Il campo {@code tournamentId} può essere {@code null} per le
 * sessioni che non appartengono a un torneo.</p>
 *
 * @see PlayerMatchFactId
 */
@Entity
@Table(name = "player_match_facts")
@IdClass(PlayerMatchFactId.class)
public class PlayerMatchFactJpaEntity {

    @Id
    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;

    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "building_id", length = 100, nullable = false)
    private String buildingId;

    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;

    @Column(name = "tournament_id", length = 36)
    private String tournamentId;

    @Column(name = "won", nullable = false)
    private Boolean won;

    @Column(name = "win_condition", length = 30)
    private String winCondition;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public PlayerMatchFactJpaEntity() {
    }

    /**
     * Costruisce un fatto di match per un partecipante a una sessione di gioco.
     *
     * @param sessionId identificativo della sessione; non deve essere {@code null}
     * @param userId identificativo dell'utente partecipante; non deve essere {@code null}
     * @param buildingId identificativo dell'edificio in cui si è svolto il match; non deve essere {@code null}
     * @param gameType tipo di gioco disputato; non deve essere {@code null}
     * @param tournamentId identificativo del torneo di appartenenza; può essere {@code null} se il match non è torneo
     * @param won indica se il partecipante ha vinto il match; non deve essere {@code null}
     * @param winCondition condizione di vittoria applicata; può essere {@code null}
     * @param endedAt istante di conclusione del match; non deve essere {@code null}
     */
    public PlayerMatchFactJpaEntity(String sessionId, String userId, String buildingId, String gameType,
                                    String tournamentId, Boolean won, String winCondition, Instant endedAt) {
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
     * Restituisce l'identificativo dell'edificio in cui si è svolto il match.
     *
     * @return l'identificativo dell'edificio; non deve essere {@code null}
     */
    public String getBuildingId() {
        return buildingId;
    }

    /**
     * Imposta l'identificativo dell'edificio in cui si è svolto il match.
     *
     * @param buildingId nuovo identificativo dell'edificio; non deve essere {@code null}
     */
    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    /**
     * Restituisce il tipo di gioco disputato nel match.
     *
     * @return il tipo di gioco; non deve essere {@code null}
     */
    public String getGameType() {
        return gameType;
    }

    /**
     * Imposta il tipo di gioco disputato nel match.
     *
     * @param gameType nuovo tipo di gioco; non deve essere {@code null}
     */
    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    /**
     * Restituisce l'identificativo del torneo di appartenenza del match.
     *
     * @return l'identificativo del torneo; può essere {@code null} se il match non appartiene a un torneo
     */
    public String getTournamentId() {
        return tournamentId;
    }

    /**
     * Imposta l'identificativo del torneo di appartenenza del match.
     *
     * @param tournamentId nuovo identificativo del torneo; può essere {@code null}
     */
    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    /**
     * Indica se il partecipante ha vinto il match.
     *
     * @return {@code true} se il partecipante ha vinto, {@code false} altrimenti;
     *         non deve essere {@code null}
     */
    public Boolean getWon() {
        return won;
    }

    /**
     * Imposta se il partecipante ha vinto il match.
     *
     * @param won nuovo valore che indica la vittoria; non deve essere {@code null}
     */
    public void setWon(Boolean won) {
        this.won = won;
    }

    /**
     * Restituisce la condizione di vittoria applicata al match.
     *
     * @return la condizione di vittoria; può essere {@code null}
     */
    public String getWinCondition() {
        return winCondition;
    }

    /**
     * Imposta la condizione di vittoria applicata al match.
     *
     * @param winCondition nuova condizione di vittoria; può essere {@code null}
     */
    public void setWinCondition(String winCondition) {
        this.winCondition = winCondition;
    }

    /**
     * Restituisce l'istante di conclusione del match.
     *
     * @return l'istante di fine match; non deve essere {@code null}
     */
    public Instant getEndedAt() {
        return endedAt;
    }

    /**
     * Imposta l'istante di conclusione del match.
     *
     * @param endedAt nuovo istante di fine match; non deve essere {@code null}
     */
    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }
}