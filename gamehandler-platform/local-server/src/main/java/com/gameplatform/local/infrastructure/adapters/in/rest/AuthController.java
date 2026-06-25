package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.ports.in.AuthenticateLocalUserUseCase;
import com.gameplatform.shared.dto.LoginRequestDto;
import com.gameplatform.shared.dto.LoginResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticateLocalUserUseCase authenticateLocalUserUseCase;

    public AuthController(AuthenticateLocalUserUseCase authenticateLocalUserUseCase) {
        this.authenticateLocalUserUseCase = authenticateLocalUserUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto req) {
        LoginResponseDto response = authenticateLocalUserUseCase.authenticate(req.username(), req.password());
        return ResponseEntity.ok(response);
    }
}
