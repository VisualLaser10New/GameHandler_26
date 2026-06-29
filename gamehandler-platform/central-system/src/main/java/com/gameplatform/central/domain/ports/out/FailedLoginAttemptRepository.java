package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.FailedLoginAttempt;
import java.time.Instant;

public interface FailedLoginAttemptRepository {
    void save(FailedLoginAttempt attempt);
    long countFailedAttempts(String username, Instant since);
}
