package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.shared.domain.model.GameType;
import java.util.List;
import java.util.Optional;

public interface GameDefinitionRepository {
    GameDefinition save(GameDefinition gameDefinition);
    Optional<GameDefinition> findByGameType(GameType gameType);
    List<GameDefinition> findAll();
    boolean existsByGameType(GameType gameType);
    void deleteByGameType(GameType gameType);
}
