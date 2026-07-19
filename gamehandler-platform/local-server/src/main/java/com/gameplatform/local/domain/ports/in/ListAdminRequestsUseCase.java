package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

import java.util.List;
import java.util.Optional;

/**
 * Use case per la lettura delle richieste amministrative. Restituisce
 * le righe di richiesta amministrativa appartenenti all'utente agente
 * specificato, oppure una singola richiesta per ID.
 *
 * @see com.gameplatform.shared.dto.AdminRequestDto
 */
public interface ListAdminRequestsUseCase {

    /**
     * Restituisce l'elenco delle richieste amministrative per l'utente agente specificato.
     *
     * @param actingUserId identificativo dell'utente agente
     * @return lista dei DTO delle richieste amministrative
     */
    List<AdminRequestDto> listByActingUser(String actingUserId);

    /**
     * Cerca una richiesta amministrativa per ID.
     *
     * @param requestId identificativo della richiesta
     * @return un {@code Optional} contenente il DTO della richiesta, oppure vuoto se non trovata
     */
    Optional<AdminRequestDto> findByRequestId(String requestId);
}