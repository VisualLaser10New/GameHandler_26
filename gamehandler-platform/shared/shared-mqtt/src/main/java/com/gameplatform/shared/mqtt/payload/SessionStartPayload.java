package com.gameplatform.shared.mqtt.payload;

import java.util.List;
import com.gameplatform.shared.domain.model.GameType;

/**
 * Record che rappresenta il payload trasmesso via MQTT per segnalare l'avvio di una sessione di gioco.
 * Contiene l'identificativo della sessione, il tipo di gioco e l'elenco dei partecipanti coinvolti.
 *
 * @see com.gameplatform.shared.domain.model.GameType
 */
public record SessionStartPayload(
    /**
     * Restituisce l'identificativo univoco della sessione di gioco.
     *
     * @return l'identificativo della sessione; non è {@code null} e non è vuoto
     */
    String sessionId,

    /**
     * Restituisce il tipo di gioco associato alla sessione.
     *
     * @return il tipo di gioco; non è {@code null}
     */
    GameType gameType,

    /**
     * Restituisce l'elenco degli identificativi dei partecipanti alla sessione.
     *
     * @return la lista dei partecipanti; non è {@code null} e può essere vuota se nessun partecipante è ancora registrato
     */
    List<String> participants
) {}
