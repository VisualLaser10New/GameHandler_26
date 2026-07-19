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
 * Caso d'uso in lettura (PIANO §7.B): restituisce le sessioni di gioco
 * completate (stato COMPLETED) a cui un determinato utente ha partecipato,
 * opzionalmente filtrate per tipo di gioco. Il filtro e' applicato in
 * Java su {@link GameSessionRepository#findByParticipant} (che restituisce
 * sessioni di qualsiasi stato); solo le sessioni COMPLETED vengono
 * restituite, proiettate in {@link PlayerMatchDto}.
 *
 * @see ListPlayerMatchesUseCase
 * @see GameSessionRepository
 */
@Service
@Transactional(readOnly = true)
public class ListPlayerMatchesService implements ListPlayerMatchesUseCase {

    private final GameSessionRepository gameSessionRepository;

    /**
     * Costruisce il servizio con il repository delle sessioni di gioco.
     *
     * @param gameSessionRepository il repository per l'accesso alle sessioni di gioco (non null)
     */
    public ListPlayerMatchesService(GameSessionRepository gameSessionRepository) {
        this.gameSessionRepository = gameSessionRepository;
    }

    /**
     * Restituisce le partite completate per un dato utente, opzionalmente
     * filtrate per tipo di gioco. Le sessioni sono proiettate in
     * {@link PlayerMatchDto}.
     *
     * @param userId         l'identificativo dell'utente (se null, restituisce lista vuota)
     * @param gameTypeFilter filtro opzionale per tipo di gioco (null per nessun filtro)
     * @return la lista delle partite completate dell'utente
     */
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

    /**
     * Converte una {@link GameSession} nel corrispondente
     * {@link PlayerMatchDto}. I partecipanti vengono mappati da
     * {@link UserId} a stringa; valori null vengono preservati.
     *
     * @param session la sessione di gioco dal modello di dominio (non null)
     * @return il DTO con id, gameType, startedAt, endedAt, durationSeconds,
     *         winnerId, winCondition e la lista dei partecipanti
     */
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