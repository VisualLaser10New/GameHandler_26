package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalUserJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Mapper null-safe tra il modello di dominio {@link User} e le entità
 * di persistenza {@link UserJpaEntity} e {@link LocalUserJpaEntity}.
 * Gestisce la conversione della lista dei ruoli da e verso una
 * rappresentazione stringa separata da virgole.
 */
@Component
public class UserMapper {

    /**
     * Converte un'entità JPA {@link UserJpaEntity} nel corrispondente
     * modello di dominio {@link User}. Trasforma la stringa dei ruoli
     * in una lista di stringhe.
     *
     * @param entity l'entità JPA da convertire, può essere {@code null}
     * @return il modello di dominio, oppure {@code null} se l'input è {@code null}
     */
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
            entity.getEmail(),
            rolesList,
            entity.getEventTime(),
            entity.getUpdatedAt()
        );
    }

    /**
     * Converte un modello di dominio {@link User} nella corrispondente
     * entità JPA {@link UserJpaEntity}. Trasforma la lista dei ruoli
     * in una stringa separata da virgole.
     *
     * @param domain il modello di dominio da convertire, può essere {@code null}
     * @return l'entità JPA, oppure {@code null} se l'input è {@code null}
     */
    public UserJpaEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }
        String rolesStr = domain.getRoles() != null ? String.join(",", domain.getRoles()) : "";
        return new UserJpaEntity(
            domain.getUserId().value(),
            domain.getUsername(),
            domain.getPasswordHash(),
            domain.getEmail(),
            rolesStr,
            domain.getEventTime(),
            domain.getUpdatedAt()
        );
    }

    /**
     * Converte un'entità {@link LocalUserJpaEntity} (utente registrato localmente)
     * nel modello di dominio {@link User}. Utilizza "USER" come ruolo predefinito
     * se la stringa dei ruoli è {@code null} o vuota.
     *
     * @param entity l'entità JPA da convertire, può essere {@code null}
     * @return il modello di dominio, oppure {@code null} se l'input è {@code null}
     */
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
