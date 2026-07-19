package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import com.gameplatform.shared.domain.model.GameMachineStatus;
import jakarta.persistence.*;

/**
 * JPA entity per la tabella {@code game_catalog}.
 * Rappresenta un'istanza fisica di una postazione gioco (macchina) situata
 * in un edificio, con stato operativo tracciato tramite {@link GameMachineStatus}
 * e ottimistic locking per la concorrenza.
 *
 * @see GameDefinitionLocalJpaEntity
 * @see GameMachineStatus
 */
@Entity
@Table(name = "game_catalog")
public class GameJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "game_type", nullable = false, length = 50)
    private String gameType;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "building_id", nullable = false, length = 50)
    private String buildingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GameMachineStatus status;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public GameJpaEntity() {
    }

    /**
     * Costruisce una nuova istanza di gioco.
     *
     * @param id         identificatore univoco della postazione
     * @param gameType   tipo di gioco associato
     * @param name       nome visualizzato della postazione
     * @param buildingId identificativo dell'edificio in cui si trova
     * @param status     stato operativo della macchina
     */
    public GameJpaEntity(String id, String gameType, String name, String buildingId, GameMachineStatus status) {
        this.id = id;
        this.gameType = gameType;
        this.name = name;
        this.buildingId = buildingId;
        this.status = status;
    }

    /**
     * Restituisce l'identificatore univoco della postazione.
     *
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificatore univoco della postazione.
     *
     * @param id nuovo identificatore
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Restituisce il tipo di gioco associato.
     *
     * @return gameType
     */
    public String getGameType() {
        return gameType;
    }

    /**
     * Imposta il tipo di gioco associato.
     *
     * @param gameType nuovo tipo di gioco
     */
    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    /**
     * Restituisce il nome visualizzato della postazione.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Imposta il nome visualizzato della postazione.
     *
     * @param name nuovo nome
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Restituisce l'identificativo dell'edificio in cui si trova la postazione.
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
     * Restituisce lo stato operativo della macchina.
     *
     * @return status
     */
    public GameMachineStatus getStatus() {
        return status;
    }

    /**
     * Imposta lo stato operativo della macchina.
     *
     * @param status nuovo stato
     */
    public void setStatus(GameMachineStatus status) {
        this.status = status;
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
}
