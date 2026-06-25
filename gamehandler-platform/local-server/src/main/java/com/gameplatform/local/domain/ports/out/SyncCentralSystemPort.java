package com.gameplatform.local.domain.ports.out;

import com.gameplatform.shared.dto.SyncPayloadDto;

public interface SyncCentralSystemPort {
    boolean isReachable();
    boolean sendSyncPayload(SyncPayloadDto payload);
}
