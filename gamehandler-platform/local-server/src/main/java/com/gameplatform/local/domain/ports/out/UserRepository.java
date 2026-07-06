package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UserId userId);
    Optional<User> findByUsername(String username);
    void saveAll(List<User> users);

    /**
     * M4 — number of rows currently held in the {@code replicated_users}
     * table. Used by the local {@code GET /internal/users/count} endpoint
     * which is queried by the central {@code UserReplicationReconciliationService}
     * to decide whether a full re-push is required. Backed by the inherited
     * {@code JpaRepository#count()} on {@code UserJpaRepository}.
     */
    long count();
}