package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserSyncDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserSyncService service;

    @Test
    void shouldSyncUsers() {
        List<UserSyncDto> dtos = List.of(
                new UserSyncDto("u-1", "alice", "hash1", List.of("PLAYER")),
                new UserSyncDto("u-2", "bob", "hash2", List.of("OPERATOR")));

        service.syncUsers(dtos);

        @SuppressWarnings("unchecked")
        Class<List<User>> listClass = (Class<List<User>>) (Class) List.class;
        verify(userRepository).saveAll(argThat(l -> l != null && l.size() == 2));
    }

    @Test
    void shouldDoNothingWhenNullList() {
        service.syncUsers(null);
        verify(userRepository, never()).saveAll(any());
    }

    @Test
    void shouldDoNothingWhenEmptyList() {
        service.syncUsers(List.of());
        verify(userRepository, never()).saveAll(any());
    }

    @Test
    void shouldFailSyncWhenDtoHasBlankUsername() {
        UserSyncDto bad = new UserSyncDto("u-1", "", "hash", List.of("PLAYER"));
        assertThrows(IllegalArgumentException.class, () -> service.syncUsers(List.of(bad)));
        verify(userRepository, never()).saveAll(any());
    }
}
