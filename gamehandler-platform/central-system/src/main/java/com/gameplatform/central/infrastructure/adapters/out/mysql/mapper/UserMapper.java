package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper senza stato (null-safe) tra il modello di dominio centrale {@link User}
 * e l'entità persistente {@link UserJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, si occupa della conversione
 * della lista di ruoli da/verso una rappresentazione CSV nella colonna
 * {@code roles} dell'entità.
 *
 * @see User
 * @see UserJpaEntity
 */
@Component
public class UserMapper {

    /**
     * Converte un'entità persistente {@link UserJpaEntity} nel corrispondente
     * modello di dominio {@link User}.
     * <p>
     * La colonna {@code roles} viene suddivisa in una lista di stringhe separandola
     * tramite virgola; se {@code null} o vuota restituisce una lista vuota.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link User} o {@code null} se l'entità è {@code null}
     * @see #toEntity(User)
     */
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

    /**
     * Converte un modello di dominio {@link User} nell'entità persistente
     * {@link UserJpaEntity} da persistere.
     * <p>
     * La lista dei ruoli viene concatenata in una stringa CSV.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link UserJpaEntity} o {@code null} se il dominio è {@code null}
     * @see #toDomain(UserJpaEntity)
     */
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
