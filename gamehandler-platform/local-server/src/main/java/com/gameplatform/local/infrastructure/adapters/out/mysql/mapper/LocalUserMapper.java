package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalUserJpaEntity;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class LocalUserMapper {

    public LocalSignupUser toDomain(LocalUserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new LocalSignupUser(
            new UserId(entity.getId()),
            entity.getUsername(),
            entity.getPasswordHash(),
            entity.getEmail(),
            parseRoles(entity.getRoles()),
            entity.getCreatedAt()
        );
    }

    public LocalUserJpaEntity toEntity(LocalSignupUser domain) {
        if (domain == null) {
            return null;
        }
        return new LocalUserJpaEntity(
            domain.getUserId().value(),
            domain.getUsername(),
            domain.getPasswordHash(),
            domain.getEmail(),
            formatRoles(domain.getRoles()),
            domain.getCreatedAt()
        );
    }

    private List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of("USER");
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String formatRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "USER";
        }
        return String.join(",", roles);
    }
}
