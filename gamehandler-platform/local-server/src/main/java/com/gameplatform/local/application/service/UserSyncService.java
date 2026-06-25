package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.in.SyncUsersUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserSyncDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class UserSyncService implements SyncUsersUseCase {

    private final UserRepository userRepository;

    public UserSyncService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void syncUsers(List<UserSyncDto> users) {
        if (users == null || users.isEmpty()) {
            return;
        }

        List<User> domainUsers = users.stream()
                .map(dto -> new User(
                        new UserId(dto.userId()),
                        dto.username(),
                        dto.hashedPassword(),
                        dto.roles(),
                        Instant.now() // sync timestamp
                ))
                .toList();

        userRepository.saveAll(domainUsers);
    }
}
