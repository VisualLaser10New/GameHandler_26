package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import java.util.List;

public interface SyncUsersUseCase {
    /**
     * Sync a batch of users and return one {@link UserSyncAckDto} per input user
     * (in input order). A poison user does NOT abort the batch.
     */
    List<UserSyncAckDto> syncUsers(List<UserSyncDto> users);
}
