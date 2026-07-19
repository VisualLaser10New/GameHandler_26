package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalUserJpaEntity;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Mapper null-safe tra il modello di dominio {@link LocalSignupUser} e l'entità
 * di persistenza {@link LocalUserJpaEntity}. Gestisce la conversione della
 * lista dei ruoli da e verso una rappresentazione stringa separata da virgole.
 */
@Component
public class LocalUserMapper {

    /**
     * Converte un'entità JPA {@link LocalUserJpaEntity} nel corrispondente
     * modello di dominio {@link LocalSignupUser}. Trasforma la stringa dei
     * ruoli in una lista di stringhe tramite {@link #parseRoles(String)}.
     *
     * @param entity l'entità JPA da convertire, può essere {@code null}
     * @return il modello di dominio, oppure {@code null} se l'input è {@code null}
     * @see #parseRoles(String)
     */
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

    /**
     * Converte un modello di dominio {@link LocalSignupUser} nella corrispondente
     * entità JPA {@link LocalUserJpaEntity}. Trasforma la lista dei ruoli in
     * una stringa separata da virgole tramite {@link #formatRoles(List)}.
     *
     * @param domain il modello di dominio da convertire, può essere {@code null}
     * @return l'entità JPA, oppure {@code null} se l'input è {@code null}
     * @see #formatRoles(List)
     */
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

    /**
     * Analizza una stringa di ruoli separata da virgole in una lista di stringhe.
     * Restituisce una lista contenente "USER" se l'input è {@code null} o vuoto.
     *
     * @param roles la stringa dei ruoli da analizzare
     * @return la lista dei ruoli
     */
    private List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of("USER");
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Formatta una lista di ruoli in una stringa separata da virgole.
     * Restituisce "USER" se la lista è {@code null} o vuota.
     *
     * @param roles la lista dei ruoli da formattare
     * @return la stringa dei ruoli
     */
    private String formatRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "USER";
        }
        return String.join(",", roles);
    }
}
