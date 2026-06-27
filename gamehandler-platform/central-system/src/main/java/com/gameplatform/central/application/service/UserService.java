package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.UserAlreadyExistsException;
import com.gameplatform.central.domain.exception.UserNotFoundException;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.GetAllUsersUseCase;
import com.gameplatform.central.domain.ports.in.RegisterUserUseCase;
import com.gameplatform.central.domain.ports.in.UpdateUserUseCase;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserSyncDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService implements RegisterUserUseCase, UpdateUserUseCase, GetAllUsersUseCase {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public UserService(UserRepository userRepository, OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @Override
    public List<UserSyncDto> getAllUsersForSync() {
        return userRepository.findAll().stream().map(user ->
            new UserSyncDto(user.getId().value(), user.getUsername(), user.getPasswordHash(), user.getRoles())
        ).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public User register(String username, String password, String email) {
        if (userRepository.findByUsername(username).isPresent() || userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("Username or email already in use");
        }

        String userId = UUID.randomUUID().toString();
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        User registrato = new User(new UserId(userId), username, hashedPassword, email, List.of("USER"), Instant.now());

        try {
            return saveUserOnDB(registrato, "USER_REGISTERED");
        } catch (DataIntegrityViolationException e) {
            log.warn("Database unique constraint violation during registration for username: {} or email: {}", username, email, e);
            throw new UserAlreadyExistsException("User already exists", e);
        }
    }

    @Transactional
    @Override
    public User updateUser(UserId id, String newPassword, List<String> newRoles) {
        User user = userRepository.findById(id).orElseThrow(() ->
            new UserNotFoundException("User not found")
        );

        if (newPassword != null && !newPassword.isBlank()) {
            String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            user.changePassword(hashedPassword);
        }

        if (newRoles != null && !newRoles.isEmpty()) {
            List<String> mergedRoles = new ArrayList<>(user.getRoles());
            for (String role : newRoles) {
                if (!mergedRoles.contains(role)) {
                    mergedRoles.add(role);
                }
            }
            user.updateRoles(mergedRoles);
        }

        return saveUserOnDB(user, "USER_UPDATED");
    }
    
    private User saveUserOnDB(User user, String eventType) {
        User savedUser = userRepository.save(user);

        UserSyncDto userSyncDto = new UserSyncDto(savedUser.getId().value(), savedUser.getUsername(), savedUser.getPasswordHash(), savedUser.getRoles());

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
