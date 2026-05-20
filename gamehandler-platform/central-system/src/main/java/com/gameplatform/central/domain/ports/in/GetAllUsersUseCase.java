package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.dto.UserSyncDto;
import java.util.List;

public interface GetAllUsersUseCase {
    List<UserSyncDto> getAllUsersForSync();
}

