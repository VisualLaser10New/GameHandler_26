package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import java.util.List;

/**
 * Use case per la sincronizzazione batch degli utenti con il sistema centrale.
 * Elabora un lotto di utenti e restituisce un acknowledgement per ciascuno,
 * senza interrompere l'elaborazione in caso di utenti non validi.
 */
public interface SyncUsersUseCase {
    /**
     * Sincronizza un lotto di utenti e restituisce gli acknowledgement corrispondenti.
     * Un utente non valido non interrompe l'elaborazione dell'intero lotto.
     *
     * @param users elenco dei DTO utente da sincronizzare
     * @return lista degli acknowledgement di sincronizzazione, uno per ogni utente in input
     */
    List<UserSyncAckDto> syncUsers(List<UserSyncDto> users);
}
