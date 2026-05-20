package com.gameplatform.central.domain.ports.in;

import com.gameplatform.central.domain.model.User;

public interface RegisterUserUseCase {
    User register(String username, String password, String email);
}

