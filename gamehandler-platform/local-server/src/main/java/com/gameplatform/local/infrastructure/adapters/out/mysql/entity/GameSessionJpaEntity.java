package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import com.gameplatform.local.infrastructure.adapters.out.mysql.converter.JsonStringUnwrappingConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity per la tabella {@code game_sessions}.
 * Rappresenta una sessione di gioco effettiva, con stato, durata, vincitore
 * e partecipanti. Contiene una relazione {@code @OneToMany} verso
 * {@link SessionParticipantJpaEntity} e utilizza optimistic locking
 * tramite {@code @Version}.
 *
 * @see SessionParticipantJpaEntity
 * @see GameJpaEntity
 */
@Entity
@Table(name = "game_sessions")
public class GameSessionJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "game_id", nullable = false, length = 36)
    private String gameId;

    @Column(name = "game_type", nullable = false, length = 50)
    private String gameType;

    @Column(name = "building_id", nullable = false, length = 36)
    private String buildingId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_s")
    private Integer durationSeconds;

    @Column(name = "winner_id", length = 36)
    private String winnerId;

    @Column(name = "win_condition", length = 30)
    private String winCondition;

    @Column(name = "result_data", columnDefinition = "JSON")
    @Convert(converter = JsonStringUnwrappingConverter.class)
    private String resultData;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id")
    private List<SessionParticipantJpaEntity> participants = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

    @Column(name = "tournament_match_id", length = 36)
    private String tournamentMatchId;

    @Column(name = "tournament_id", length = 36)
    private String tournamentId;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public GameSessionJpaEntity() {
    }

    /**
     * Costruisce una nuova sessione di gioco con tutti i campi.
     *
     * @param id                identificatore univoco della sessione
     * @param gameId            identificatore della postazione gioco
     * @param gameType          tipo di gioco
     * @param buildingId        identificativo dell'edificio
     * @param status            stato della sessione
     * @param startedAt         istante di inizio della sessione
     * @param endedAt           istante di fine (può essere {@code null})
     * @param durationSeconds   durata in secondi (può essere {@code null})
     * @param winnerId          identificativo del vincitore (può essere {@code null})
     * @param winCondition      condizione di vittoria (può essere {@code null})
     * @param resultData        dati di risultato in formato JSON (può essere {@code null})
     * @param participants      elenco dei partecipanti alla sessione
     * @param tournamentMatchId identificativo dell'incontro torneo associato (può essere {@code null})
     * @param tournamentId      identificativo del torneo associato (può essere {@code null})
     */
    public GameSessionJpaEntity(String id, String gameId, String gameType, String buildingId, String status, Instant startedAt, Instant endedAt, Integer durationSeconds, String winnerId, String winCondition, String resultData, List<SessionParticipantJpaEntity> participants, String tournamentMatchId, String tournamentId) {
        this.id = id;
        this.gameId = gameId;
        this.gameType = gameType;
        this.buildingId = buildingId;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.winnerId = winnerId;
        this.winCondition = winCondition;
        this.resultData = resultData;
        this.participants = participants != null ? participants : new ArrayList<>();
        this.tournamentMatchId = tournamentMatchId;
        this.tournamentId = tournamentId;
    }

    /**
     * Restituisce l'identificatore univoco della sessione.
     *
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificatore univoco della sessione.
     *
     * @param id nuovo identificatore
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Restituisce l'identificatore della postazione gioco.
     *
     * @return gameId
     */
    public String getGameId() {
        return gameId;
    }

    /**
     * Imposta l'identificatore della postazione gioco.
     *
     * @param gameId nuovo identificatore postazione
     */
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    /**
     * Restituisce il tipo di gioco della sessione.
     *
     * @return gameType
     */
    public String getGameType() {
        return gameType;
    }

    /**
     * Imposta il tipo di gioco della sessione.
     *
     * @param gameType nuovo tipo di gioco
     */
    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    /**
     * Restituisce l'identificativo dell'edificio in cui si svolge la sessione.
     *
     * @return buildingId
     */
    public String getBuildingId() {
        return buildingId;
    }

    /**
     * Imposta l'identificativo dell'edificio.
     *
     * @param buildingId nuovo identificativo edificio
     */
    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    /**
     * Restituisce lo stato della sessione.
     *
     * @return status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Imposta lo stato della sessione.
     *
     * @param status nuovo stato
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Restituisce l'istante di inizio della sessione.
     *
     * @return startedAt
     */
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Imposta l'istante di inizio della sessione.
     *
     * @param startedAt nuovo istante di inizio
     */
    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * Restituisce l'istante di fine della sessione.
     *
     * @return endedAt (può essere {@code null} se la sessione è ancora in corso)
     */
    public Instant getEndedAt() {
        return endedAt;
    }

    /**
     * Imposta l'istante di fine della sessione.
     *
     * @param endedAt nuovo istante di fine
     */
    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    /**
     * Restituisce la durata in secondi della sessione.
     *
     * @return durationSeconds (può essere {@code null})
     */
    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * Imposta la durata in secondi della sessione.
     *
     * @param durationSeconds nuova durata in secondi
     */
    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    /**
     * Restituisce l'identificativo del vincitore della sessione.
     *
     * @return winnerId (può essere {@code null})
     */
    public String getWinnerId() {
        return winnerId;
    }

    /**
     * Imposta l'identificativo del vincitore della sessione.
     *
     * @param winnerId nuovo identificativo vincitore
     */
    public void setWinnerId(String winnerId) {
        this.winnerId = winnerId;
    }

    /**
     * Restituisce la condizione di vittoria della sessione.
     *
     * @return winCondition (può essere {@code null})
     */
    public String getWinCondition() {
        return winCondition;
    }

    /**
     * Imposta la condizione di vittoria della sessione.
     *
     * @param winCondition nuova condizione di vittoria
     */
    public void setWinCondition(String winCondition) {
        this.winCondition = winCondition;
    }

    /**
     * Restituisce i dati di risultato in formato JSON.
     *
     * @return resultData (può essere {@code null})
     */
    public String getResultData() {
        return resultData;
    }

    /**
     * Imposta i dati di risultato in formato JSON.
     *
     * @param resultData nuovi dati di risultato
     */
    public void setResultData(String resultData) {
        this.resultData = resultData;
    }

    /**
     * Restituisce l'elenco dei partecipanti alla sessione.
     *
     * @return lista di partecipanti
     */
    public List<SessionParticipantJpaEntity> getParticipants() {
        return participants;
    }

    /**
     * Imposta l'elenco dei partecipanti alla sessione.
     *
     * @param participants nuova lista di partecipanti
     */
    public void setParticipants(List<SessionParticipantJpaEntity> participants) {
        this.participants = participants;
    }

    /**
     * Restituisce la versione per l'optimistic locking.
     *
     * @return version
     */
    public Long getVersion() {
        return version;
    }

    /**
     * Imposta la versione per l'optimistic locking.
     *
     * @param version nuova versione
     */
    public void setVersion(Long version) {
        this.version = version;
    }

    /**
     * Restituisce l'identificativo dell'incontro torneo associato.
     *
     * @return tournamentMatchId (può essere {@code null})
     */
    public String getTournamentMatchId() {
        return tournamentMatchId;
    }

    /**
     * Imposta l'identificativo dell'incontro torneo associato.
     *
     * @param tournamentMatchId nuovo identificativo incontro torneo
     */
    public void setTournamentMatchId(String tournamentMatchId) {
        this.tournamentMatchId = tournamentMatchId;
    }

    /**
     * Restituisce l'identificativo del torneo associato.
     *
     * @return tournamentId (può essere {@code null})
     */
    public String getTournamentId() {
        return tournamentId;
    }

    /**
     * Imposta l'identificativo del torneo associato.
     *
     * @param tournamentId nuovo identificativo torneo
     */
    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }
}
