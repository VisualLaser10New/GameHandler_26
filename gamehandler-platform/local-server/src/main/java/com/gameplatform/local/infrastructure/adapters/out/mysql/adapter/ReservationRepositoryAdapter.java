package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.ReservationJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.ReservationMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.ReservationJpaRepository;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ReservationRepositoryAdapter implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;
    private final ReservationMapper mapper;

    public ReservationRepositoryAdapter(ReservationJpaRepository jpaRepository, ReservationMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Reservation save(Reservation reservation) {
        ReservationJpaEntity entity = mapper.toEntity(reservation);
        ReservationJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Reservation> findById(ReservationId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Reservation> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId.value()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Reservation> findByGameId(GameId gameId) {
        return jpaRepository.findByGameId(gameId.id()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Reservation> findByStatus(ReservationStatus status) {
        return jpaRepository.findByStatus(status.name()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Reservation> findExpired(Instant now) {
        return jpaRepository.findByStatusInAndEndTimeBefore(List.of("PENDING"), now).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public int countByGameIds(List<GameId> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) {
            return 0;
        }
        List<String> ids = gameIds.stream()
            .map(GameId::id)
            .collect(Collectors.toList());
        return jpaRepository.countByGameIdIn(ids);
    }
}
