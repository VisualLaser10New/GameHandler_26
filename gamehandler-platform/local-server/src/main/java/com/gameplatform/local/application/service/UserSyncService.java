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
 * Riceve eventi di replica utente ({@code USER_REGISTERED} /
 * {@code USER_UPDATED}) dal Central tramite outbox e li applica
 * idempotentemente alla tabella locale {@code replicated_users}.
 * Gestisce la protezione da eventi stale tramite confronto timestamp
 * e utenti poison senza far rollbackare l'intero batch. Quando
 * l'evento trasporta un {@code originatingRequestId}, la riga
 * {@code admin_requests_local} corrispondente viene transizionata
 * a COMPLETED.
 *
 * @see SyncUsersUseCase
 * @see UserRepository
 * @see AdminRequestRepository
 */
@Service
public class UserSyncService implements SyncUsersUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserSyncService.class);

    static final String STALE_EVENT_REASON = "STALE_EVENT";

    private final UserRepository userRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final Clock clock;

    /**
     * Costruisce il servizio con i repository necessari per la replica
     * degli utenti e la chiusura delle richieste admin.
     *
     * @param userRepository           il repository locale degli utenti replicati
     * @param adminRequestRepository   il repository per la chiusura delle richieste admin
     * @param clock                    l'orologio per la generazione dei timestamp
     */
    public UserSyncService(UserRepository userRepository,
                           AdminRequestRepository adminRequestRepository,
                           Clock clock) {
        this.userRepository = userRepository;
        this.adminRequestRepository = adminRequestRepository;
        this.clock = clock;
    }

    /**
     * Sincronizza una lista di utenti replicati dal Central. Per ogni
     * utente, verifica se l'evento e' stale (timestamp piu' vecchio
     * dell'esistente), applica la replica o scarta l'evento. Gestisce
     * utenti poison catturando eccezioni per-evento senza interrompere
     * il batch. Se l'evento trasporta un originatingRequestId, la
     * richiesta admin corrispondente viene marcata come COMPLETED.
     *
     * @param users la lista di DTO utente da replicare (puo' essere null o vuota)
     * @return la lista degli acknowledgement per ogni utente processato
     */
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

    /**
     * Se il DTO utente trasporta un {@code originatingRequestId} non blank,
     * marca la corrispondente richiesta admin come COMPLETED tramite
     * {@link AdminRequestRepository#markCompleted}.
     *
     * @param dto il DTO utente da cui estrarre l'originatingRequestId (non null)
     */
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
