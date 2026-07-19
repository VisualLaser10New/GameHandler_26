package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import java.util.List;

/**
 * Porta di uscita per l'invio di un batch di utenti a un server locale e la
 * ricezione dell'esito di ciascun utente.
 *
 * <p>Espone il contratto di sincronizzazione utente con il server locale,
 * restituendo per ogni utente in ingresso un esito che ne descrive l'applicazione,
 * lo scarto come obsoleto o il rifiuto come messaggio avvelenato.</p>
 *
 * @see UserSyncDto
 * @see UserSyncAckDto
 * @see RegisteredLocalServer
 */
public interface PushUserToLocalServersPort {

    /**
     * Invia un batch di utenti al server locale indicato e restituisce un
     * {@link UserSyncAckDto} per ciascun utente in ingresso, nello stesso ordine,
     * descrivendo se ciascuno è stato applicato, scartato come obsoleto o rifiutato
     * come avvelenato.
     *
     * @param users  il batch di utenti da inviare; non deve essere {@code null}
     * @param server il server locale di destinazione; non deve essere {@code null}
     * @return la lista degli esiti, uno per ogni utente in ingresso e nel medesimo ordine; mai {@code null}
     * @throws IllegalArgumentException in caso di parametri {@code null}
     * @throws com.gameplatform.central.domain.exception.TransientPushException
     *         in caso di fallimento transitorio di trasporto
     */
    List<UserSyncAckDto> pushUsers(List<UserSyncDto> users, RegisteredLocalServer server);
}
