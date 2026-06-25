package com.gameplatform.local.infrastructure.security;

import com.gameplatform.local.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    public String generateToken(User user) {
        return "mock-jwt-token-for-" + user.getUsername();
    }
}
