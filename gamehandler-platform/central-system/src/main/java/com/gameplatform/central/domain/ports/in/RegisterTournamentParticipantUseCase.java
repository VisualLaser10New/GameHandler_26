package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.TournamentParticipantDto;
import java.util.List;

public interface RegisterTournamentParticipantUseCase {
    TournamentParticipantDto register(TournamentId tournamentId, UserId captainId, String teamName, List<String> teamMemberIds);
}
