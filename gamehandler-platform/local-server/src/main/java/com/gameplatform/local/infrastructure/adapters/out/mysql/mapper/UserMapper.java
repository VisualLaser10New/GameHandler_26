package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalUserJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class UserMapper {

    public User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<String> rolesList = List.of();
        if (entity.getRoles() != null && !entity.getRoles().isBlank()) {
            rolesList = Arrays.asList(entity.getRoles().split(","));
        }
        return new User(
            new UserId(entity.getUserId()),
            entity.getUsername(),
            entity.getPasswordHash(),
            rolesList,
            entity.getSyncedAt()
        );
    }

    public UserJpaEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }
        String rolesStr = domain.getRoles() != null ? String.join(",", domain.getRoles()) : "";
        return new UserJpaEntity(
            domain.getUserId().value(),
            domain.getUsername(),
            domain.getPasswordHash(),
            rolesStr,
            domain.getSyncedAt()
        );
    }

    public User toDomainFromLocalUser(LocalUserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<String> rolesList = List.of("USER");
        if (entity.getRoles() != null && !entity.getRoles().isBlank()) {
            rolesList = Arrays.stream(entity.getRoles().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return new User(
            new UserId(entity.getId()),
            entity.getUsername(),
            entity.getPasswordHash(),
            rolesList,
            entity.getCreatedAt()
        );
    }
}
