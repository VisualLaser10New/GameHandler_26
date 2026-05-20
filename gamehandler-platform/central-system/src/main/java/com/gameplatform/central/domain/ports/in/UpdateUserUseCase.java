package com.gameplatform.central.domain.ports.in;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;

public interface UpdateUserUseCase {
    User updateUser(UserId id, String newPassword, List<String> newRoles);
}

