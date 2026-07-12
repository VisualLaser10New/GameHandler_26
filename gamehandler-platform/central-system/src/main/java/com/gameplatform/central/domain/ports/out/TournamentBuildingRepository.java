package com.gameplatform.central.domain.ports.out;

import com.gameplatform.shared.domain.model.TournamentId;
import java.util.List;

public interface TournamentBuildingRepository {
    void saveAll(TournamentId tournamentId, List<String> buildingIds);
    List<String> findByTournament(TournamentId tournamentId);
    void deleteByTournament(TournamentId tournamentId);
    boolean existsByTournamentAndBuilding(TournamentId tournamentId, String buildingId);
}
