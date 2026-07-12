package com.gameplatform.central.domain.ports.in;

import com.gameplatform.central.domain.model.GameDefinition;

public interface UpsertGameDefinitionUseCase {
    GameDefinition upsert(GameDefinition gameDefinition);
}
