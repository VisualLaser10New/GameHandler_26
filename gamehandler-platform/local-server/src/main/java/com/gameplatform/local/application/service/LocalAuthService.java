package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.exception.UserNotFoundException;
import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.in.AuthenticateLocalUserUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.local.domain.ports.out.TokenGeneratorPort;
import com.gameplatform.shared.domain.security.TokenWithExpiry;
import com.gameplatform.shared.dto.LoginResponseDto;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Servizio di autenticazione locale per gli utenti replicati. Verifica
 * le credenziali (username/password) tramite BCrypt contro la tabella
 * {@code replicated_users} e genera un token JWT con scadenza tramite
 * {@link TokenGeneratorPort}. Il timestamp di emissione e' troncato ai
 * secondi per allineamento con la specifica NumericDate del JWT.
 *
 * @see AuthenticateLocalUserUseCase
 * @see UserRepository
 * @see TokenGeneratorPort
 */
@Service
public class LocalAuthService implements AuthenticateLocalUserUseCase {

    private final UserRepository userRepository;
    private final TokenGeneratorPort tokenGeneratorPort;
    private final Clock clock;

    /**
     * Costruisce il servizio di autenticazione locale.
     *
     * @param userRepository     il repository degli utenti replicati (non null)
     * @param tokenGeneratorPort il port per la generazione dei token JWT (non null)
     * @param clock              l'orologio per i timestamp (non null)
     */
    public LocalAuthService(UserRepository userRepository, TokenGeneratorPort tokenGeneratorPort, Clock clock) {
        this.userRepository = userRepository;
        this.tokenGeneratorPort = tokenGeneratorPort;
        this.clock = clock;
    }

    /**
     * Autentica un utente tramite username e password. Verifica la
     * password con BCrypt e genera un token JWT con scadenza.
     *
     * @param username il nome utente (non null)
     * @param password la password in chiaro (non null)
     * @return il DTO di risposta contenente il token JWT, l'ID utente e la scadenza
     * @throws com.gameplatform.local.domain.exception.UserNotFoundException se l'utente non viene trovato
     * @throws org.springframework.security.authentication.BadCredentialsException se la password non corrisponde
     */
    @Override
    public LoginResponseDto authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid username or password");
        }

        // Determine base instant truncated to seconds to align with JWT NumericDate specification
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);

        // Token generator is the single source of truth for the JWT exp claim (fix for BUG-L09 / B11)
        TokenWithExpiry tokenWithExpiry = tokenGeneratorPort.generateTokenWithExpiry(user, now);

        return new LoginResponseDto(tokenWithExpiry.token(), user.getUserId().value(), tokenWithExpiry.expiresAt());
    }
}
