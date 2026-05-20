package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<String> rolesList = List.of();
        if (entity.getRoles() != null && !entity.getRoles().isBlank()) {
            rolesList = Arrays.stream(entity.getRoles().split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }
        return new User(
                new UserId(entity.getId()),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getEmail(),
                rolesList,
                entity.getCreatedAt()
        );
    }

    public UserJpaEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }
        String rolesStr = "";
        if (domain.getRoles() != null) {
            rolesStr = String.join(",", domain.getRoles());
        }
        return new UserJpaEntity(
                domain.getId() != null ? domain.getId().value() : null,
                domain.getUsername(),
                domain.getPasswordHash(),
                domain.getEmail(),
                rolesStr,
                domain.getCreatedAt()
        );
    }
}
