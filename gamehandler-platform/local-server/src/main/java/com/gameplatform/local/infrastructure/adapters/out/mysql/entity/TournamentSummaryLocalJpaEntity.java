package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code tournaments_summary_local} (PIANO §7.B). Read-only
 * replica updated only by sync; no {@code @OneToMany}, no {@code @Version}
 * (mirror of {@code GameDefinitionLocalJpaEntity} /
 * {@code TournamentMatchLocalJpaEntity}). The {@code buildingIds} {@link List}
 * is serialised as a JSON {@link String} in a {@code TEXT} column (handled by
 * {@code TournamentSummaryLocalMapper}, like {@code registration_rules} on
 * {@code GameDefinitionLocalJpaEntity}). {@code deleted} defaults to
 * {@code false}; the sync service physically removes the row on a
 * {@code deleted=true} tombstone, so a stored row always has
 * {@code deleted=false}.
 */
@Entity
@Table(name = "tournaments_summary_local")
public class TournamentSummaryLocalJpaEntity {

    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;

    @Column(name = "team_based", nullable = false)
    private Boolean teamBased;

    @Column(name = "team_size", nullable = false)
    private Integer teamSize;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "building_ids", columnDefinition = "TEXT")
    private String buildingIdsJson;

    @Column(name = "participants_count", nullable = false)
    private Integer participantsCount;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public TournamentSummaryLocalJpaEntity() {
    }

    /**
     * Costruisce un nuovo riepilogo torneo locale con tutti i campi.
     *
     * @param tournamentId      identificativo del torneo
     * @param name              nome del torneo
     * @param gameType          tipo di gioco
     * @param teamBased         indica se il torneo è a squadre
     * @param teamSize          dimensione delle squadre
     * @param status            stato del torneo
     * @param startsAt          data/hora di inizio (può essere {@code null})
     * @param endsAt            data/hora di fine (può essere {@code null})
     * @param buildingIdsJson   JSON contenente gli ID degli edifici coinvolti
     * @param participantsCount numero di partecipanti
     * @param deleted           flag di eliminazione logica (sempre {@code false} in archivio)
     * @param updatedAt         istante dell'ultimo aggiornamento
     */
    public TournamentSummaryLocalJpaEntity(String tournamentId, String name, String gameType, Boolean teamBased,
                                           Integer teamSize, String status, Instant startsAt, Instant endsAt,
                                           String buildingIdsJson, Integer participantsCount, Boolean deleted,
                                           Instant updatedAt) {
        this.tournamentId = tournamentId;
        this.name = name;
        this.gameType = gameType;
        this.teamBased = teamBased;
        this.teamSize = teamSize;
        this.status = status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.buildingIdsJson = buildingIdsJson;
        this.participantsCount = participantsCount;
        this.deleted = deleted;
        this.updatedAt = updatedAt;
    }

    /**
     * Restituisce l'identificativo del torneo.
     *
     * @return tournamentId
     */
    public String getTournamentId() {
        return tournamentId;
    }

    /**
     * Imposta l'identificativo del torneo.
     *
     * @param tournamentId nuovo identificativo torneo
     */
    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    /**
     * Restituisce il nome del torneo.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Imposta il nome del torneo.
     *
     * @param name nuovo nome
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Restituisce il tipo di gioco del torneo.
     *
     * @return gameType
     */
    public String getGameType() {
        return gameType;
    }

    /**
     * Imposta il tipo di gioco del torneo.
     *
     * @param gameType nuovo tipo di gioco
     */
    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    /**
     * Indica se il torneo è a squadre.
     *
     * @return {@code true} se è a squadre
     */
    public Boolean getTeamBased() {
        return teamBased;
    }

    /**
     * Imposta se il torneo è a squadre.
     *
     * @param teamBased {@code true} per indicare torneo a squadre
     */
    public void setTeamBased(Boolean teamBased) {
        this.teamBased = teamBased;
    }

    /**
     * Restituisce la dimensione delle squadre.
     *
     * @return teamSize
     */
    public Integer getTeamSize() {
        return teamSize;
    }

    /**
     * Imposta la dimensione delle squadre.
     *
     * @param teamSize nuova dimensione squadre
     */
    public void setTeamSize(Integer teamSize) {
        this.teamSize = teamSize;
    }

    /**
     * Restituisce lo stato del torneo.
     *
     * @return status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Imposta lo stato del torneo.
     *
     * @param status nuovo stato
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Restituisce la data/hora di inizio del torneo.
     *
     * @return startsAt (può essere {@code null})
     */
    public Instant getStartsAt() {
        return startsAt;
    }

    /**
     * Imposta la data/hora di inizio del torneo.
     *
     * @param startsAt nuova data/hora inizio
     */
    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    /**
     * Restituisce la data/hora di fine del torneo.
     *
     * @return endsAt (può essere {@code null})
     */
    public Instant getEndsAt() {
        return endsAt;
    }

    /**
     * Imposta la data/hora di fine del torneo.
     *
     * @param endsAt nuova data/hora fine
     */
    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    /**
     * Restituisce il JSON contenente gli ID degli edifici coinvolti.
     *
     * @return buildingIdsJson (può essere {@code null})
     */
    public String getBuildingIdsJson() {
        return buildingIdsJson;
    }

    /**
     * Imposta il JSON contenente gli ID degli edifici coinvolti.
     *
     * @param buildingIdsJson nuovo JSON edifici
     */
    public void setBuildingIdsJson(String buildingIdsJson) {
        this.buildingIdsJson = buildingIdsJson;
    }

    /**
     * Restituisce il numero di partecipanti al torneo.
     *
     * @return participantsCount
     */
    public Integer getParticipantsCount() {
        return participantsCount;
    }

    /**
     * Imposta il numero di partecipanti.
     *
     * @param participantsCount nuovo numero partecipanti
     */
    public void setParticipantsCount(Integer participantsCount) {
        this.participantsCount = participantsCount;
    }

    /**
     * Indica se il torneo è stato eliminato logicamente.
     *
     * @return {@code true} se eliminato (sempre {@code false} in archivio)
     */
    public Boolean getDeleted() {
        return deleted;
    }

    /**
     * Imposta il flag di eliminazione logica.
     *
     * @param deleted {@code true} per marcare come eliminato
     */
    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * Restituisce l'istante dell'ultimo aggiornamento.
     *
     * @return updatedAt
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Imposta l'istante dell'ultimo aggiornamento.
     *
     * @param updatedAt nuovo istante di aggiornamento
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
