package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.AdminRequestLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.AdminRequestLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.AdminRequestLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.AdminRequestLocalJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA adapter for the {@link AdminRequestRepository} port (PIANO §7.B).
 * {@code markCompleted} and {@code markFailed} delegate to the
 * {@link AdminRequestLocalJpaRepository} conditional bulk UPDATEs
 * ({@code WHERE status = 'PENDING'}) — idempotent on re-delivery of the
 * same return-event (a second call against an already-resolved row is a
 * no-op). The {2} return value lets the matching {@code *SyncService}
 * observe whether the transition actually mutated any row (it does not
 * have to read-then-write).
 */
@Component
public class AdminRequestRepositoryAdapter implements AdminRequestRepository {

    private final AdminRequestLocalJpaRepository jpaRepository;
    private final AdminRequestLocalMapper mapper;
    private final Clock clock;

    public AdminRequestRepositoryAdapter(AdminRequestLocalJpaRepository jpaRepository,
                                         AdminRequestLocalMapper mapper,
                                         Clock clock) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminRequestLocal save(AdminRequestLocal request) {
        if (request == null) {
            return null;
        }
        AdminRequestLocalJpaEntity entity = mapper.toEntity(request);
        AdminRequestLocalJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminRequestLocal> findByRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findById(requestId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminRequestLocal> findByActingUserId(String actingUserId) {
        if (actingUserId == null || actingUserId.isBlank()) {
            return List.of();
        }
        return jpaRepository.findByActingUserId(actingUserId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminRequestLocal> findByActingUserIdAndStatus(String actingUserId, String status) {
        if (actingUserId == null || actingUserId.isBlank() || status == null || status.isBlank()) {
            return List.of();
        }
        return jpaRepository.findByActingUserIdAndStatus(actingUserId, status).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int markCompleted(String requestId, String resultData, Instant now) {
        if (requestId == null || requestId.isBlank()) {
            return 0;
        }
        Instant ts = now != null ? now : Instant.now(clock);
        return jpaRepository.markCompleted(requestId, resultData, ts);
    }

    @Override
    @Transactional
    public int markFailed(String requestId, String reason, Instant now) {
        if (requestId == null || requestId.isBlank()) {
            return 0;
        }
        Instant ts = now != null ? now : Instant.now(clock);
        return jpaRepository.markFailed(requestId, reason, ts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminRequestLocal> findPendingOlderThan(Instant threshold) {
        if (threshold == null) {
            return List.of();
        }
        return jpaRepository.findByStatusAndCreatedAtBefore("PENDING", threshold).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}