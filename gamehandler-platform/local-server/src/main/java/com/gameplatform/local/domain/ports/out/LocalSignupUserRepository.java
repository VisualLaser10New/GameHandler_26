package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.LocalSignupUser;

public interface LocalSignupUserRepository {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    LocalSignupUser save(LocalSignupUser user);
}
