package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.LocalStatistics;
import com.gameplatform.shared.domain.model.GameType;
import java.util.List;

public interface GetStatisticsUseCase {
    LocalStatistics getStatistics(GameType gameType);
    List<GameSession> getActiveSessions();
}
