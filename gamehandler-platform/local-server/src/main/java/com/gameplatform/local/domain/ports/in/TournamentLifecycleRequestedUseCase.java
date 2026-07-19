package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

/**
 * Use case parametrico per le operazioni sul ciclo di vita di un torneo.
 * Un amministratore di piattaforma pu&ograve; aprire le registrazioni,
 * cancellare o programmare le partite di un torneo. L'azione &egrave;
 * discriminata dal tipo di evento specificato. Effettua il pre-controllo
 * del ruolo {@code PLATFORM_ADMIN}, quindi scrive in modo atomico una
 * riga {@code PENDING} su {@code admin_requests_local} e l'evento di
 * outbox corrispondente.
 *
 * @see com.gameplatform.shared.dto.AdminRequestDto
 */
public interface TournamentLifecycleRequestedUseCase {

    /**
     * Avanza una richiesta di azione sul ciclo di vita del torneo specificato.
     *
     * @param eventType   tipo di evento del ciclo di vita (apertura, cancellazione, programmazione)
     * @param tournamentId identificativo del torneo
     * @param actingUserId identificativo dell'amministratore richiedente
     * @param actingRole   ruolo con cui l'amministratore agisce
     * @param buildingId   identificativo della struttura di appartenenza
     * @return il DTO della richiesta amministrativa persistita con stato {@code PENDING}
     */
    AdminRequestDto lifecycle(String eventType,
                               String tournamentId,
                               String actingUserId,
                               String actingRole,
                               String buildingId);
}