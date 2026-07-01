package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.dto.UserRegisteredEventDto;

public interface RegisterUserFromSyncUseCase {
    void registerFromSync(UserRegisteredEventDto dto);
}
