package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

/**
 * Use case per la richiesta di eliminazione di un torneo da parte
 * di un amministratore di piattaforma. Verifica l'invariante DRAFT
 * sul torneo (rifiuta immediatamente con stato {@code FAILED} se il
 * torneo non &egrave; in stato {@code DRAFT}), effettua il pre-controllo
 * del ruolo {@code PLATFORM_ADMIN}, quindi scrive in modo atomico una
 * riga {@code PENDING} su {@code admin_requests_local} e l'evento di
 * outbox corrispondente.
 *
 * @see com.gameplatform.shared.dto.AdminRequestDto
 */
public interface DeleteTournamentRequestedUseCase {

    /**
     * Avanza la richiesta di eliminazione del torneo specificato.
     *
     * @param tournamentId identificativo del torneo da eliminare
     * @param actingUserId identificativo dell'amministratore richiedente
     * @param actingRole   ruolo con cui l'amministratore agisce
     * @param buildingId   identificativo della struttura di appartenenza
     * @return il DTO della richiesta amministrativa persistita con stato {@code PENDING}
     */
    AdminRequestDto delete(String tournamentId,
                            String actingUserId,
                            String actingRole,
                            String buildingId);
}