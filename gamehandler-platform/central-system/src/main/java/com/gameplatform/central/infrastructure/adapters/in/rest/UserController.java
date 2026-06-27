package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.RegisterUserUseCase;
import com.gameplatform.shared.dto.CreateUserRequestDto;
import com.gameplatform.shared.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST adapter for user registration.
 *
 * <p>Exception-to-HTTP-status mapping is delegated to {@link GlobalExceptionHandler}:
 * <ul>
 *   <li>{@code UserAlreadyExistsException} → 409 Conflict</li>
 *   <li>{@code MethodArgumentNotValidException} → 400 Bad Request</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final RegisterUserUseCase registerUserUseCase;

    public UserController(RegisterUserUseCase registerUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
    }

    @PostMapping
    public ResponseEntity<UserDto> registerUser(@Valid @RequestBody CreateUserRequestDto request) {
        User registeredUser = registerUserUseCase.register(
                request.username(),
                request.password(),
                request.email()
        );

        UserDto responseDto = new UserDto(
                registeredUser.getId().value(),
                registeredUser.getUsername(),
                registeredUser.getEmail(),
                registeredUser.getRoles(),
                registeredUser.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }
}
