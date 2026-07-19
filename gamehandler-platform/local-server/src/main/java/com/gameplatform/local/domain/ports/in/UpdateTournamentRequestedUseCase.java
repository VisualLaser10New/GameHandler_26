package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

import java.time.Instant;
import java.util.List;

/**
 * Use case per la richiesta di aggiornamento dei metadati di un torneo
 * da parte di un amministratore di piattaforma. Verifica l'invariante
 * DRAFT sul torneo (rifiuta immediatamente con stato {@code FAILED} se
 * il torneo non &egrave; in bozza), effettua il pre-controllo del ruolo
 * {@code PLATFORM_ADMIN}, quindi scrive in modo atomico una riga
 * {@code PENDING} su {@code admin_requests_local} e l'evento di outbox
 * corrispondente.
 *
 * @see com.gameplatform.shared.dto.AdminRequestDto
 */
public interface UpdateTournamentRequestedUseCase {

    /**
     * Avanza la richiesta di aggiornamento dei metadati del torneo specificato.
     *
     * @param tournamentId identificativo del torneo da aggiornare
     * @param name         nuovo nome del torneo
     * @param startsAt     nuova data e ora di inizio
     * @param buildingIds  nuovo elenco delle strutture coinvolte
     * @param actingUserId identificativo dell'amministratore richiedente
     * @param actingRole   ruolo con cui l'amministratore agisce
     * @param buildingId   identificativo della struttura di appartenenza
     * @return il DTO della richiesta amministrativa persistita con stato {@code PENDING}
     */
    AdminRequestDto update(String tournamentId,
                            String name,
                            Instant startsAt,
                            List<String> buildingIds,
                            String actingUserId,
                            String actingRole,
                            String buildingId);
}