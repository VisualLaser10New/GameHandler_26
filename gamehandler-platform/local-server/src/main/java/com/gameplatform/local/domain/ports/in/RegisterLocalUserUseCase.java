package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.LocalSignupUser;

public interface RegisterLocalUserUseCase {
    LocalSignupUser register(String username, String password, String email);
}
