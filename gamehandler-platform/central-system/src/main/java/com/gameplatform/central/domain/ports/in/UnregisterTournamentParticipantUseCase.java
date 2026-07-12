package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;

public interface UnregisterTournamentParticipantUseCase {
    void unregister(TournamentId tournamentId, UserId currentUserId);
}
