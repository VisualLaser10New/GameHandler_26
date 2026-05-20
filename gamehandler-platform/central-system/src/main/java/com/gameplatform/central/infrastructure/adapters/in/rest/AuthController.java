package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.ports.in.AuthenticateUserUseCase;
import com.gameplatform.shared.dto.LoginRequestDto;
import com.gameplatform.shared.dto.LoginResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;

    public AuthController(AuthenticateUserUseCase authenticateUserUseCase) {
        this.authenticateUserUseCase = authenticateUserUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto request) {
        LoginResponseDto responseDto = authenticateUserUseCase.authenticate(
                request.username(),
                request.password()
        );
        return ResponseEntity.ok(responseDto);
    }
}

