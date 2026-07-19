package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Rappresenta un aggregato di statistiche di utilizzo relativo a un edificio e a
 * una tipologia di gioco in un determinato intervallo temporale.
 * Raccoglie i valori riepilogativi su sessioni, prenotazioni e durata medie
 * calcolati per il periodo indicato, utilizzabili per reportistica e monitoraggio.
 *
 * @param buildingId identificativo dell'edificio a cui le statistiche si riferiscono;
 *                   {@code null} se le statistiche non sono associate a uno specifico edificio
 * @param gameType tipologia di gioco a cui le statistiche si riferiscono;
 *                 {@code null} se le statistiche sono aggregate su tutte le tipologie
 * @param periodStart istante iniziale (incluso) dell'intervallo temporale considerato
 * @param periodEnd istante finale (escluso) dell'intervallo temporale considerato
 * @param totalSessions numero totale di sessioni di gioco avviate nel periodo;
 *                      {@code 0} se non ne è stata registrata alcuna
 * @param avgDuration durata media delle sessioni nel periodo, espressa in secondi;
 *                    {@code 0} in assenza di sessioni valutabili
 * @param totalReservations numero totale di prenotazioni effettuate nel periodo;
 *                          {@code 0} se non ne è stata registrata alcuna
 * @param data contenuto aggiuntivo o dettaglio delle statistiche in forma testuale
 *             (ad esempio una rappresentazione serializzata); {@code null} se non presente
 * @param totalAbortedSessions numero totale di sessioni interrotte o annullate nel periodo;
 *                             {@code 0} se non ne è stata registrata alcuna
 */
public record StatisticsDto(
    String buildingId,
    String gameType,
    Instant periodStart,
    Instant periodEnd,
    Integer totalSessions,
    Integer avgDuration,
    Integer totalReservations,
    String data,
    Integer totalAbortedSessions
) {}
