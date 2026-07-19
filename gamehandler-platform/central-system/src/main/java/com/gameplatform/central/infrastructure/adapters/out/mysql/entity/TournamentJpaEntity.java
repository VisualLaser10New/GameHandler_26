package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entità JPA per la tabella {@code tournaments} del database MySQL.
 *
 * <p>Rappresenta un torneo gestito dal sistema centrale, includendone i metadati
 * di configurazione (tipo di gioco, formato, dimensione delle squadre) e il ciclo
 * di vita (stato, periodo di svolgimento). La chiave primaria è l'identificativo
 * del torneo. Non sono dichiarate relazioni JPA: i riferimenti (es. creatore) sono
 * mantenuti come colonne testuali, secondo la convenzione esagonale adottata nel
 * progetto. Il campo {@code endsAt} può essere {@code null} per tornei a data di
 * fine non ancora definita.</p>
 *
 * @see TournamentParticipantJpaEntity
 * @see TournamentMatchJpaEntity
 */
@Entity
@Table(name = "tournaments")
public class TournamentJpaEntity {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;
    @Column(name = "name", length = 200, nullable = false)
    private String name;
    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;
    @Column(name = "team_based", nullable = false)
    private Boolean teamBased;
    @Column(name = "team_size", nullable = false)
    private Integer teamSize;
    @Column(name = "format", length = 30, nullable = false)
    private String format;
    @Column(name = "status", length = 30, nullable = false)
    private String status;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at")
    private Instant endsAt;
    @Column(name = "created_by", length = 36, nullable = false)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public TournamentJpaEntity() {
    }

    /**
     * Costruisce un torneo con i metadati e il ciclo di vita forniti.
     *
     * @param id identificativo univoco del torneo; non deve essere {@code null}
     * @param name nome descrittivo del torneo; non deve essere {@code null}
     * @param gameType tipo di gioco disputato nel torneo; non deve essere {@code null}
     * @param teamBased indica se il torneo è a squadre; non deve essere {@code null}
     * @param teamSize dimensione della squadra; non deve essere {@code null} e positivo
     * @param format formato del torneo (es. eliminazione, round-robin); non deve essere {@code null}
     * @param status stato corrente del torneo; non deve essere {@code null}
     * @param startsAt istante di inizio del torneo; non deve essere {@code null}
     * @param endsAt istante di fine del torneo; può essere {@code null}
     * @param createdBy identificativo dell'utente creatore; non deve essere {@code null}
     * @param createdAt istante di creazione del torneo; non deve essere {@code null}
     */
    public TournamentJpaEntity(String id, String name, String gameType, Boolean teamBased, Integer teamSize,
                               String format, String status, Instant startsAt, Instant endsAt,
                               String createdBy, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.gameType = gameType;
        this.teamBased = teamBased;
        this.teamSize = teamSize;
        this.format = format;
        this.status = status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /**
     * Restituisce l'identificativo univoco del torneo.
     *
     * @return l'identificativo del torneo; non deve essere {@code null}
     */
    public String getId() { return id; }

    /**
     * Imposta l'identificativo univoco del torneo.
     *
     * @param id nuovo identificativo del torneo; può essere {@code null}
     */
    public void setId(String id) { this.id = id; }

    /**
     * Restituisce il nome descrittivo del torneo.
     *
     * @return il nome del torneo; non deve essere {@code null}
     */
    public String getName() { return name; }

    /**
     * Imposta il nome descrittivo del torneo.
     *
     * @param name nuovo nome del torneo; non deve essere {@code null}
     */
    public void setName(String name) { this.name = name; }

    /**
     * Restituisce il tipo di gioco disputato nel torneo.
     *
     * @return il tipo di gioco; non deve essere {@code null}
     */
    public String getGameType() { return gameType; }

    /**
     * Imposta il tipo di gioco disputato nel torneo.
     *
     * @param gameType nuovo tipo di gioco; non deve essere {@code null}
     */
    public void setGameType(String gameType) { this.gameType = gameType; }

    /**
     * Indica se il torneo è a squadre.
     *
     * @return {@code true} se il torneo è a squadre, {@code false} altrimenti;
     *         non deve essere {@code null}
     */
    public Boolean getTeamBased() { return teamBased; }

    /**
     * Imposta se il torneo è a squadre.
     *
     * @param teamBased nuovo valore che indica la natura a squadre; non deve essere {@code null}
     */
    public void setTeamBased(Boolean teamBased) { this.teamBased = teamBased; }

    /**
     * Restituisce la dimensione della squadra prevista per il torneo.
     *
     * @return la dimensione della squadra; non deve essere {@code null} e positivo
     */
    public Integer getTeamSize() { return teamSize; }

    /**
     * Imposta la dimensione della squadra prevista per il torneo.
     *
     * @param teamSize nuova dimensione della squadra; non deve essere {@code null} e positivo
     */
    public void setTeamSize(Integer teamSize) { this.teamSize = teamSize; }

    /**
     * Restituisce il formato del torneo.
     *
     * @return il formato del torneo; non deve essere {@code null}
     */
    public String getFormat() { return format; }

    /**
     * Imposta il formato del torneo.
     *
     * @param format nuovo formato del torneo; non deve essere {@code null}
     */
    public void setFormat(String format) { this.format = format; }

    /**
     * Restituisce lo stato corrente del torneo.
     *
     * @return lo stato del torneo; non deve essere {@code null}
     */
    public String getStatus() { return status; }

    /**
     * Imposta lo stato corrente del torneo.
     *
     * @param status nuovo stato del torneo; non deve essere {@code null}
     */
    public void setStatus(String status) { this.status = status; }

    /**
     * Restituisce l'istante di inizio del torneo.
     *
     * @return l'istante di inizio; non deve essere {@code null}
     */
    public Instant getStartsAt() { return startsAt; }

    /**
     * Imposta l'istante di inizio del torneo.
     *
     * @param startsAt nuovo istante di inizio; non deve essere {@code null}
     */
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }

    /**
     * Restituisce l'istante di fine del torneo.
     *
     * @return l'istante di fine; può essere {@code null} se non ancora definito
     */
    public Instant getEndsAt() { return endsAt; }

    /**
     * Imposta l'istante di fine del torneo.
     *
     * @param endsAt nuovo istante di fine; può essere {@code null}
     */
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }

    /**
     * Restituisce l'identificativo dell'utente che ha creato il torneo.
     *
     * @return l'identificativo del creatore; non deve essere {@code null}
     */
    public String getCreatedBy() { return createdBy; }

    /**
     * Imposta l'identificativo dell'utente che ha creato il torneo.
     *
     * @param createdBy nuovo identificativo del creatore; non deve essere {@code null}
     */
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    /**
     * Restituisce l'istante di creazione del torneo.
     *
     * @return l'istante di creazione; non deve essere {@code null}
     */
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Imposta l'istante di creazione del torneo.
     *
     * @param createdAt nuovo istante di creazione; non deve essere {@code null}
     */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}