package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.domain.ports.out.LocalSignupUserRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalUserJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.LocalUserMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.LocalUserJpaRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.UserJpaRepository;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalSignupUserRepositoryAdapterEdgeCaseTest {

    @Mock
    private LocalUserJpaRepository jpaRepository;

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private LocalUserMapper mapper;

    private LocalSignupUserRepository adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalSignupUserRepositoryAdapter(jpaRepository, userJpaRepository, mapper);
    }

    @Test
    @DisplayName("existsByUsername returns true when only the replicated users table contains the username")
    void existsByUsernameDetectsReplicatedUser() {
        when(jpaRepository.existsByUsername("alice")).thenReturn(false);
        when(userJpaRepository.findByUsername("alice")).thenReturn(Optional.of(new UserJpaEntity()));

        assertThat(adapter.existsByUsername("alice")).isTrue();
    }

    @Test
    @DisplayName("existsByUsername returns false when neither table contains the username")
    void existsByUsernameFalseWhenAbsentEverywhere() {
        when(jpaRepository.existsByUsername("alice")).thenReturn(false);
        when(userJpaRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThat(adapter.existsByUsername("alice")).isFalse();
    }

    @Test
    @DisplayName("existsByUsername short-circuits on local hit without consulting the replicated table")
    void existsByUsernameShortCircuitsOnLocalHit() {
        when(jpaRepository.existsByUsername("alice")).thenReturn(true);

        assertThat(adapter.existsByUsername("alice")).isTrue();
        verifyNoInteractions(userJpaRepository);
    }

    @Test
    @DisplayName("EDGE-L2: existsByEmail does NOT consult the replicated users table (asymmetric with existsByUsername)")
    void existsByEmailDoesNotCheckReplicatedUsers() {
        when(jpaRepository.existsByEmail("a@example.com")).thenReturn(false);

        assertThat(adapter.existsByEmail("a@example.com")).isFalse();
        verifyNoInteractions(userJpaRepository);
    }

    @Test
    @DisplayName("existsByEmail returns false for null email (defensive guard, inconsistent with existsByUsername which has no guard)")
    void existsByEmailReturnsFalseForNull() {
        assertThat(adapter.existsByEmail(null)).isFalse();
        verifyNoInteractions(jpaRepository);
    }

    @Test
    @DisplayName("existsByEmail returns false for blank email")
    void existsByEmailReturnsFalseForBlank() {
        assertThat(adapter.existsByEmail("   ")).isFalse();
        verifyNoInteractions(jpaRepository);
    }

    @Test
    @DisplayName("save maps domain->entity, persists, then maps back to domain")
    void saveDelegatesAndMaps() {
        LocalSignupUser domain = new LocalSignupUser(
                new UserId("u-1"), "alice", "hash", "a@example.com", List.of("USER"), Instant.now());
        LocalUserJpaEntity entity = new LocalUserJpaEntity();
        LocalUserJpaEntity savedEntity = new LocalUserJpaEntity(
                "u-1", "alice", "hash", "a@example.com", "USER", Instant.now());

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpaRepository.save(entity)).thenReturn(savedEntity);
        when(mapper.toDomain(savedEntity)).thenReturn(domain);

        assertThat(adapter.save(domain)).isSameAs(domain);
        verify(jpaRepository).save(entity);
    }
}
