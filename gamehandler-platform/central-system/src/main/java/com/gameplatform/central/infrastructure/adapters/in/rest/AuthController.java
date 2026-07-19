package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.ports.in.AuthenticateUserUseCase;
import com.gameplatform.shared.dto.LoginRequestDto;
import com.gameplatform.shared.dto.LoginResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST adapter for user authentication (login).
 *
 * <p>Exception-to-HTTP-status mapping is delegated to {@link GlobalExceptionHandler}:
 * <ul>
 *   <li>{@code InvalidCredentialsException}  → 401 Unauthorized</li>
 *   <li>{@code RateLimitExceededException}   → 429 Too Many Requests</li>
 *   <li>{@code MethodArgumentNotValidException} → 400 Bad Request</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;

    /**
     * Costruisce il controller iniettando il caso d'uso di autenticazione.
     *
     * @param authenticateUserUseCase caso d'uso per l'autenticazione degli utenti, non {@code null}
     */
    public AuthController(AuthenticateUserUseCase authenticateUserUseCase) {
        this.authenticateUserUseCase = authenticateUserUseCase;
    }

    /**
     * Autentica un utente a partire dalle credenziali fornite e restituisce il token di sessione.
     *
     * <p>Delega l'elaborazione al caso d'uso di dominio e, in caso di successo,
     * restituisce il payload di risposta contenente i dati di autenticazione.</p>
     *
     * @param request dto di richiesta con username e password, validato tramite {@code @Valid}; non {@code null}
     * @return {@link ResponseEntity} con stato {@code 200 OK} e il {@link LoginResponseDto} generato
     * @throws com.gameplatform.central.domain.exception.InvalidCredentialsException se le credenziali non sono valide (mappato a {@code 401})
     * @throws com.gameplatform.central.domain.exception.RateLimitExceededException se è stato superato il limite di tentativi (mappato a {@code 429})
     * @throws jakarta.validation.ValidationException se il body non supera i vincoli di validazione (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        LoginResponseDto responseDto = authenticateUserUseCase.authenticate(
                request.username(),
                request.password()
        );
        return ResponseEntity.ok(responseDto);
    }
}
