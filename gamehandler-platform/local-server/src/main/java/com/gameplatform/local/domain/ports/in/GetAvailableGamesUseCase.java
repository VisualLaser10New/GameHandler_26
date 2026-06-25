package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.Game;
import java.util.List;

public interface GetAvailableGamesUseCase {
    List<Game> getAvailable();
    List<Game> getAll();
}
