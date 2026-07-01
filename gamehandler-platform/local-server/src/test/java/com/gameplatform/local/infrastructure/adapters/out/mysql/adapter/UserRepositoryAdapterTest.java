package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalUserJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.UserMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.LocalUserJpaRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.UserJpaRepository;
import com.gameplatform.shared.domain.model.UserId;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

    @Mock
    private UserJpaRepository jpaRepository;
    @Mock
    private LocalUserJpaRepository localUserJpaRepository;
    @Mock
    private UserMapper mapper;
    @InjectMocks
    private UserRepositoryAdapter adapter;

    private User sample() {
        return new User(new UserId("u-1"), "alice", "hash", List.of("PLAYER"), Instant.now());
    }

    @Test
    void saveDelegates() {
        UserJpaEntity entity = new UserJpaEntity();
        UserJpaEntity saved = new UserJpaEntity();
        User domain = sample();
        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpaRepository.save(entity)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(domain);
        assertThat(adapter.save(domain)).isSameAs(domain);
    }

    @Test
    void findByUsernameDelegates() {
        UserJpaEntity e = new UserJpaEntity();
        when(jpaRepository.findByUsername("alice")).thenReturn(Optional.of(e));
        when(mapper.toDomain(e)).thenReturn(sample());
        assertThat(adapter.findByUsername("alice")).isPresent();
    }

    @Test
    void findByUsernameFallsBackToLocalUsersWhenNotInReplicated() {
        UserJpaEntity replicated = null;
        LocalUserJpaEntity local = new LocalUserJpaEntity();
        User domain = sample();
        when(jpaRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(localUserJpaRepository.findByUsername("alice")).thenReturn(Optional.of(local));
        when(mapper.toDomainFromLocalUser(local)).thenReturn(domain);
        assertThat(adapter.findByUsername("alice")).isPresent().hasValue(domain);
        verifyNoMoreInteractions(localUserJpaRepository);
    }

    @Test
    void findByUsernamePrefersReplicatedUserOverLocalUser() {
        UserJpaEntity replicated = new UserJpaEntity();
        User replicatedDomain = sample();
        when(jpaRepository.findByUsername("alice")).thenReturn(Optional.of(replicated));
        when(mapper.toDomain(replicated)).thenReturn(replicatedDomain);
        assertThat(adapter.findByUsername("alice")).isPresent().hasValue(replicatedDomain);
        verifyNoInteractions(localUserJpaRepository);
    }

    @Test
    void findByUsernameReturnsEmptyWhenNotFoundInEitherSource() {
        when(jpaRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(localUserJpaRepository.findByUsername("alice")).thenReturn(Optional.empty());
        assertThat(adapter.findByUsername("alice")).isEmpty();
    }

    @Test
    void saveAllNullReturnsEarly() {
        adapter.saveAll(null);
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void saveAllEmptyReturnsEarly() {
        adapter.saveAll(List.of());
        verifyNoInteractions(jpaRepository);
    }

    @Test
    void saveAllDelegates() {
        User u = sample();
        UserJpaEntity e = new UserJpaEntity();
        when(mapper.toEntity(u)).thenReturn(e);
        adapter.saveAll(List.of(u));
        verify(jpaRepository).saveAll(List.of(e));
    }
}
