package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.in.SyncUsersUseCase;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
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

/**
 * Receives user-replication events ({@code USER_REGISTERED} /
 * {@code USER_UPDATED}) from the Central via outbox and applies them
 * idempotently to the local {@code replicated_users} table. When the
 * upstream event carries a non-null {@code originatingRequestId}
 * (the Central return-event closes a Local-admin
 * {@code ROLE_ASSIGNMENT_REQUESTED} request, PIANO §7.A.7 / §7.B W10),
 * the matching {@code admin_requests_local} row is transitioned to
 * {@code COMPLETED} via {@link AdminRequestRepository#markCompleted}.
 */
@Service
public class UserSyncService implements SyncUsersUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserSyncService.class);

    static final String STALE_EVENT_REASON = "STALE_EVENT";

    private final UserRepository userRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final Clock clock;

    public UserSyncService(UserRepository userRepository,
                           AdminRequestRepository adminRequestRepository,
                           Clock clock) {
        this.userRepository = userRepository;
        this.adminRequestRepository = adminRequestRepository;
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
                    markCompletedIfRequested(dto);
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
                markCompletedIfRequested(dto);
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

    private void markCompletedIfRequested(UserSyncDto dto) {
        String originatingRequestId = dto.originatingRequestId();
        if (originatingRequestId == null || originatingRequestId.isBlank()) {
            return;
        }
        int mutated = adminRequestRepository.markCompleted(
                originatingRequestId, "{\"applied\":true}", Instant.now(clock));
        if (mutated > 0) {
            log.info("Admin request {} marked COMPLETED by user-replication event for userId={}",
                    originatingRequestId, dto.userId());
        } else if (log.isDebugEnabled()) {
            log.debug("Admin request {} already resolved or unknown — markCompleted returned 0 (userId={})",
                    originatingRequestId, dto.userId());
        }
    }
}
