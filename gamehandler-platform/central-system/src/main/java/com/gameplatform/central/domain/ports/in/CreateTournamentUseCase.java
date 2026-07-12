package com.gameplatform.central.domain.ports.in;

import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.shared.dto.TournamentDto;
import java.util.List;

public interface CreateTournamentUseCase {
    TournamentDto create(Tournament tournament, List<String> buildingIds, String originatingRequestId);

    default TournamentDto create(Tournament tournament, List<String> buildingIds) {
        return create(tournament, buildingIds, null);
    }
}