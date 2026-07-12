package com.gameplatform.central.domain.ports.in;

import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.shared.domain.model.GameType;
import java.util.List;
import java.util.Optional;

public interface ListGameDefinitionsUseCase {
    List<GameDefinition> findAll();
    Optional<GameDefinition> findByGameType(GameType gameType);
}
