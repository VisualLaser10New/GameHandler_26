package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentDto;
import java.util.Optional;

/**
 * Caso d'uso di lettura che recupera i dettagli di un singolo torneo
 * a partire dal suo identificativo.
 */
public interface GetTournamentUseCase {

    /**
     * Restituisce il torneo identificato dal relativo identificativo.
     *
     * @param tournamentId l'identificativo del torneo da recuperare; non deve essere {@code null}
     * @return un {@link Optional} contenente il {@link TournamentDto} se il torneo esiste, altrimenti un {@link Optional} vuoto
     */
    Optional<TournamentDto> getById(TournamentId tournamentId);
}
