package com.gameplatform.shared.dto;

import java.util.List;

/**
 * Record che rappresenta il risultato di una sessione di gioco, aggregando i dati
 * della sessione e l'elenco dei partecipanti coinvolti. Viene utilizzato per il
 * trasferimento dei dati tra i componenti della piattaforma.
 *
 * @see GameSessionDto
 */
public record GameSessionResultDto(
    /**
     * La sessione di gioco di riferimento. Non deve essere {@code null}.
     *
     * @return la sessione di gioco associata al risultato
     */
    GameSessionDto session,

    /**
     * L'elenco dei nomi dei partecipanti alla sessione. Non deve essere {@code null};
     * può essere una lista vuota se nessun partecipante ha preso parte alla sessione.
     *
     * @return la lista dei partecipanti; mai {@code null}, eventualmente vuota
     */
    List<String> participants
) {}
