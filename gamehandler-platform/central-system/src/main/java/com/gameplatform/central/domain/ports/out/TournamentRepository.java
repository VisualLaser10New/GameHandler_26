package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import java.util.List;
import java.util.Optional;

public interface TournamentRepository {
    Tournament save(Tournament tournament);
    Optional<Tournament> findById(TournamentId id);
    List<Tournament> findAll();
    List<Tournament> findByStatus(TournamentStatus status);
    boolean existsById(TournamentId id);
    void deleteById(TournamentId id);
    Optional<Tournament> findByIdForUpdate(TournamentId id);
}
