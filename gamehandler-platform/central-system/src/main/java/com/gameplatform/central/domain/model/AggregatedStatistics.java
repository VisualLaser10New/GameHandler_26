package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

/**
 * Entità di dominio che rappresenta le statistiche aggregate di un edificio per
 * un determinato tipo di gioco e un intervallo temporale. Raccoglie i totali di
 * sessioni, prenotazioni, sessioni interrotte e la durata media, oltre a un
 * insieme di dati aggiuntivi estensibili, e supporta la fusione di più aggregati
 * relativi allo stesso edificio e tipo di gioco.
 *
 * @see BuildingId
 * @see GameType
 */
public class AggregatedStatistics {
    private String id;
    private BuildingId buildingId;
    private GameType gameType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private int totalSessions;
    private int avgDurationSeconds;
    private int totalReservations;
    private int totalAbortedSessions;
    private Map<String, Object> data;

    /**
     * Costruisce un aggregato di statistiche con numero di sessioni interrotte
     * pari a zero.
     *
     * @param id identificativo univoco dell'aggregato; non può essere {@code null} né vuoto
     * @param buildingId identificativo dell'edificio a cui si riferiscono le statistiche; non può essere {@code null}
     * @param gameType tipo di gioco a cui si riferiscono le statistiche; non può essere {@code null}
     * @param periodStart data di inizio del periodo di aggregazione; non può essere {@code null} né successiva a {@code periodEnd}
     * @param periodEnd data di fine del periodo di aggregazione; non può essere {@code null}
     * @param totalSessions numero totale di sessioni; non può essere negativo
     * @param avgDurationSeconds durata media delle sessioni in secondi; non può essere negativa
     * @param totalReservations numero totale di prenotazioni; non può essere negativo
     * @param data mappa di dati aggiuntivi; se {@code null} viene inizializzata come mappa vuota
     * @throws IllegalArgumentException se uno dei vincoli sui parametri non è rispettato
     * @see #AggregatedStatistics(String, BuildingId, GameType, LocalDate, LocalDate, int, int, int, int, Map)
     */
    public AggregatedStatistics(String id, BuildingId buildingId, GameType gameType, LocalDate periodStart, LocalDate periodEnd, int totalSessions, int avgDurationSeconds, int totalReservations, Map<String, Object> data) {
        this(id, buildingId, gameType, periodStart, periodEnd, totalSessions, avgDurationSeconds, totalReservations, 0, data);
    }

    /**
     * Costruisce un aggregato di statistiche con tutti i valori specificati.
     *
     * @param id identificativo univoco dell'aggregato; non può essere {@code null} né vuoto
     * @param buildingId identificativo dell'edificio a cui si riferiscono le statistiche; non può essere {@code null}
     * @param gameType tipo di gioco a cui si riferiscono le statistiche; non può essere {@code null}
     * @param periodStart data di inizio del periodo di aggregazione; non può essere {@code null} né successiva a {@code periodEnd}
     * @param periodEnd data di fine del periodo di aggregazione; non può essere {@code null}
     * @param totalSessions numero totale di sessioni; non può essere negativo
     * @param avgDurationSeconds durata media delle sessioni in secondi; non può essere negativa
     * @param totalReservations numero totale di prenotazioni; non può essere negativo
     * @param totalAbortedSessions numero totale di sessioni interrotte; non può essere negativo
     * @param data mappa di dati aggiuntivi; se {@code null} viene inizializzata come mappa vuota
     * @throws IllegalArgumentException se uno dei vincoli sui parametri non è rispettato
     */
    public AggregatedStatistics(String id, BuildingId buildingId, GameType gameType, LocalDate periodStart, LocalDate periodEnd, int totalSessions, int avgDurationSeconds, int totalReservations, int totalAbortedSessions, Map<String, Object> data) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Id cannot be null or empty");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("Period bounds cannot be null");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart cannot be after periodEnd");
        }
        if (totalSessions < 0) {
            throw new IllegalArgumentException("totalSessions cannot be negative");
        }
        if (avgDurationSeconds < 0) {
            throw new IllegalArgumentException("avgDurationSeconds cannot be negative");
        }
        if (totalReservations < 0) {
            throw new IllegalArgumentException("totalReservations cannot be negative");
        }
        if (totalAbortedSessions < 0) {
            throw new IllegalArgumentException("totalAbortedSessions cannot be negative");
        }
        this.id = id;
        this.buildingId = buildingId;
        this.gameType = gameType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalSessions = totalSessions;
        this.avgDurationSeconds = avgDurationSeconds;
        this.totalReservations = totalReservations;
        this.totalAbortedSessions = totalAbortedSessions;
        this.data = data != null ? new java.util.HashMap<>(data) : new java.util.HashMap<>();
    }

    /**
     * Fonde questo aggregato con un altro relativo allo stesso edificio e tipo
     * di gioco, sommando i totali, ricalcolando la durata media ponderata sul
     * numero di sessioni, estendendo il periodo per includere entrambi gli
     * intervalli e unendo ricorsivamente le mappe di dati aggiuntivi.
     *
     * @param other aggregato da fondere con quello corrente; non può essere {@code null}
     * @throws IllegalArgumentException se {@code other} è {@code null}, se appartiene a un edificio o a un tipo di gioco differente, oppure se il suo periodo non è valido
     */
    public void mergeWith(AggregatedStatistics other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot merge with null statistics");
        }

        if (!java.util.Objects.equals(this.buildingId, other.buildingId)) {
            throw new IllegalArgumentException("Cannot merge statistics belonging to different buildings");
        }
        if (this.gameType != other.gameType) {
            throw new IllegalArgumentException("Cannot merge statistics belonging to different game types");
        }
        if (other.periodStart == null || other.periodEnd == null) {
            throw new IllegalArgumentException("Other statistics period bounds cannot be null");
        }
        if (other.periodStart.isAfter(other.periodEnd)) {
            throw new IllegalArgumentException("Other statistics periodStart cannot be after periodEnd");
        }

        int combinedTotalSessions = this.totalSessions + other.totalSessions;
        if (combinedTotalSessions > 0) {
            long totalDurationThis = (long) this.avgDurationSeconds * this.totalSessions;
            long totalDurationOther = (long) other.avgDurationSeconds * other.totalSessions;
            this.avgDurationSeconds = (int) Math.round((double) (totalDurationThis + totalDurationOther) / combinedTotalSessions);
        } else {
            this.avgDurationSeconds = 0;
        }

        this.totalSessions = combinedTotalSessions;
        this.totalReservations += other.totalReservations;
        this.totalAbortedSessions += other.totalAbortedSessions;

        if (this.periodStart == null || other.periodStart.isBefore(this.periodStart)) {
            this.periodStart = other.periodStart;
        }
        if (this.periodEnd == null || other.periodEnd.isAfter(this.periodEnd)) {
            this.periodEnd = other.periodEnd;
        }

        this.data = mergeDataMaps(this.data, other.data);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeDataMaps(Map<String, Object> map1, Map<String, Object> map2) {
        if (map1 == null && map2 == null) {
            return new HashMap<>();
        }
        if (map1 == null) {
            return new HashMap<>(map2);
        }
        if (map2 == null) {
            return new HashMap<>(map1);
        }

        Map<String, Object> merged = new HashMap<>(map1);
        for (Map.Entry<String, Object> entry : map2.entrySet()) {
            String key = entry.getKey();
            Object val2 = entry.getValue();
            if (!merged.containsKey(key)) {
                merged.put(key, val2);
            } else {
                Object val1 = merged.get(key);
                if (val1 instanceof Map && val2 instanceof Map) {
                    merged.put(key, mergeDataMaps((Map<String, Object>) val1, (Map<String, Object>) val2));
                } else if (val1 instanceof Number && val2 instanceof Number) {
                    merged.put(key, sumNumbers((Number) val1, (Number) val2));
                } else {
                    merged.put(key, val2); // Fallback to overwrite
                }
            }
        }
        return merged;
    }

    private Number sumNumbers(Number n1, Number n2) {
        if (n1 instanceof java.math.BigDecimal || n2 instanceof java.math.BigDecimal) {
            java.math.BigDecimal bd1 = toBigDecimal(n1);
            java.math.BigDecimal bd2 = toBigDecimal(n2);
            return bd1.add(bd2);
        }
        if (n1 instanceof Double || n2 instanceof Double || n1 instanceof Float || n2 instanceof Float) {
            return n1.doubleValue() + n2.doubleValue();
        } else {
            long sum = n1.longValue() + n2.longValue();
            if (sum >= Integer.MIN_VALUE && sum <= Integer.MAX_VALUE) {
                return (int) sum;
            }
            return sum;
        }
    }

    private java.math.BigDecimal toBigDecimal(Number number) {
        if (number instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) number;
        }
        if (number instanceof java.math.BigInteger) {
            return new java.math.BigDecimal((java.math.BigInteger) number);
        }
        if (number instanceof Double || number instanceof Float) {
            return java.math.BigDecimal.valueOf(number.doubleValue());
        }
        return java.math.BigDecimal.valueOf(number.longValue());
    }

    /**
     * Restituisce l'identificativo univoco dell'aggregato.
     *
     * @return l'identificativo dell'aggregato, mai {@code null}
     */
    public String getId() {
        return id;
    }
    /**
     * Restituisce l'identificativo dell'edificio a cui si riferiscono le statistiche.
     *
     * @return l'identificativo dell'edificio, mai {@code null}
     */
    public BuildingId getBuildingId() {
        return buildingId;
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
     * Restituisce la data di inizio del periodo di aggregazione.
     *
     * @return la data di inizio del periodo, mai {@code null}
     */
    public LocalDate getPeriodStart() {
        return periodStart;
    }
    /**
     * Restituisce la data di fine del periodo di aggregazione.
     *
     * @return la data di fine del periodo, mai {@code null}
     */
    public LocalDate getPeriodEnd() {
        return periodEnd;
    }
    /**
     * Restituisce il numero totale di sessioni aggregate.
     *
     * @return il numero totale di sessioni, sempre maggiore o uguale a zero
     */
    public int getTotalSessions() {
        return totalSessions;
    }
    /**
     * Restituisce la durata media delle sessioni espressa in secondi.
     *
     * @return la durata media in secondi, sempre maggiore o uguale a zero
     */
    public int getAvgDurationSeconds() {
        return avgDurationSeconds;
    }
    /**
     * Restituisce il numero totale di prenotazioni aggregate.
     *
     * @return il numero totale di prenotazioni, sempre maggiore o uguale a zero
     */
    public int getTotalReservations() {
        return totalReservations;
    }
    /**
     * Restituisce il numero totale di sessioni interrotte.
     *
     * @return il numero totale di sessioni interrotte, sempre maggiore o uguale a zero
     */
    public int getTotalAbortedSessions() {
        return totalAbortedSessions;
    }
    /**
     * Restituisce i dati aggiuntivi dell'aggregato come mappa non modificabile.
     *
     * @return una vista non modificabile della mappa dei dati aggiuntivi, mai {@code null}
     */
    public Map<String, Object> getData() {
        return java.util.Collections.unmodifiableMap(data);
    }
}

