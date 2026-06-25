package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.LoginResponseDto;

public interface AuthenticateLocalUserUseCase {
    LoginResponseDto authenticate(String username, String password);
}
