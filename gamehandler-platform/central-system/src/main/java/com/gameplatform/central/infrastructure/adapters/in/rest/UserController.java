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

    /**
     * Costruisce il controller iniettando il caso d'uso di registrazione utente.
     *
     * @param registerUserUseCase caso d'uso per la registrazione degli utenti, non {@code null}
     */
    public UserController(RegisterUserUseCase registerUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
    }

    /**
     * Registra un nuovo utente nel sistema a partire dai dati forniti.
     *
     * <p>Delega la creazione al caso d'uso di dominio e restituisce il DTO
     * rappresentante l'utente appena creato.</p>
     *
     * @param request dto di richiesta con username, password ed email, validato tramite {@code @Valid}; non {@code null}
     * @return {@link ResponseEntity} con stato {@code 201 Created} e il {@link UserDto} dell'utente registrato
     * @throws com.gameplatform.central.domain.exception.UserAlreadyExistsException se lo username è già in uso (mappato a {@code 409})
     * @throws jakarta.validation.ValidationException se il body non supera i vincoli di validazione (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
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
