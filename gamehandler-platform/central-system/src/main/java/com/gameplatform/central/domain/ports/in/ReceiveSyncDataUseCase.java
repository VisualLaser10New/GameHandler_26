package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.dto.SyncPayloadDto;

public interface ReceiveSyncDataUseCase {
    void receiveSyncPayload(SyncPayloadDto payload);
}

