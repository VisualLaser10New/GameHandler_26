package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentParticipantDto;
import java.util.List;

public interface ListTournamentParticipantsUseCase {
    List<TournamentParticipantDto> listParticipants(TournamentId tournamentId);
}
