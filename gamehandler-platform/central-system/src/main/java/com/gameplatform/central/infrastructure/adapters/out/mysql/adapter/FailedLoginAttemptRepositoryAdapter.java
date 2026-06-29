package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.FailedLoginAttempt;
import com.gameplatform.central.domain.ports.out.FailedLoginAttemptRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.FailedLoginAttemptJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.FailedLoginAttemptJpaRepository;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
public class FailedLoginAttemptRepositoryAdapter implements FailedLoginAttemptRepository {

    private final FailedLoginAttemptJpaRepository jpaRepository;

    public FailedLoginAttemptRepositoryAdapter(FailedLoginAttemptJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(FailedLoginAttempt attempt) {
        if (attempt == null) {
            return;
        }
        String id = UUID.randomUUID().toString();
        FailedLoginAttemptJpaEntity entity = new FailedLoginAttemptJpaEntity(
                id,
                attempt.username(),
                attempt.attemptTime()
        );
        jpaRepository.save(entity);
    }

    @Override
    public long countFailedAttempts(String username, Instant since) {
        if (username == null || since == null) {
            return 0;
        }
        return jpaRepository.countByUsernameAndAttemptTimeAfter(username, since);
    }
}
