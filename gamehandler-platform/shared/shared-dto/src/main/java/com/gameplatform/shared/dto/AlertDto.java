package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Record che rappresenta un avviso generato all'interno della piattaforma di gioco.
 *
 * <p>Contiene le informazioni essenziali per identificare e descrivere un evento
 * di allerta associato a un edificio e a un gioco, includendone il tipo, il
 * messaggio descrittivo e l'istante di emissione.</p>
 *
 * @see com.gameplatform.shared.dto.AlertType
 */
public record AlertDto(
    /**
     * Identificativo dell'edificio a cui l'avviso fa riferimento.
     *
     * @return l'identificativo dell'edificio, mai {@code null}
     */
    String buildingId,

    /**
     * Identificativo del gioco associato all'avviso.
     *
     * @return l'identificativo del gioco, mai {@code null}
     */
    String gameId,

    /**
     * Tipologia dell'avviso che classifica l'evento segnalato.
     *
     * @return il tipo di avviso, mai {@code null}
     */
    String alertType,

    /**
     * Messaggio descrittivo del contenuto dell'avviso.
     *
     * @return il messaggio dell'avviso, mai {@code null} e non vuoto
     */
    String message,

    /**
     * Istante in cui l'avviso è stato generato.
     *
     * @return il timestamp dell'avviso, mai {@code null}
     */
    Instant timestamp
) {}
