package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.dto.LoginResponseDto;

public interface AuthenticateUserUseCase {
    LoginResponseDto authenticate(String username, String password);
}

