package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentMatchDto;

import java.util.List;

/**
 * Generates the round-1 single-elimination bracket for an
 * {@code OPEN_REGISTRATION} tournament and atomically emits one
 * outbox event per {@code SCHEDULED} match ({@code TOURNAMENT_MATCH_SCHEDULED}).
 *
 * <p>The implementing service ({@code TournamentBracketService}) is responsible
 * for the atomicity contract: the tournament status transition, every match
 * save, every outbox write and the standings seed MUST occur within the
 * same {@code @Transactional} method (Outbox Pattern — see
 * {@code LocalAdminBuildingService.writeOutboxEvent}).</p>
 *
 * <p>Idempotency-by-rejection: a second call on an already-{@code IN_PROGRESS}
 * tournament throws {@code InvalidTournamentStateException} (mapped to 400)
 * because {@code Tournament.startProgress()} only allows the
 * {@code OPEN_REGISTRATION -> IN_PROGRESS} transition. No explicit
 * idempotency-key check is needed.</p>
 */
public interface ScheduleTournamentMatchesUseCase {

    /**
     * Genera il tabellone a eliminazione diretta del primo turno per un torneo in registrazione aperta.
     *
     * @param tournamentId l'identificativo del torneo da schedulare; non deve essere {@code null}
     * @return la lista di {@link TournamentMatchDto} rappresentante gli incontri schedulati
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @throws com.gameplatform.shared.domain.exception.InvalidTournamentStateException se il torneo non è in stato {@code OPEN_REGISTRATION} o è già in corso
     */
    List<TournamentMatchDto> schedule(TournamentId tournamentId);
}
