package com.gameplatform.local.domain.model;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;

/**
 * Modello del dominio che rappresenta una postazione di gioco (macchina)
 * all'interno di un edificio. Gestisce il ciclo di vita dello stato della
 * macchina attraverso transizioni quali prenotazione, utilizzo, rilascio
 * e manutenzione, con validazione delle transizioni di stato consentite.
 *
 * @see GameMachineStatus
 * @see GameType
 * @see BuildingId
 */
public class Game {
    private final GameId id;
    private final GameType gameType;
    private String name;
    private final BuildingId buildingId;
    private GameMachineStatus status;
    private long version;

    /**
     * Costruisce una nuova postazione di gioco con versione iniziale 0.
     *
     * @param id         identificatore univoco della postazione (non null)
     * @param gameType   tipo di gioco della postazione (non null)
     * @param name       nome della postazione (non blank)
     * @param buildingId identificatore dell'edificio di appartenenza (non null)
     * @param status     stato iniziale della postazione (non null)
     * @throws IllegalArgumentException se uno qualsiasi dei parametri obbligatori è null o blank
     */
    public Game(GameId id, GameType gameType, String name, BuildingId buildingId, GameMachineStatus status) {
        this(id, gameType, name, buildingId, status, 0L);
    }

    /**
     * Costruisce una nuova postazione di gioco con versione specificata.
     *
     * @param id         identificatore univoco della postazione (non null)
     * @param gameType   tipo di gioco della postazione (non null)
     * @param name       nome della postazione (non blank)
     * @param buildingId identificatore dell'edificio di appartenenza (non null)
     * @param status     stato iniziale della postazione (non null)
     * @param version    versione per controllo concorrenza ottimistico
     * @throws IllegalArgumentException se uno qualsiasi dei parametri obbligatori è null o blank
     */
    public Game(GameId id, GameType gameType, String name, BuildingId buildingId, GameMachineStatus status, long version) {
        if (id == null) {
            throw new IllegalArgumentException("GameId cannot be null");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("GameMachineStatus cannot be null");
        }
        this.id = id;
        this.gameType = gameType;
        this.name = name;
        this.buildingId = buildingId;
        this.status = status;
        this.version = version;
    }

    /**
     * Transita la postazione allo stato {@code RESERVED} se attualmente disponibile.
     *
     * @throws InvalidGameStateTransitionException se la postazione non è in stato AVAILABLE
     */
    public void reserve() {
        if (status != GameMachineStatus.AVAILABLE) {
            throw new InvalidGameStateTransitionException(
                "Cannot reserve game machine because its current status is: " + status
            );
        }
        this.status = GameMachineStatus.RESERVED;
    }

    /**
     * Avvia l'utilizzo della postazione, portandola allo stato {@code IN_USE}.
     * Consentito solo se la postazione è AVAILABLE, RESERVED o LOBBY.
     *
     * @throws InvalidGameStateTransitionException se la postazione non è in uno stato valido per l'avvio
     */
    public void startUse() {
        if (status != GameMachineStatus.AVAILABLE && status != GameMachineStatus.RESERVED && status != GameMachineStatus.LOBBY) {
            throw new InvalidGameStateTransitionException(
                "Cannot start using game machine because its current status is: " + status
            );
        }
        this.status = GameMachineStatus.IN_USE;
    }

    /**
     * Rilascia la postazione riportandola allo stato {@code AVAILABLE}.
     * Se già disponibile, l'operazione è un no-op.
     *
     * @throws InvalidGameStateTransitionException se la postazione è in uno stato non rilasciabile
     */
    public void release() {
        if (status == GameMachineStatus.AVAILABLE) {
            return;
        }
        if (status != GameMachineStatus.IN_USE && status != GameMachineStatus.RESERVED && status != GameMachineStatus.MAINTENANCE && status != GameMachineStatus.LOBBY) {
            throw new InvalidGameStateTransitionException(
                "Cannot release game machine because its current status is: " + status
            );
        }
        this.status = GameMachineStatus.AVAILABLE;
    }

    /**
     * Imposta la postazione in stato di manutenzione.
     */
    public void setMaintenance() {
        this.status = GameMachineStatus.MAINTENANCE;
    }

    /**
     * Imposta la postazione in modalità lobby.
     *
     * @throws InvalidGameStateTransitionException se la postazione non è in stato AVAILABLE
     */
    public void setLobby() {
        if (status != GameMachineStatus.AVAILABLE) {
            throw new InvalidGameStateTransitionException(
                "Cannot set game machine to LOBBY because its current status is: " + status
            );
        }
        this.status = GameMachineStatus.LOBBY;
    }

    /**
     * Rinomina la postazione di gioco.
     *
     * @param newName il nuovo nome (non blank)
     * @throws IllegalArgumentException se newName è null o blank
     */
    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null, empty or blank");
        }
        this.name = newName;
    }

    /**
     * Restituisce l'identificatore univoco della postazione.
     *
     * @return id
     */
    public GameId getId() {
        return id;
    }

    /**
     * Restituisce il tipo di gioco della postazione.
     *
     * @return gameType
     */
    public GameType getGameType() {
        return gameType;
    }

    /**
     * Restituisce il nome della postazione.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Restituisce l'identificatore dell'edificio di appartenenza.
     *
     * @return buildingId
     */
    public BuildingId getBuildingId() {
        return buildingId;
    }

    /**
     * Restituisce lo stato corrente della postazione.
     *
     * @return status
     */
    public GameMachineStatus getStatus() {
        return status;
    }

    /**
     * Restituisce la versione per controllo concorrenza ottimistico.
     *
     * @return version
     */
    public long getVersion() {
        return version;
    }
}

