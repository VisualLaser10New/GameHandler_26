package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;

public interface StartGameSessionUseCase {
    GameSession start(GameId gameId, GameType gameType, List<UserId> participants, ReservationId reservationId);
}
