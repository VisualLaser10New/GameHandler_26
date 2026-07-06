package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.in.SyncUsersUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserSyncService implements SyncUsersUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserSyncService.class);

    static final String STALE_EVENT_REASON = "STALE_EVENT";

    private final UserRepository userRepository;
    private final Clock clock;

    public UserSyncService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public List<UserSyncAckDto> syncUsers(List<UserSyncDto> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }

        List<UserSyncAckDto> acks = new ArrayList<>(users.size());
        for (UserSyncDto dto : users) {
            try {
                Optional<User> existing = userRepository.findById(new UserId(dto.userId()));

                if (existing.isPresent()
                        && dto.occurredAt() != null
                        && existing.get().getEventTime().isAfter(dto.occurredAt())) {
                    log.warn("Stale user replication event for userId={}: existing eventTime={} > incoming occurredAt={}; skipping",
                            dto.userId(), existing.get().getEventTime(), dto.occurredAt());
                    acks.add(new UserSyncAckDto(dto.userId(), true, STALE_EVENT_REASON));
                    continue;
                }

                Instant now = Instant.now(clock);
                Instant eventTime = dto.occurredAt() != null ? dto.occurredAt() : now;
                User user = new User(
                        new UserId(dto.userId()),
                        dto.username(),
                        dto.hashedPassword(),
                        dto.email(),
                        dto.roles(),
                        eventTime,
                        now
                );
                userRepository.save(user);
                acks.add(new UserSyncAckDto(dto.userId(), true, null));
            } catch (IllegalArgumentException | DataIntegrityViolationException e) {
                // Poison user (e.g. blank username from the User ctor) — quarantine this
                // user only; the batch MUST continue. The exception is caught INSIDE the
                // loop so the class-level @Transactional batch does NOT roll back.
                log.warn("Poison user replication event for userId={}: {}", dto.userId(), e.getMessage());
                acks.add(new UserSyncAckDto(dto.userId(), false, "VALIDATION_ERROR: " + e.getMessage()));
            }
        }
        return acks;
    }
}
