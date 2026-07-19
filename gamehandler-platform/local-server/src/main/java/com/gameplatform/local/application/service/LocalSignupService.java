package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.UserAlreadyExistsException;
import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.model.OutboxEventStatus;
import com.gameplatform.local.domain.ports.in.RegisterLocalUserUseCase;
import com.gameplatform.local.domain.ports.out.LocalSignupUserRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.security.Role;
import com.gameplatform.shared.dto.UserRegisteredEventDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Servizio di registrazione locale per nuovi utenti. Valida i campi
 * obbligatori (username, password, email), verifica unicita' di username
 * ed email, hash la password con BCrypt, persiste l'utente nella tabella
 * {@code local_signup_users} e genera un evento outbox
 * {@code USER_REGISTERED} per la replica verso il Central.
 *
 * @see RegisterLocalUserUseCase
 * @see LocalSignupUserRepository
 * @see OutboxEventRepository
 */
@Service
@Transactional
public class LocalSignupService implements RegisterLocalUserUseCase {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final LocalSignupUserRepository localSignupUserRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LocalSignupService(
            LocalSignupUserRepository localSignupUserRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.localSignupUserRepository = localSignupUserRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Registra un nuovo utente locale. Valida i parametri, verifica
     * l'unicita' di username ed email, hash la password con BCrypt,
     * persiste l'utente e genera l'evento outbox USER_REGISTERED.
     *
     * @param username il nome utente (non blank, max 100 caratteri)
     * @param password la password in chiaro (non blank)
     * @param email    l'email (non blank, formato valido, max 255 caratteri)
     * @return l'utente registrato
     * @throws IllegalArgumentException se uno dei parametri non e' valido
     * @throws com.gameplatform.local.domain.exception.UserAlreadyExistsException se username o email esistono gia'
     */
    @Override
    public LocalSignupUser register(String username, String password, String email) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        username = username.strip();
        email = email.strip();

        if (username.length() > 100) {
            throw new IllegalArgumentException("Username exceeds 100 characters");
        }
        if (email.length() > 255) {
            throw new IllegalArgumentException("Email exceeds 255 characters");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }

        if (localSignupUserRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already exists: " + username);
        }
        if (localSignupUserRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already exists: " + email);
        }

        Instant now = Instant.now(clock);
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        UserId userId = new UserId(UUID.randomUUID().toString());
        List<String> roles = List.of(Role.PLAYER.name());

        LocalSignupUser user = new LocalSignupUser(
                userId,
                username,
                passwordHash,
                email,
                roles,
                now
        );

        LocalSignupUser savedUser;
        try {
            savedUser = localSignupUserRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExistsException("User already exists: " + username, e);
        }

        createUserRegisteredOutboxEvent(savedUser);

        return savedUser;
    }

    /**
     * Crea e persiste un evento outbox {@code USER_REGISTERED} per la
     * replica dell'utente verso il Central. Serialezza il DTO dell'evento
     * in JSON e salva l'evento nello stato PENDING.
     *
     * @param user l'utente registrato (non null)
     * @throws RuntimeException se la serializzazione JSON del payload fallisce
     */
    private void createUserRegisteredOutboxEvent(LocalSignupUser user) {
        try {
            UserRegisteredEventDto eventDto = new UserRegisteredEventDto(
                    user.getUserId().value(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getPasswordHash(),
                    user.getRoles(),
                    user.getCreatedAt()
            );
            String payloadJson = objectMapper.writeValueAsString(eventDto);

            OutboxEvent outboxEvent = new OutboxEvent(
                    UUID.randomUUID().toString(),
                    com.gameplatform.shared.domain.events.UserRegisteredEvent.EVENT_TYPE,
                    payloadJson,
                    OutboxEventStatus.PENDING.name(),
                    user.getCreatedAt(),
                    null,
                    0
            );
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize OutboxEvent payload for USER_REGISTERED", e);
        }
    }
}
