package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UserId id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findAll();
}

