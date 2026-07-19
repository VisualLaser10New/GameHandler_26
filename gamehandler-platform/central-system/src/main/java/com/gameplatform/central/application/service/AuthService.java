package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.exception.InvalidCredentialsException;
import com.gameplatform.central.domain.exception.RateLimitExceededException;
import com.gameplatform.central.domain.model.FailedLoginAttempt;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.AuthenticateUserUseCase;
import com.gameplatform.central.domain.ports.out.FailedLoginAttemptRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.central.domain.ports.out.TokenProviderPort;
import com.gameplatform.shared.domain.security.TokenWithExpiry;
import com.gameplatform.shared.dto.LoginResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Servizio applicativo per l'autenticazione degli utenti presso il sistema
 * centrale (Source-of-Truth). Implementa {@link AuthenticateUserUseCase}
 * eseguendo la verifica delle credenziali, la protezione contro il
 * brute-force (rate-limit e password fittizia per utenti inesistenti) e
 * l'emissione del token di sessione.
 *
 * <p>Applica inoltre una protezione temporale contro gli attacchi di tipo
 * timing: la verifica di una password su un utente inesistente esegue comunque
 * un controllo BCrypt fittizio, così da non rivelare l'esistenza o meno
 * dell'username tramite la differenza di tempo di risposta.</p>
 *
 * @see AuthenticateUserUseCase
 * @see com.gameplatform.central.domain.ports.out.UserRepository
 * @see com.gameplatform.central.domain.ports.out.TokenProviderPort
 */
@Service
public class AuthService implements AuthenticateUserUseCase {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DUMMY_HASH = "$2a$10$LwY.F2hWpSXe/9jG4rXf6OQk3V0vYhZ8S.a8m3uW9Ym8X6n3uS3lO";

    private final UserRepository userRepository;
    private final FailedLoginAttemptRepository failedLoginAttemptRepository;
    private final TokenProviderPort tokenProviderPort;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            FailedLoginAttemptRepository failedLoginAttemptRepository,
            TokenProviderPort tokenProviderPort,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.failedLoginAttemptRepository = failedLoginAttemptRepository;
        this.tokenProviderPort = tokenProviderPort;
        this.clock = clock;
    }

    /**
     * Autentica un utente verificando username e password e restituendo la
     * risposta di login contenente il token firmato.
     *
     * <p>Prima della verifica applica un rate-limit basato sui tentativi
     * falliti nell'ultimo minuto; in caso di superamento della soglia rifiuta
     * la richiesta senza rivelare l'esistenza dell'utente. Se l'utente non
     * esiste o la password non corrisponde registra il fallimento e lancia
     * un'eccezione di credenziali non valide (il controllo BCrypt fittizio su
     * utente inesistente previene attacchi di tipo timing). Con credenziali
     * valide genera e restituisce un token con relativa scadenza.</p>
     *
     * @param username il nome utente da autenticare (non deve essere {@code null})
     * @param password la password in chiaro fornita dall'utente (non deve essere {@code null})
     * @return la risposta di login con token, id utente e istante di scadenza
     * @throws RateLimitExceededException se i tentativi falliti negli ultimi 60 secondi
     *         superano la soglia consentita
     * @throws InvalidCredentialsException se l'utente non esiste o la password non è corretta
     * @see #checkRateLimit(String)
     * @see #recordFailure(String)
     */
    @Override
    public LoginResponseDto authenticate(String username, String password) {
        checkRateLimit(username);

        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (BCrypt.checkpw(password, user.getPasswordHash())) {
                Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
                TokenWithExpiry tokenWithExpiry = tokenProviderPort.generateTokenWithExpiry(user, now);
                return new LoginResponseDto(tokenWithExpiry.token(), user.getId().value(), tokenWithExpiry.expiresAt());
            } else {
                recordFailure(username);
                log.warn("Failed login attempt: Incorrect password for username '{}'", username);
                throw new InvalidCredentialsException("Invalid username or password");
            }
        } else {
            // Compute dummy BCrypt check to prevent timing attacks
            BCrypt.checkpw(password, DUMMY_HASH);
            recordFailure(username);
            log.warn("Failed login attempt: User not found for username '{}'", username);
            throw new InvalidCredentialsException("Invalid username or password");
        }
    }

    private void checkRateLimit(String username) {
        Instant since = Instant.now(clock).minusSeconds(60);
        long failures = failedLoginAttemptRepository.countFailedAttempts(username, since);
        if (failures >= 5) {
            log.warn("Rate limit blocked: Username '{}' has had {} failed login attempts in the last 60 seconds.", username, failures);
            throw new RateLimitExceededException("Too many failed login attempts. Please try again later.");
        }
    }

    private void recordFailure(String username) {
        FailedLoginAttempt attempt = new FailedLoginAttempt(username, Instant.now(clock));
        failedLoginAttemptRepository.save(attempt);
    }
}
