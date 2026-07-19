package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.domain.ports.in.AuthenticateLocalUserUseCase;
import com.gameplatform.local.domain.ports.in.RegisterLocalUserUseCase;
import com.gameplatform.local.domain.model.LocalAdminBuilding;
import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.LocalAdminBuildingLocalRepository;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller REST per l'autenticazione e la registrazione degli utenti locali.
 * Espone gli endpoint di login, signup e recupero delle informazioni
 * dell'utente corrente arricchite con ruoli e edifici associati.
 *
 * @see AuthenticateLocalUserUseCase
 * @see RegisterLocalUserUseCase
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticateLocalUserUseCase authenticateLocalUserUseCase;
    private final RegisterLocalUserUseCase registerLocalUserUseCase;
    private final UserRepository userRepository;
    private final LocalAdminBuildingLocalRepository localAdminBuildingLocalRepository;

    /**
     * Costruisce il controller con i casi d'uso e i repository necessari.
     *
     * @param authenticateLocalUserUseCase caso d'uso per l'autenticazione
     * @param registerLocalUserUseCase caso d'uso per la registrazione
     * @param userRepository repository degli utenti replicati
     * @param localAdminBuildingLocalRepository repository delle associazioni admin-edificio
     */
    public AuthController(AuthenticateLocalUserUseCase authenticateLocalUserUseCase,
                          RegisterLocalUserUseCase registerLocalUserUseCase,
                          UserRepository userRepository,
                          LocalAdminBuildingLocalRepository localAdminBuildingLocalRepository) {
        this.authenticateLocalUserUseCase = authenticateLocalUserUseCase;
        this.registerLocalUserUseCase = registerLocalUserUseCase;
        this.userRepository = userRepository;
        this.localAdminBuildingLocalRepository = localAdminBuildingLocalRepository;
    }

    /**
     * Autentica un utente locale con username e password.
     *
     * @param req la richiesta contenente username e password
     * @return una {@link ResponseEntity} contenente il {@link LoginResponseDto}
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto req) {
        LoginResponseDto response = authenticateLocalUserUseCase.authenticate(req.username(), req.password());
        return ResponseEntity.ok(response);
    }

    /**
     * Registra un nuovo utente locale.
     *
     * @param req la richiesta contenente username, password ed email
     * @return una {@link ResponseEntity} con status 201 e il {@link SignupResponseDto}
     */
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
     * Returns the authenticated user's identity enriched with the
     * {@code userId} and {@code roles} resolved from the locally replicated
     * {@code replicated_users} table (and, for a {@code LOCAL_ADMIN}, the
     * {@code buildings} resolved from {@code local_admin_buildings_local}).
     * Used by the Game Client Emulator after login so the client can drive
     * its UI without decoding the JWT claims. PIANO §7.B.
     *
     * @param auth the Spring Security authentication object (principal.name
     *             is the username subject of the JWT)
     * @return a {@link UserInfoDto} with the enriched user identity
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfoDto> getCurrentUser(Authentication auth) {
        String username = auth != null ? auth.getName() : null;
        if (username == null || username.isBlank()) {
            // Fallback to the legacy single-arg ctor (no enrichment possible).
            return ResponseEntity.ok(new UserInfoDto(null));
        }
        Optional<User> existing = userRepository.findByUsername(username);
        if (existing.isEmpty()) {
            // User not yet locally replicated: return only the username so the
            // client can still navigate (no roles / buildings available).
            return ResponseEntity.ok(new UserInfoDto(username));
        }
        User user = existing.get();
        UserId userId = user.getUserId();
        List<String> buildings = localAdminBuildingLocalRepository.findByUserId(userId).stream()
                .map(binding -> binding.getBuildingId().id())
                .collect(Collectors.toList());
        return ResponseEntity.ok(new UserInfoDto(
                username,
                userId.value(),
                user.getRoles(),
                buildings
        ));
    }
}
