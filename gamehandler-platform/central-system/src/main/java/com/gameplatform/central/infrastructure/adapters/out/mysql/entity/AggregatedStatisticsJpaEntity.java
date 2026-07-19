package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * Entità JPA per la tabella {@code aggregated_statistics} del database MySQL.
 *
 * <p>Rappresenta una aggregazione di statistiche di gioco per edificio, tipo di
 * gioco e periodo temporale. Ogni riga è identificata univocamente dalla coppia
 * di vincoli di unicità su {@code (building_id, game_type, period_start)}. I
 * conteggi sono sempre valorizzati (mai {@code null}) e le sessioni interrotte
 * hanno valore predefinito pari a 0 quando non fornite esplicitamente.</p>
 *
 * @see LocalAdminBuildingJpaEntity
 */
@Entity
@Table(name = "aggregated_statistics", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"building_id", "game_type", "period_start"})
})
public class AggregatedStatisticsJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "building_id", nullable = false, length = 50)
    private String buildingId;

    @Column(name = "game_type", nullable = false, length = 50)
    private String gameType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_sessions", nullable = false)
    private int totalSessions;

    @Column(name = "avg_duration_seconds", nullable = false)
    private int avgDurationSeconds;

    @Column(name = "total_reservations", nullable = false)
    private int totalReservations;

    @Column(name = "total_aborted_sessions", nullable = false)
    private int totalAbortedSessions;

    @Column(name = "data", columnDefinition = "TEXT")
    private String data;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public AggregatedStatisticsJpaEntity() {
    }

    /**
     * Costruisce un'aggregazione di statistiche inizializzando il numero di
     * sessioni interrotte a 0.
     *
     * @param id identificativo univoco dell'aggregazione; non deve essere {@code null}
     * @param buildingId identificativo dell'edificio di riferimento; non deve essere {@code null}
     * @param gameType tipo di gioco associato all'aggregazione; non deve essere {@code null}
     * @param periodStart data di inizio del periodo coperto; non deve essere {@code null}
     * @param periodEnd data di fine del periodo coperto; non deve essere {@code null}
     * @param totalSessions numero totale di sessioni nel periodo; non negativo
     * @param avgDurationSeconds durata media delle sessioni in secondi; non negativo
     * @param totalReservations numero totale di prenotazioni nel periodo; non negativo
     * @param data payload aggiuntivo in formato testuale; può essere {@code null}
     */
    public AggregatedStatisticsJpaEntity(String id, String buildingId, String gameType, LocalDate periodStart, LocalDate periodEnd, int totalSessions, int avgDurationSeconds, int totalReservations, String data) {
        this(id, buildingId, gameType, periodStart, periodEnd, totalSessions, avgDurationSeconds, totalReservations, 0, data);
    }

    /**
     * Costruisce un'aggregazione di statistiche con tutti i campi valorizzati.
     *
     * @param id identificativo univoco dell'aggregazione; non deve essere {@code null}
     * @param buildingId identificativo dell'edificio di riferimento; non deve essere {@code null}
     * @param gameType tipo di gioco associato all'aggregazione; non deve essere {@code null}
     * @param periodStart data di inizio del periodo coperto; non deve essere {@code null}
     * @param periodEnd data di fine del periodo coperto; non deve essere {@code null}
     * @param totalSessions numero totale di sessioni nel periodo; non negativo
     * @param avgDurationSeconds durata media delle sessioni in secondi; non negativo
     * @param totalReservations numero totale di prenotazioni nel periodo; non negativo
     * @param totalAbortedSessions numero totale di sessioni interrotte nel periodo; non negativo
     * @param data payload aggiuntivo in formato testuale; può essere {@code null}
     */
    public AggregatedStatisticsJpaEntity(String id, String buildingId, String gameType, LocalDate periodStart, LocalDate periodEnd, int totalSessions, int avgDurationSeconds, int totalReservations, int totalAbortedSessions, String data) {
        this.id = id;
        this.buildingId = buildingId;
        this.gameType = gameType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalSessions = totalSessions;
        this.avgDurationSeconds = avgDurationSeconds;
        this.totalReservations = totalReservations;
        this.totalAbortedSessions = totalAbortedSessions;
        this.data = data;
    }

    /**
     * Restituisce l'identificativo univoco dell'aggregazione.
     *
     * @return l'identificativo dell'aggregazione; può essere {@code null} se l'entità
     *         non è ancora stata persistita
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificativo univoco dell'aggregazione.
     *
     * @param id nuovo identificativo dell'aggregazione; può essere {@code null}
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Restituisce l'identificativo dell'edificio a cui si riferisce l'aggregazione.
     *
     * @return l'identificativo dell'edificio; non deve essere {@code null}
     */
    public String getBuildingId() {
        return buildingId;
    }

    /**
     * Imposta l'identificativo dell'edificio a cui si riferisce l'aggregazione.
     *
     * @param buildingId nuovo identificativo dell'edificio; non deve essere {@code null}
     */
    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    /**
     * Restituisce il tipo di gioco associato all'aggregazione.
     *
     * @return il tipo di gioco; non deve essere {@code null}
     */
    public String getGameType() {
        return gameType;
    }

    /**
     * Imposta il tipo di gioco associato all'aggregazione.
     *
     * @param gameType nuovo tipo di gioco; non deve essere {@code null}
     */
    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    /**
     * Restituisce la data di inizio del periodo coperto dall'aggregazione.
     *
     * @return la data di inizio del periodo; non deve essere {@code null}
     */
    public LocalDate getPeriodStart() {
        return periodStart;
    }

    /**
     * Imposta la data di inizio del periodo coperto dall'aggregazione.
     *
     * @param periodStart nuova data di inizio del periodo; non deve essere {@code null}
     */
    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    /**
     * Restituisce la data di fine del periodo coperto dall'aggregazione.
     *
     * @return la data di fine del periodo; non deve essere {@code null}
     */
    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    /**
     * Imposta la data di fine del periodo coperto dall'aggregazione.
     *
     * @param periodEnd nuova data di fine del periodo; non deve essere {@code null}
     */
    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    /**
     * Restituisce il numero totale di sessioni registrate nel periodo.
     *
     * @return il numero totale di sessioni; non negativo
     */
    public int getTotalSessions() {
        return totalSessions;
    }

    /**
     * Imposta il numero totale di sessioni registrate nel periodo.
     *
     * @param totalSessions nuovo numero totale di sessioni; non negativo
     */
    public void setTotalSessions(int totalSessions) {
        this.totalSessions = totalSessions;
    }

    /**
     * Restituisce la durata media delle sessioni espressa in secondi.
     *
     * @return la durata media in secondi; non negativo
     */
    public int getAvgDurationSeconds() {
        return avgDurationSeconds;
    }

    /**
     * Imposta la durata media delle sessioni espressa in secondi.
     *
     * @param avgDurationSeconds nuova durata media in secondi; non negativo
     */
    public void setAvgDurationSeconds(int avgDurationSeconds) {
        this.avgDurationSeconds = avgDurationSeconds;
    }

    /**
     * Restituisce il numero totale di prenotazioni registrate nel periodo.
     *
     * @return il numero totale di prenotazioni; non negativo
     */
    public int getTotalReservations() {
        return totalReservations;
    }

    /**
     * Imposta il numero totale di prenotazioni registrate nel periodo.
     *
     * @param totalReservations nuovo numero totale di prenotazioni; non negativo
     */
    public void setTotalReservations(int totalReservations) {
        this.totalReservations = totalReservations;
    }

    /**
     * Restituisce il numero totale di sessioni interrotte nel periodo.
     *
     * @return il numero totale di sessioni interrotte; non negativo
     */
    public int getTotalAbortedSessions() {
        return totalAbortedSessions;
    }

    /**
     * Imposta il numero totale di sessioni interrotte nel periodo.
     *
     * @param totalAbortedSessions nuovo numero totale di sessioni interrotte; non negativo
     */
    public void setTotalAbortedSessions(int totalAbortedSessions) {
        this.totalAbortedSessions = totalAbortedSessions;
    }

    /**
     * Restituisce il payload di dati aggiuntivi dell'aggregazione.
     *
     * @return i dati aggiuntivi in formato testuale; può essere {@code null}
     */
    public String getData() {
        return data;
    }

    /**
     * Imposta il payload di dati aggiuntivi dell'aggregazione.
     *
     * @param data nuovi dati aggiuntivi in formato testuale; può essere {@code null}
     */
    public void setData(String data) {
        this.data = data;
    }
}
