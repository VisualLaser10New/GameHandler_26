package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.UserSyncDto;
import java.util.List;

public interface PushUserToLocalServersPort {
    void pushUsers(List<UserSyncDto> users, RegisteredLocalServer server);
}

