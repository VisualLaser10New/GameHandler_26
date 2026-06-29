package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.FailedLoginAttemptJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

public interface FailedLoginAttemptJpaRepository extends JpaRepository<FailedLoginAttemptJpaEntity, String> {
    long countByUsernameAndAttemptTimeAfter(String username, Instant since);
}
