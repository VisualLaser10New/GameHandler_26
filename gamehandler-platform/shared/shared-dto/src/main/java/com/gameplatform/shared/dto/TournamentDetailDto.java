package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Proiezione in sola lettura di un singolo torneo con il relativo dettaglio
 * completo (riepilogo, classifica, incontri e partecipanti) esposta
 * dall'endpoint Locale {@code GET /api/tournaments/{id}} (PIANO §7.B).
 * Aggrega le quattro repliche locali in un unico payload di risposta.
 *
 * @param summary       riepilogo del torneo; non deve essere {@code null}
 * @param standings     righe della classifica; lista non {@code null},
 *                      possibilmente vuota se il torneo non ha ancora
 *                      assegnato punteggi
 * @param matches       incontri locali del torneo; lista non {@code null},
 *                      possibilmente vuota se non sono ancora stati
 *                      generati incontri
 * @param participants  partecipanti registrati al torneo; lista non
 *                      {@code null}, possibilmente vuota se non vi sono
 *                      iscrizioni
 *
 * @see TournamentSummaryDto
 * @see TournamentStandingDto
 * @see TournamentMatchDto
 * @see TournamentParticipantViewDto
 */
public record TournamentDetailDto(
        TournamentSummaryDto summary,
        List<TournamentStandingDto> standings,
        List<TournamentMatchDto> matches,
        List<TournamentParticipantViewDto> participants
) {
}
