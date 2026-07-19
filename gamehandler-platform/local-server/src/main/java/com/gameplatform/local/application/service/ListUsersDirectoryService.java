package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.in.ListUsersDirectoryUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.dto.UsersDirectoryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Caso d'uso in lettura (PIANO §7.B, deviazione D1): restituisce una
 * proiezione di directory di tutti gli utenti replicati localmente
 * ({@code replicated_users}), escludendo il campo {@code hashedPassword}.
 *
 * @see ListUsersDirectoryUseCase
 * @see UserRepository
 */
@Service
@Transactional(readOnly = true)
public class ListUsersDirectoryService implements ListUsersDirectoryUseCase {

    private final UserRepository userRepository;

    /**
     * Costruisce il servizio con il repository degli utenti.
     *
     * @param userRepository il repository per l'accesso agli utenti replicati (non null)
     */
    public ListUsersDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Restituisce la directory completa di tutti gli utenti replicati
     * localmente, escludendo il campo password hashata.
     *
     * @return la lista dei DTO degli utenti
     */
    @Override
    public List<UsersDirectoryDto> listAllUsers() {
        return userRepository.findAllReplicated().stream()
                .map(ListUsersDirectoryService::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Converte un {@link User} nel corrispondente {@link UsersDirectoryDto}.
     * Il campo password hashata viene escluso dalla proiezione.
     *
     * @param user l'utente dal modello di dominio (non null)
     * @return il DTO con userId, username, email, roles e updatedAt
     */
    private static UsersDirectoryDto toDto(User user) {
        return new UsersDirectoryDto(
                user.getUserId().value(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles(),
                user.getUpdatedAt()
        );
    }
}