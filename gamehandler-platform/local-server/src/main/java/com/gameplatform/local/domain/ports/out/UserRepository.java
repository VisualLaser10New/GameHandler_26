package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findByUsername(String username);
    void saveAll(List<User> users);
}
