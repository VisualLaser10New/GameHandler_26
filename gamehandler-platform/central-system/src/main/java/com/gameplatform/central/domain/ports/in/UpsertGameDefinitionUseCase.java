package com.gameplatform.central.domain.ports.in;

import com.gameplatform.central.domain.model.GameDefinition;

public interface UpsertGameDefinitionUseCase {
    GameDefinition upsert(GameDefinition gameDefinition, String originatingRequestId);

    default GameDefinition upsert(GameDefinition gameDefinition) {
        return upsert(gameDefinition, null);
    }
}