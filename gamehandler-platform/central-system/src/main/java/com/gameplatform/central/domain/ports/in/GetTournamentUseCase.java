package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentDto;
import java.util.Optional;

public interface GetTournamentUseCase {
    Optional<TournamentDto> getById(TournamentId tournamentId);
}
