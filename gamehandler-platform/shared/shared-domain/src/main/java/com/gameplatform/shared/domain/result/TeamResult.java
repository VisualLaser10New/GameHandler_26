package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

/**
 * {@link GameResult} variant for team-tournament matches. Approved
 * simplification (ambiguity F/G): a team match yields a SINGLE winner
 * ({@code winnerId}) derived from {@code winnerTeamId}; {@code getWinnerIds()}
 * returns a one-element list.
 *
 * <p>PURE Java record — no annotations (shared-domain rule). Serialised via the
 * shared-mqtt {@code GameResultMixIn} under discriminator {@code "TEAM"}.</p>
 */
public record TeamResult(
        UserId winnerId,
        List<UserId> winnerIds,
        TeamId winnerTeamId,
        WinCondition winCondition
) implements GameResult {

    /**
     * Canonicalising compact constructor: {@code winnerId} is derived from
     * {@code winnerTeamId} when the caller passes it as {@code null}
     * ({@code new UserId(winnerTeamId.value())}); {@code winnerIds} defaults to
     * {@code List.of(winnerId)} when the caller passes {@code null} or empty.
     */
    public TeamResult {
        if (winnerId == null && winnerTeamId != null) {
            winnerId = new UserId(winnerTeamId.value());
        }
        if (winnerIds == null || winnerIds.isEmpty()) {
            winnerIds = List.of(winnerId);
        }
    }

    @Override
    public UserId getWinnerId() {
        return winnerId;
    }

    @Override
    public List<UserId> getWinnerIds() {
        return winnerIds;
    }

    @Override
    public WinCondition getWinCondition() {
        return winCondition;
    }
}