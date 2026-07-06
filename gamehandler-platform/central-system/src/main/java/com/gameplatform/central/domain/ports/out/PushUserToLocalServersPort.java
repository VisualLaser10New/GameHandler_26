package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import java.util.List;

public interface PushUserToLocalServersPort {
    /**
     * Push a batch of users to a local server and return one
     * {@link UserSyncAckDto} per input user (in input order) describing whether
     * each was applied, skipped as stale, or rejected as poison.
     */
    List<UserSyncAckDto> pushUsers(List<UserSyncDto> users, RegisteredLocalServer server);
}
