package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.TournamentSummaryDto;

import java.util.List;

/**
 * Use case (PIANO §7.B): returns the {@code tournaments_summary_local}
 * rows optionally filtered by status, projected to
 * {@link TournamentSummaryDto}. Only {@code deleted == false} rows are
 * returned (the sync service physically removes tombstones, so in steady
 * state every persisted row has {@code deleted == false}).
 */
public interface ListTournamentSummariesUseCase {

    List<TournamentSummaryDto> listSummaries(TournamentStatus statusFilter);
}