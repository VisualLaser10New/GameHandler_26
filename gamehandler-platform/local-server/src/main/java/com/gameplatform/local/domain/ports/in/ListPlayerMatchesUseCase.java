package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerMatchDto;

import java.util.List;

/**
 * Use case (PIANO §7.B): returns the COMPLETED game sessions in which the
 * given user participated, optionally filtered by {@code gameType}. The
 * filter is applied in Java on top of
 * {@code GameSessionRepository.findByParticipant} because the repository
 * already returns the full per-user session history (any status).
 */
public interface ListPlayerMatchesUseCase {

    List<PlayerMatchDto> listCompletedMatches(UserId userId, GameType gameTypeFilter);
}