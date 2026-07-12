package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.shared.domain.model.TournamentId;
import java.util.List;
import java.util.Optional;

public interface TournamentParticipantRepository {
    TournamentParticipant save(TournamentParticipant participant);
    Optional<TournamentParticipant> findByTournamentAndParticipantId(TournamentId tournamentId, String participantId);
    List<TournamentParticipant> findByTournament(TournamentId tournamentId);
    long countByTournament(TournamentId tournamentId);
    boolean existsByTournamentAndParticipantId(TournamentId tournamentId, String participantId);
    void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId);
}
