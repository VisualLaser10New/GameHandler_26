package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.TournamentDto;
import java.util.List;

public interface ListTournamentsUseCase {
    List<TournamentDto> findAll();
    List<TournamentDto> findByStatus(TournamentStatus status);
}
