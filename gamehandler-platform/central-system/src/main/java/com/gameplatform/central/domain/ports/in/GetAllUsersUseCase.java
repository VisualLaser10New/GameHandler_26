package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.dto.UserSyncDto;
import java.util.List;

/**
 * Caso d'uso di lettura che restituisce l'elenco completo degli utenti
 * del sistema centrale, finalizzato alla sincronizzazione con i Local Server.
 */
public interface GetAllUsersUseCase {

    /**
     * Restituisce tutti gli utenti del sistema centrale in formato adatto alla sincronizzazione.
     *
     * @return la lista di {@link UserSyncDto} rappresentante gli utenti; la lista è vuota se non esiste alcun utente
     */
    List<UserSyncDto> getAllUsersForSync();
}

