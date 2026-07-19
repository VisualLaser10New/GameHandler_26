package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

import java.util.List;

/**
 * Use case che gestisce la richiesta di assegnazione dei ruoli a un utente target
 * da parte di un amministratore di piattaforma. Sostituisce il set di ruoli esistente
 * con quelli forniti, effettua il pre-controllo del ruolo {@code PLATFORM_ADMIN}
 * sugli utenti replicati, quindi scrive in modo atomico una riga {@code PENDING}
 * su {@code admin_requests_local} e l'evento di outbox corrispondente.
 *
 * @see com.gameplatform.shared.dto.AdminRequestDto
 */
public interface AssignRoleRequestedUseCase {

    /**
     * Assegna i ruoli specificati all'utente target, sostituendo quelli esistenti.
     *
     * @param targetUserId identificativo dell'utente a cui assegnare i ruoli
     * @param roles        elenco dei ruoli da assegnare
     * @param actingUserId identificativo dell'amministratore che richiede l'operazione
     * @param actingRole   ruolo con cui l'amministratore agisce
     * @param buildingId   identificativo della struttura di appartenenza
     * @return il DTO della richiesta amministrativa persistita con stato {@code PENDING}
     */
    AdminRequestDto assign(String targetUserId,
                            List<String> roles,
                            String actingUserId,
                            String actingRole,
                            String buildingId);
}