package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentDto;

public interface OpenTournamentRegistrationUseCase {
    TournamentDto open(TournamentId tournamentId, String originatingRequestId);

    default TournamentDto open(TournamentId tournamentId) {
        return open(tournamentId, null);
    }
}