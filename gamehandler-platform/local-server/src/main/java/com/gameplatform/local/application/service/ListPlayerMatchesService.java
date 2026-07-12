package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.in.ListPlayerMatchesUseCase;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerMatchDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Read use case (PIANO §7.B): returns the COMPLETED game sessions in
 * which the given user participated, optionally filtered by game type.
 * The filter is applied in Java on top of
 * {@link GameSessionRepository#findByParticipant} (which returns every
 * session of any status); only {@link GameStatus#COMPLETED} sessions are
 * returned, projected to {@link PlayerMatchDto}.
 */
@Service
@Transactional(readOnly = true)
public class ListPlayerMatchesService implements ListPlayerMatchesUseCase {

    private final GameSessionRepository gameSessionRepository;

    public ListPlayerMatchesService(GameSessionRepository gameSessionRepository) {
        this.gameSessionRepository = gameSessionRepository;
    }

    @Override
    public List<PlayerMatchDto> listCompletedMatches(UserId userId, GameType gameTypeFilter) {
        if (userId == null) {
            return List.of();
        }
        List<GameSession> sessions = gameSessionRepository.findByParticipant(userId);
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        return sessions.stream()
                .filter(s -> s.getStatus() == GameStatus.COMPLETED)
                .filter(s -> gameTypeFilter == null || s.getGameType() == gameTypeFilter)
                .map(ListPlayerMatchesService::toDto)
                .collect(Collectors.toList());
    }

    private static PlayerMatchDto toDto(GameSession session) {
        List<String> participants = session.getParticipants().stream()
                .map(u -> u != null ? u.value() : null)
                .collect(Collectors.toList());
        String winnerId = session.getWinnerId() != null ? session.getWinnerId().value() : null;
        return new PlayerMatchDto(
                session.getId() != null ? session.getId().value() : null,
                session.getGameType(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getDurationSeconds(),
                winnerId,
                session.getWinCondition(),
                participants
        );
    }
}