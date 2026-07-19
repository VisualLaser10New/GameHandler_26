package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.GameDefinitionEventDto;
import java.util.List;

/**
 * Porta di uscita per l'invio di un batch di eventi di metadati
 * {@code GAME_DEFINITION_UPSERTED} a un singolo server locale attivo.
 *
 * <p>L'operazione è idempotente lato ricevente per chiave primaria
 * {@code (game_type)}: non è richiesto alcun contratto di ack o di isolamento
 * dei messaggi avvelenati.</p>
 *
 * @throws com.gameplatform.central.domain.exception.TransientPushException
 *         in caso di fallimento transitorio di trasporto (il chiamante ritenta tramite l'outbox)
 * @see GameDefinitionEventDto
 * @see RegisteredLocalServer
 */
public interface PushGameDefinitionToLocalServersPort {

    /**
     * Invia un batch di definizioni di gioco al server locale indicato.
     *
     * @param events il batch di eventi di definizione da inviare; non deve essere {@code null}
     * @param server il server locale attivo di destinazione; non deve essere {@code null}
     * @throws IllegalArgumentException in caso di parametri {@code null}
     * @throws com.gameplatform.central.domain.exception.TransientPushException
     *         in caso di fallimento transitorio di trasporto
     */
    void pushGameDefinitions(List<GameDefinitionEventDto> events, RegisteredLocalServer server);
}
