package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.TournamentStanding;
import com.gameplatform.shared.domain.model.TournamentId;
import java.util.List;
import java.util.Optional;

public interface TournamentStandingRepository {
    TournamentStanding save(TournamentStanding standing);
    Optional<TournamentStanding> findByTournamentAndParticipantId(TournamentId tournamentId, String participantId);
    List<TournamentStanding> findByTournament(TournamentId tournamentId);
    void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId);
}
