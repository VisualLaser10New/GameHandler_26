package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.dto.UserRegisteredEventDto;

/**
 * Caso d'uso che registra o aggiorna un utente a seguito di un evento di
 * sincronizzazione proveniente da un Local Server.
 */
public interface RegisterUserFromSyncUseCase {

    /**
     * Registra un utente a partire dall'evento di registrazione ricevuto dalla sincronizzazione.
     *
     * @param dto il DTO contenente i dati dell'utente da registrare; non deve essere {@code null}
     * @throws IllegalArgumentException se il DTO è {@code null} o privo dei dati obbligatori
     */
    void registerFromSync(UserRegisteredEventDto dto);
}
