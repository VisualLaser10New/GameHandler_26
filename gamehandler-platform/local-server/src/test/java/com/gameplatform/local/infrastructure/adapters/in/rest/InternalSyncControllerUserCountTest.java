package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gameplatform.local.domain.ports.in.SyncUsersUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * M4 — focused Mockito unit test for the new
 * {@code GET /internal/users/count} endpoint exposed by
 * {@link InternalSyncController}.
 *
 * <p>Asserts:</p>
 * <ul>
 *   <li>200 OK with a bare JSON number body;</li>
 *   <li>the number comes from {@link UserRepository#count()} (the port), not
 *       from a direct JPA call (the controller must depend on the port only
 *       — hexagonal rule);</li>
 *   <li>0 → {@code 0} (boundary).</li>
 * </ul>
 *
 * <p>The {@code X-Internal-Api-Key} header is NOT asserted here — the
 * {@code InternalApiKeyFilter} enforces it at the filter layer (covers
 * {@code /internal/**}); this MockMvc test uses {@code standaloneSetup} which
 * deliberately bypasses the Spring filter chain, so the api-key contract is
 * verified separately at the integration level. See the existing
 * {@link InternalSyncControllerTest#apiKeyHeaderIsNotEnforcedAtControllerLevel}
 * for the established convention that api-key enforcement is a filter concern,
 * not a controller concern.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalSyncController GET /internal/users/count")
class InternalSyncControllerUserCountTest {

    @Mock private SyncUsersUseCase syncUseCase;
    @Mock private UserRepository userRepository;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalSyncController(syncUseCase, userRepository)).build();
    }

    @Test
    @DisplayName("returns 200 with the replicated_users count as a bare JSON number")
    void returnsCountFromUserRepositoryPort() throws Exception {
        when(userRepository.count()).thenReturn(7L);

        mvc.perform(get("/internal/users/count")
                        .header("X-Internal-Api-Key", "k")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").value(7));
    }

    @Test
    @DisplayName("returns 0 when the replicated_users table is empty (boundary)")
    void returnsZeroWhenEmpty() throws Exception {
        when(userRepository.count()).thenReturn(0L);

        mvc.perform(get("/internal/users/count")
                        .header("X-Internal-Api-Key", "k"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0));
    }
}