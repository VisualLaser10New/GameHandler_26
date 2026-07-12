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
 * Read use case (PIANO §7.B, deviation D1): returns a directory
 * projection of every locally replicated user ({@code replicated_users}),
 * excluding the {@code hashedPassword} field. Mirrors
 * {@code getCurrentUser}'s pattern of reading the User domain object via
 * {@link UserRepository}.
 */
@Service
@Transactional(readOnly = true)
public class ListUsersDirectoryService implements ListUsersDirectoryUseCase {

    private final UserRepository userRepository;

    public ListUsersDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<UsersDirectoryDto> listAllUsers() {
        return userRepository.findAllReplicated().stream()
                .map(ListUsersDirectoryService::toDto)
                .collect(Collectors.toList());
    }

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