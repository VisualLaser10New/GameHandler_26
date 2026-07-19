package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.result.GameResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Modello del dominio che rappresenta una sessione di gioco, gestendo il ciclo
 * di vita completo: creazione, avvio, pausa, ripresa, completamento e aborto.
 * Supporta il tracciamento dei partecipanti, la durata effettiva (al netto
 * delle pause) e l'associazione opzionale a un match di torneo.
 *
 * @see GameStatus
 * @see GameResult
 * @see TournamentMatchLocal
 */
public class GameSession {
    private final GameSessionId id;
    private final GameId gameId;
    private final GameType gameType;
    private final BuildingId buildingId;
    private GameStatus status;
    private Instant startedAt;
    private Instant endedAt;
    private Integer durationSeconds;
    private UserId winnerId;
    private WinCondition winCondition;
    private GameResult result;
    private List<UserId> participants;

    // Pause tracking: when the session was paused, and how many seconds
    // of pause have accumulated across all pause/resume cycles.
    private Instant pausedAt;
    private int accumulatedPausedSeconds;
    private long version;

    // FASE 6 — tournament binding (nullable). When non-null the session is
    // bound to a tournament match; the local end/abort flows emit an extra
    // TOURNAMENT_MATCH_COMPLETED outbox row and flip the local match status.
    private final TournamentMatchId tournamentMatchId;
    private final TournamentId tournamentId;

    /**
     * Costruisce una sessione di gioco con partecipanti e versione iniziale 0.
     *
     * @param id               identificatore della sessione (non null)
     * @param gameId           identificatore della postazione di gioco (non null)
     * @param gameType         tipo di gioco (non null)
     * @param buildingId       identificatore dell'edificio (non null)
     * @param status           stato iniziale della sessione (non null)
     * @param startedAt        istante di inizio (non null)
     * @param endedAt          istante di fine (può essere null)
     * @param durationSeconds  durata in secondi (può essere null)
     * @param winnerId         identificatore del vincitore (può essere null)
     * @param winCondition     condizione di vittoria (può essere null)
     * @param result           risultato della partita (può essere null)
     * @param participants     lista dei partecipanti (può essere null)
     */
    public GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId, GameStatus status,
                       Instant startedAt, Instant endedAt, Integer durationSeconds, UserId winnerId,
                       WinCondition winCondition, GameResult result, List<UserId> participants) {
        this(id, gameId, gameType, buildingId, status, startedAt, endedAt, durationSeconds, winnerId,
             winCondition, result, participants, 0L);
    }

    /**
     * Costruisce una sessione di gioco con partecipanti e versione specificata,
     * senza associazione a torneo.
     *
     * @param id               identificatore della sessione (non null)
     * @param gameId           identificatore della postazione di gioco (non null)
     * @param gameType         tipo di gioco (non null)
     * @param buildingId       identificatore dell'edificio (non null)
     * @param status           stato iniziale della sessione (non null)
     * @param startedAt        istante di inizio (non null)
     * @param endedAt          istante di fine (può essere null)
     * @param durationSeconds  durata in secondi (può essere null)
     * @param winnerId         identificatore del vincitore (può essere null)
     * @param winCondition     condizione di vittoria (può essere null)
     * @param result           risultato della partita (può essere null)
     * @param participants     lista dei partecipanti (può essere null)
     * @param version          versione per controllo concorrenza ottimistico
     */
    public GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId, GameStatus status,
                       Instant startedAt, Instant endedAt, Integer durationSeconds, UserId winnerId,
                       WinCondition winCondition, GameResult result, List<UserId> participants, long version) {
        this(id, gameId, gameType, buildingId, status, startedAt, endedAt, durationSeconds, winnerId,
             winCondition, result, participants, version, null, null);
    }

    /**
     * FASE 6 tournament-aware ctor (version=0) — delegates to the 15-arg
     * primary ctor. Used by {@code GameSessionService.start(...)} 5-arg
     * overload to bind a session to a tournament match.
     */
    /**
     * Costruisce una sessione di gioco associata a un match di torneo con versione 0.
     *
     * @param id                 identificatore della sessione (non null)
     * @param gameId             identificatore della postazione di gioco (non null)
     * @param gameType           tipo di gioco (non null)
     * @param buildingId         identificatore dell'edificio (non null)
     * @param status             stato iniziale della sessione (non null)
     * @param startedAt          istante di inizio (non null)
     * @param endedAt            istante di fine (può essere null)
     * @param durationSeconds    durata in secondi (può essere null)
     * @param winnerId           identificatore del vincitore (può essere null)
     * @param winCondition       condizione di vittoria (può essere null)
     * @param result             risultato della partita (può essere null)
     * @param participants       lista dei partecipanti (può essere null)
     * @param tournamentMatchId  identificatore del match di torneo (può essere null)
     * @param tournamentId       identificatore del torneo (può essere null)
     * @see TournamentMatchLocal
     */
    public GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId, GameStatus status,
                       Instant startedAt, Instant endedAt, Integer durationSeconds, UserId winnerId,
                       WinCondition winCondition, GameResult result, List<UserId> participants,
                       TournamentMatchId tournamentMatchId, TournamentId tournamentId) {
        this(id, gameId, gameType, buildingId, status, startedAt, endedAt, durationSeconds, winnerId,
             winCondition, result, participants, 0L, tournamentMatchId, tournamentId);
    }

    /**
     * Costruttore primario che inizializza tutti i campi della sessione di gioco.
     *
     * @param id                 identificatore della sessione (non null)
     * @param gameId             identificatore della postazione di gioco (non null)
     * @param gameType           tipo di gioco (non null)
     * @param buildingId         identificatore dell'edificio (non null)
     * @param status             stato iniziale della sessione (non null)
     * @param startedAt          istante di inizio (non null)
     * @param endedAt            istante di fine (può essere null)
     * @param durationSeconds    durata in secondi (può essere null)
     * @param winnerId           identificatore del vincitore (può essere null)
     * @param winCondition       condizione di vittoria (può essere null)
     * @param result             risultato della partita (può essere null)
     * @param participants       lista dei partecipanti (può essere null, viene copia difensiva)
     * @param version            versione per controllo concorrenza ottimistico
     * @param tournamentMatchId  identificatore del match di torneo (può essere null)
     * @param tournamentId       identificatore del torneo (può essere null)
     * @throws IllegalArgumentException se id, gameId, gameType, buildingId, status o startedAt sono null
     */
    public GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId, GameStatus status,
                       Instant startedAt, Instant endedAt, Integer durationSeconds, UserId winnerId,
                       WinCondition winCondition, GameResult result, List<UserId> participants, long version,
                       TournamentMatchId tournamentMatchId, TournamentId tournamentId) {
        if (id == null) {
            throw new IllegalArgumentException("GameSessionId cannot be null");
        }
        if (gameId == null) {
            throw new IllegalArgumentException("GameId cannot be null");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("GameStatus cannot be null");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("StartedAt cannot be null");
        }
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
        this.result = result;
        this.participants = participants != null ? List.copyOf(participants) : List.of();
        this.version = version;
        this.tournamentMatchId = tournamentMatchId;
        this.tournamentId = tournamentId;
    }

    /**
     * Costruttore per compatibilità con sessioni senza partecipanti (delega al costruttore con lista vuota).
     *
     * @param id              identificatore della sessione (non null)
     * @param gameId          identificatore della postazione di gioco (non null)
     * @param gameType        tipo di gioco (non null)
     * @param buildingId      identificatore dell'edificio (non null)
     * @param status          stato iniziale della sessione (non null)
     * @param startedAt       istante di inizio (non null)
     * @param endedAt         istante di fine (può essere null)
     * @param durationSeconds durata in secondi (può essere null)
     * @param winnerId        identificatore del vincitore (può essere null)
     * @param winCondition    condizione di vittoria (può essere null)
     * @param result          risultato della partita (può essere null)
     */
    public GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId, GameStatus status,
                       Instant startedAt, Instant endedAt, Integer durationSeconds, UserId winnerId,
                       WinCondition winCondition, GameResult result) {
        this(id, gameId, gameType, buildingId, status, startedAt, endedAt, durationSeconds, winnerId, winCondition, result, new ArrayList<>());
    }

    /**
     * Completa la sessione con il risultato specificato, utilizzando l'istante corrente.
     *
     * @param result risultato della partita
     * @see #complete(GameResult, Instant)
     */
    public void complete(GameResult result) {
        complete(result, Instant.now());
    }

    /**
     * Completa la sessione con il risultato e l'istante di fine specificati.
     * Imposta vincitore, condizione di vittoria e calcola la durata effettiva.
     *
     * @param result  risultato della partita
     * @param endedAt istante di completamento
     * @throws InvalidGameStateTransitionException se la sessione è già completata
     *                                              o non è in uno stato valido (IN_PROGRESS, PAUSED, ABORTED)
     */
    public void complete(GameResult result, Instant endedAt) {
        if (this.status == GameStatus.COMPLETED) {
            throw new InvalidGameStateTransitionException("Cannot complete session because it is already completed");
        }
        if (this.status != GameStatus.IN_PROGRESS && this.status != GameStatus.PAUSED && this.status != GameStatus.ABORTED) {
            throw new InvalidGameStateTransitionException("Cannot complete session because its current status is: " + this.status);
        }
        this.status = GameStatus.COMPLETED;
        this.result = result;
        this.endedAt = endedAt;
        if (result != null) {
            this.winnerId = result.getWinnerId();
            this.winCondition = result.getWinCondition();
        }
        calculateDuration();
    }

    /**
     * Aborta la sessione con la ragione specificata, utilizzando l'istante corrente.
     *
     * @param reason ragione dell'aborto
     * @see #abort(StopReason, Instant)
     */
    public void abort(StopReason reason) {
        abort(reason, Instant.now());
    }

    /**
     * Aborta la sessione con la ragione e l'istante di fine specificati.
     * Se la ragione è TIMEOUT, imposta WinCondition.TIMEOUT; altrimenti WinCondition.ABANDONED.
     *
     * @param reason  ragione dell'aborto
     * @param endedAt istante di fine
     * @throws InvalidGameStateTransitionException se la sessione è già ABORTED o COMPLETED,
     *                                              o non è IN_PROGRESS o PAUSED
     */
    public void abort(StopReason reason, Instant endedAt) {
        if (this.status == GameStatus.ABORTED || this.status == GameStatus.COMPLETED) {
            throw new InvalidGameStateTransitionException("Cannot abort session because it is already " + this.status);
        }
        if (this.status != GameStatus.IN_PROGRESS && this.status != GameStatus.PAUSED) {
            throw new InvalidGameStateTransitionException("Cannot abort session because its current status is: " + this.status);
        }
        this.status = GameStatus.ABORTED;
        this.endedAt = endedAt;
        if (reason == StopReason.TIMEOUT) {
            this.winCondition = WinCondition.TIMEOUT;
        } else {
            this.winCondition = WinCondition.ABANDONED;
        }
        calculateDuration();
    }

    /**
     * Cancella una lobby in attesa, portando la sessione allo stato ABORTED.
     *
     * @param endedAt istante di cancellazione
     * @throws InvalidGameStateTransitionException se la sessione non è in stato WAITING
     */
    public void cancelLobby(Instant endedAt) {
        if (this.status != GameStatus.WAITING) {
            throw new InvalidGameStateTransitionException("Cannot cancel lobby because its current status is: " + this.status);
        }
        this.status = GameStatus.ABORTED;
        this.endedAt = endedAt;
        this.winCondition = WinCondition.TIMEOUT;
        calculateDuration();
    }

    /**
     * Rinnova la lobby aggiornando il timestamp {@code startedAt} all'istante corrente,
     * in modo da ripristinare la finestra di inattività utilizzata da
     * {@link com.gameplatform.local.application.service.LobbyExpirationService}.
     * Solo valido se la sessione è ancora in stato WAITING.
     *
     * <p>Non modifica {@code endedAt}; al momento della cancellazione o dell'avvio,
     * {@link #calculateDuration()} misura comunque dalla creazione originale,
     * lasciando inalterate le statistiche.
     *
     * @param now istante corrente per il rinnovo
     */
    public void renewLobby(Instant now) {
        if (this.status != GameStatus.WAITING) {
            return;
        }
        this.startedAt = now;
    }

    /**
     * Mette in pausa la sessione con l'istante corrente.
     *
     * @see #pause(Instant)
     */
    public void pause() {
        pause(Instant.now());
    }

    /**
     * Mette in pausa la sessione all'istante specificato.
     *
     * @param pausedAt istante di pausa
     * @throws InvalidGameStateTransitionException se la sessione non è IN_PROGRESS
     */
    public void pause(Instant pausedAt) {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStateTransitionException("Cannot pause session because its current status is: " + this.status);
        }
        this.status = GameStatus.PAUSED;
        this.pausedAt = pausedAt;
    }

    /**
     * Riprende la sessione in pausa con l'istante corrente.
     *
     * @see #resume(Instant)
     */
    public void resume() {
        resume(Instant.now());
    }

    /**
     * Riprende la sessione in pausa all'istante specificato, accumulando
     * i secondi di pausa trascorsi.
     *
     * @param resumedAt istante di ripresa
     * @throws InvalidGameStateTransitionException se la sessione non è PAUSED
     */
    public void resume(Instant resumedAt) {
        if (this.status != GameStatus.PAUSED) {
            throw new InvalidGameStateTransitionException("Cannot resume session because its current status is: " + this.status);
        }
        this.status = GameStatus.IN_PROGRESS;
        if (this.pausedAt != null) {
            this.accumulatedPausedSeconds += (int) Duration.between(this.pausedAt, resumedAt).toSeconds();
            this.pausedAt = null;
        }
    }

    /**
     * Calcola la durata effettiva della sessione, sottraendo il tempo di pausa
     * dalla durata totale (differenza tra endedAt e startedAt).
     */
    public void calculateDuration() {
        if (startedAt != null && endedAt != null) {
            int totalSeconds = (int) Duration.between(startedAt, endedAt).toSeconds();
            int pausedSeconds = accumulatedPausedSeconds;
            if (pausedAt != null && !endedAt.isBefore(pausedAt)) {
                pausedSeconds += (int) Duration.between(pausedAt, endedAt).toSeconds();
            }
            this.durationSeconds = Math.max(0, totalSeconds - pausedSeconds);
        }
    }

    /**
     * Restituisce l'identificatore della sessione.
     *
     * @return id
     */
    public GameSessionId getId() {
        return id;
    }

    /**
     * Restituisce l'identificatore della postazione di gioco.
     *
     * @return gameId
     */
    public GameId getGameId() {
        return gameId;
    }

    /**
     * Restituisce il tipo di gioco.
     *
     * @return gameType
     */
    public GameType getGameType() {
        return gameType;
    }

    /**
     * Restituisce l'identificatore dell'edificio.
     *
     * @return buildingId
     */
    public BuildingId getBuildingId() {
        return buildingId;
    }

    /**
     * Restituisce lo stato corrente della sessione.
     *
     * @return status
     */
    public GameStatus getStatus() {
        return status;
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
     * Restituisce l'istante di fine della sessione.
     *
     * @return endedAt, o null se la sessione non è terminata
     */
    public Instant getEndedAt() {
        return endedAt;
    }

    /**
     * Restituisce la durata effettiva in secondi (al netto delle pause).
     *
     * @return durationSeconds, o null se non ancora calcolata
     */
    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * Restituisce l'istante di pausa corrente, se la sessione è in pausa.
     *
     * @return pausedAt, o null se non in pausa
     */
    public Instant getPausedAt() {
        return pausedAt;
    }

    /**
     * Restituisce i secondi di pausa accumulati.
     *
     * @return accumulatedPausedSeconds
     */
    public int getAccumulatedPausedSeconds() {
        return accumulatedPausedSeconds;
    }

    /**
     * Restituisce l'identificatore del vincitore.
     *
     * @return winnerId, o null se non determinato
     */
    public UserId getWinnerId() {
        return winnerId;
    }

    /**
     * Restituisce la condizione di vittoria.
     *
     * @return winCondition, o null se non determinata
     */
    public WinCondition getWinCondition() {
        return winCondition;
    }

    /**
     * Restituisce il risultato della partita.
     *
     * @return result, o null se non ancora completata
     */
    public GameResult getResult() {
        return result;
    }

    /**
     * Restituisce una copia immutabile della lista dei partecipanti.
     *
     * @return lista dei partecipanti
     */
    public List<UserId> getParticipants() {
        return List.copyOf(participants);
    }

    /**
     * Aggiunge un partecipante alla sessione. Se già presente, l'operazione è un no-op.
     *
     * @param userId identificatore dell'utente da aggiungere (non null)
     * @throws IllegalArgumentException se userId è null
     */
    public void addParticipant(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (this.participants.contains(userId)) {
            return; // Already in participants
        }
        List<UserId> newList = new ArrayList<>(this.participants);
        newList.add(userId);
        this.participants = List.copyOf(newList);
    }

    /**
     * Removes a participant from the lobby.  Mirrors {@link #addParticipant}
     * but for the lobby "leave" flow.  Idempotent: returning silently when the
     * user is not in the list mirrors the no-op-on-duplicate behaviour of
     * {@code addParticipant} and makes the operation resilient to MQTT
     * QoS-1 redelivery (a re-delivered {@code lobby/leave} for an already
     * removed participant is a no-op rather than an error).  The lobby
     * creator (participants.get(0)) can never be removed through this
     * method — they must use {@link #cancelLobby(Instant)} instead, otherwise
     * the lobby would be orphaned without a creator.
     */
    public void removeParticipant(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (!this.participants.isEmpty() && this.participants.get(0).equals(userId)) {
            throw new IllegalStateException("The lobby creator cannot leave; cancel the lobby instead");
        }
        if (!this.participants.contains(userId)) {
            return; // Idempotent no-op for QoS-1 redelivery / non-participant
        }
        List<UserId> newList = new ArrayList<>(this.participants);
        newList.remove(userId);
        this.participants = List.copyOf(newList);
    }

    /**
     * Imposta lo stato della sessione.
     *
     * @param status nuovo stato
     */
    public void setStatus(GameStatus status) {
        this.status = status;
    }

    /**
     * Restituisce la versione per controllo concorrenza ottimistico.
     *
     * @return version
     */
    public long getVersion() {
        return version;
    }

    /**
     * Restituisce l'identificatore del match di torneo associato, se presente.
     *
     * @return tournamentMatchId, o null se non associata a un torneo
     */
    public TournamentMatchId getTournamentMatchId() {
        return tournamentMatchId;
    }

    /**
     * Restituisce l'identificatore del torneo associato, se presente.
     *
     * @return tournamentId, o null se non associata a un torneo
     */
    public TournamentId getTournamentId() {
        return tournamentId;
    }
}

