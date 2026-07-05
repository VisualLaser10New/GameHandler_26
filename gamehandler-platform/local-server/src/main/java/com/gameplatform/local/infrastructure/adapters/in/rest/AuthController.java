package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.domain.ports.in.AuthenticateLocalUserUseCase;
import com.gameplatform.local.domain.ports.in.RegisterLocalUserUseCase;
import com.gameplatform.shared.dto.LoginRequestDto;
import com.gameplatform.shared.dto.LoginResponseDto;
import com.gameplatform.shared.dto.SignupRequestDto;
import com.gameplatform.shared.dto.SignupResponseDto;
import com.gameplatform.shared.dto.UserInfoDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticateLocalUserUseCase authenticateLocalUserUseCase;
    private final RegisterLocalUserUseCase registerLocalUserUseCase;

    public AuthController(
            AuthenticateLocalUserUseCase authenticateLocalUserUseCase,
            RegisterLocalUserUseCase registerLocalUserUseCase) {
        this.authenticateLocalUserUseCase = authenticateLocalUserUseCase;
        this.registerLocalUserUseCase = registerLocalUserUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto req) {
        LoginResponseDto response = authenticateLocalUserUseCase.authenticate(req.username(), req.password());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponseDto> signup(@RequestBody SignupRequestDto req) {
        LocalSignupUser user = registerLocalUserUseCase.register(req.username(), req.password(), req.email());
        SignupResponseDto response = new SignupResponseDto(
                user.getUserId().value(),
                user.getUsername(),
                user.getEmail()
        );
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Returns the username of the currently authenticated user.
     * Used by the Game Client Emulator after login to identify itself.
     *
     * @param auth the Spring Security authentication object
     * @return a {@link UserInfoDto} with the authenticated username
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfoDto> getCurrentUser(Authentication auth) {
        return ResponseEntity.ok(new UserInfoDto(auth.getName()));
    }
}
