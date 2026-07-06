package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.UserMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.LocalUserJpaRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.UserJpaRepository;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final LocalUserJpaRepository localUserJpaRepository;
    private final UserMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository, LocalUserJpaRepository localUserJpaRepository, UserMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.localUserJpaRepository = localUserJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = mapper.toEntity(user);
        UserJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UserId userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(userId.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        Optional<User> replicated = jpaRepository.findByUsername(username).map(mapper::toDomain);
        if (replicated.isPresent()) {
            return replicated;
        }
        return localUserJpaRepository.findByUsername(username).map(mapper::toDomainFromLocalUser);
    }

    @Override
    public void saveAll(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<UserJpaEntity> entities = users.stream()
            .map(mapper::toEntity)
            .collect(Collectors.toList());
        jpaRepository.saveAll(entities);
    }

    @Override
    public long count() {
        // M4 — backed by JpaRepository#count() (inherited), which counts the
        // replicated_users rows. The local_users table is intentionally
        // excluded: it is a separate identity store for users registered
        // directly on the local server (not subject to central reconciliation).
        return jpaRepository.count();
    }
}
