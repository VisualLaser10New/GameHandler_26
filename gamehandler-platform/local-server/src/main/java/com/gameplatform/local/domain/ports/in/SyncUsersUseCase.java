package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.UserSyncDto;
import java.util.List;

public interface SyncUsersUseCase {
    void syncUsers(List<UserSyncDto> users);
}
