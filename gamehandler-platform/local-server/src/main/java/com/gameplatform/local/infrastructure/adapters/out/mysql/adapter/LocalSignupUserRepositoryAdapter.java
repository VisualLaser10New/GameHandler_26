package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.domain.ports.out.LocalSignupUserRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalUserJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.LocalUserMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.LocalUserJpaRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.UserJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class LocalSignupUserRepositoryAdapter implements LocalSignupUserRepository {

    private final LocalUserJpaRepository jpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final LocalUserMapper mapper;

    public LocalSignupUserRepositoryAdapter(LocalUserJpaRepository jpaRepository, UserJpaRepository userJpaRepository, LocalUserMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username) || userJpaRepository.findByUsername(username).isPresent();
    }

    @Override
    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public LocalSignupUser save(LocalSignupUser user) {
        LocalUserJpaEntity entity = mapper.toEntity(user);
        LocalUserJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
}
