package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.UserAlreadyExistsException;
import com.gameplatform.central.domain.exception.UserNotFoundException;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.GetAllUsersUseCase;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.in.RegisterUserUseCase;
import com.gameplatform.central.domain.ports.in.UpdateUserUseCase;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.security.Role;
import com.gameplatform.shared.dto.UserRegisteredEventDto;
import com.gameplatform.shared.dto.UserSyncDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servizio applicativo per la gestione degli utenti presso il sistema centrale
 * (Source-of-Truth). Implementa la registrazione, l'aggiornamento, la lettura
 * per replica e la creazione da evento di sincronizzazione, scrivendo in modo
 * atomico l'evento outbox di replica ad ogni mutazione.
 *
 * <p>Le password sono sempre memorizzate in forma hash BCrypt. Ogni salvataggio
 * emette un {@code USER_REGISTERED} o {@code USER_UPDATED} che verrà propagato
 * ai Local Server dallo scheduler di replica.</p>
 *
 * @see RegisterUserUseCase
 * @see UpdateUserUseCase
 * @see GetAllUsersUseCase
 * @see RegisterUserFromSyncUseCase
 */
@Service
public class UserService implements RegisterUserUseCase, UpdateUserUseCase, GetAllUsersUseCase, RegisterUserFromSyncUseCase {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UserService(UserRepository userRepository, OutboxEventRepository outboxEventRepository,
                      ObjectMapper objectMapper, Clock clock) {
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Restituisce lo snapshot completo degli utenti centrali per la replica ai
     * Local Server.
     *
     * @return la lista dei DTO di sincronizzazione (con hash password, ruoli ed
     *         email) di tutti gli utenti; lista vuota (mai {@code null}) se non
     *         esiste alcun utente
     */
    @Transactional
    @Override
    public List<UserSyncDto> getAllUsersForSync() {
        return userRepository.findAll().stream().map(user ->
            new UserSyncDto(user.getId().value(), user.getUsername(), user.getEmail(), user.getPasswordHash(), user.getRoles(), Instant.now(clock))
        ).collect(Collectors.toList());
    }

    /**
     * Registra un nuovo utente con ruolo PLAYER e ne emette l'evento di replica.
     *
     * @param username il nome utente (univoco, non vuoto)
     * @param password la password in chiaro da hashare con BCrypt
     * @param email l'email dell'utente (univoca, non vuota)
     * @return l'utente appena creato e persistito
     * @throws UserAlreadyExistsException se username o email sono già in uso
     */
    @Transactional
    @Override
    public User register(String username, String password, String email) {
        if (userRepository.findByUsername(username).isPresent() || userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("Username or email already in use");
        }

        String userId = UUID.randomUUID().toString();
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        User registrato = new User(new UserId(userId), username, hashedPassword, email, List.of(Role.PLAYER.name()), Instant.now());

        try {
            return saveUserOnDB(registrato, "USER_REGISTERED");
        } catch (DataIntegrityViolationException e) {
            log.warn("Database unique constraint violation during registration for username: {} or email: {}", username, email, e);
            throw new UserAlreadyExistsException("User already exists", e);
        }
    }

    /**
     * Crea un utente centrale a partire da un evento di sincronizzazione, se non
     * già presente per id, username o email.
     *
     * <p>L'operazione è idempotente: se l'utente esiste già (per id, username o
     * email) viene saltata silenziosamente. In caso di violazione di vincolo
     * univoco in concorrenza, l'eccezione è registrata e ignorata mantenendo
     * l'utente esistente.</p>
     *
     * @param dto l'evento di registrazione ricevuto dal Local Server (non deve
     *        essere {@code null})
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void registerFromSync(UserRegisteredEventDto dto) {
        UserId userId = new UserId(dto.userId());
        if (userRepository.findById(userId).isPresent()) {
            log.info("User already exists from sync, skipping: {}", dto.userId());
            return;
        }
        if (userRepository.findByUsername(dto.username()).isPresent()) {
            log.info("Username already exists from sync, skipping: {}", dto.username());
            return;
        }
        if (userRepository.findByEmail(dto.email()).isPresent()) {
            log.info("Email already exists from sync, skipping: {}", dto.email());
            return;
        }

        User user = new User(userId, dto.username(), dto.hashedPassword(), dto.email(), dto.roles(), dto.createdAt());
        try {
            saveUserOnDB(user, "USER_REGISTERED");
            log.info("Created central user from sync: {}", dto.userId());
        } catch (DataIntegrityViolationException e) {
            log.warn("Central user already exists for userId={}; username={}; buildingId={}; keeping existing password; the losing building is still locally consistent",
                    dto.userId(), dto.username(), null, e);
        }
    }

    /**
     * Aggiorna la password e/o i ruoli di un utente esistente, emettendo
     * l'evento di replica {@code USER_UPDATED}.
     *
     * @param id l'id dell'utente da aggiornare (non deve essere {@code null})
     * @param newPassword la nuova password in chiaro, o {@code null}/vuota per
     *        non modificarla
     * @param newRoles i nuovi ruoli (i duplicati sono rimossi); {@code null} o
     *        vuoto per non modificarli
     * @param originatingRequestId l'id della richiesta originaria, o
     *        {@code null} sul path REST diretto
     * @return l'utente aggiornato e persistito
     * @throws UserNotFoundException se l'utente con l'id indicato non esiste
     */
    @Transactional
    @Override
    public User updateUser(UserId id, String newPassword, List<String> newRoles, String originatingRequestId) {
        User user = userRepository.findById(id).orElseThrow(() ->
            new UserNotFoundException("User not found")
        );

        if (newPassword != null && !newPassword.isBlank()) {
            String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            user.changePassword(hashedPassword);
        }

        if (newRoles != null && !newRoles.isEmpty()) {
            List<String> deduplicatedRoles = newRoles.stream()
                    .distinct()
                    .collect(Collectors.toList());
            user.updateRoles(deduplicatedRoles);
        }

        return saveUserOnDB(user, "USER_UPDATED", originatingRequestId);
    }

    /**
     * Persiste un utente e scrive l'evento outbox di replica corrispondente.
     *
     * @param user l'utente da salvare (non deve essere {@code null})
     * @param eventType il tipo di evento da emettere ({@code USER_REGISTERED}/
     *        {@code USER_UPDATED})
     * @return l'utente salvato
     */
    private User saveUserOnDB(User user, String eventType) {
        return saveUserOnDB(user, eventType, null);
    }

    /**
     * Persiste un utente e scrive l'evento outbox di replica, propagando
     * l'id della richiesta originaria nel DTO di sincronizzazione.
     *
     * @param user l'utente da salvare (non deve essere {@code null})
     * @param eventType il tipo di evento da emettere
     * @param originatingRequestId l'id della richiesta originaria, o {@code null}
     * @return l'utente salvato
     * @throws RuntimeException se la serializzazione JSON dell'utente fallisce
     */
    private User saveUserOnDB(User user, String eventType, String originatingRequestId) {
        User savedUser = userRepository.save(user);

        UserSyncDto userSyncDto = new UserSyncDto(savedUser.getId().value(), savedUser.getUsername(), savedUser.getEmail(), savedUser.getPasswordHash(), savedUser.getRoles(), Instant.now(clock), originatingRequestId);

        String jsonPayLoad;
        try {
            jsonPayLoad = objectMapper.writeValueAsString(userSyncDto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize user to JSON: ", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent(UUID.randomUUID().toString(), eventType, jsonPayLoad, OutboxEventStatus.PENDING, Instant.now(), null);

        outboxEventRepository.save(outboxEvent);

        return savedUser;
    }
}
