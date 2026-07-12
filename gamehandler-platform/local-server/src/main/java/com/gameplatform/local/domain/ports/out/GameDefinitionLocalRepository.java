package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.GameDefinitionLocal;
import com.gameplatform.shared.domain.model.GameType;

import java.util.List;
import java.util.Optional;

public interface GameDefinitionLocalRepository {
    GameDefinitionLocal save(GameDefinitionLocal gameDefinitionLocal);
    Optional<GameDefinitionLocal> findByGameType(GameType gameType);
    List<GameDefinitionLocal> findAll();
    boolean existsByGameType(GameType gameType);
    void deleteByGameType(GameType gameType);
}
