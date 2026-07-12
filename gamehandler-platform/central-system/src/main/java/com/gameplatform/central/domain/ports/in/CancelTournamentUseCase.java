package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentDto;

public interface CancelTournamentUseCase {
    TournamentDto cancel(TournamentId tournamentId);
}
