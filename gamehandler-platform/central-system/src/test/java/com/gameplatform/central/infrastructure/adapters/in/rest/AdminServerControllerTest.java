package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.ServerHealthDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M12 — focused {@code @WebMvcTest} for {@link AdminServerController}.
 *
 * <p>Boots only the controller slice with {@link TestSecurityConfig} (which
 * disables Spring Security authentication and permits every request) plus
 * {@link GlobalExceptionHandler}. The {@link
 * com.gameplatform.central.infrastructure.security.InternalApiKeyFilter
 * InternalApiKeyFilter} is excluded from the component scan — the path-pattern
 * security contract is already exhaustively covered by
 * {@code InternalApiKeyFilterTest}; re-asserting it here under {@code @WebMvcTest}
 * would duplicate coverage and require loading the filter's
 * {@code internal.api-key} property. The happy-path body shape (DTO fields,
 * ordering, pending count wiring) is asserted here.</p>
 */
@WebMvcTest(
        controllers = AdminServerController.class,
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {
                        com.gameplatform.central.infrastructure.config.SecurityConfig.class,
                        com.gameplatform.central.infrastructure.security.JwtAuthenticationFilter.class,
                        com.gameplatform.central.infrastructure.security.InternalApiKeyFilter.class
                }
        )
)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
class AdminServerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalServerRegistryPort localServerRegistryPort;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @Test
    void listServers_returns200AndDtosOrderedByLastSeenAtDesc() throws Exception {
        Instant t1 = Instant.parse("2026-07-05T12:00:00Z");
        Instant t2 = Instant.parse("2026-07-05T11:00:00Z");

        RegisteredLocalServer activeRecent = new RegisteredLocalServer(
                new BuildingId("building-1"), "http://local-1:8081", t1, true);
        RegisteredLocalServer stale = new RegisteredLocalServer(
                new BuildingId("building-2"), "http://local-2:8081", t2, false);

        when(localServerRegistryPort.findAll()).thenReturn(List.of(activeRecent, stale));
        when(outboxEventRepository.countPendingReplicationForServer(eq("building-1")))
                .thenReturn(3L);
        when(outboxEventRepository.countPendingReplicationForServer(eq("building-2")))
                .thenReturn(0L);

        mockMvc.perform(get("/internal/servers").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buildingId").value("building-1"))
                .andExpect(jsonPath("$[0].baseUrl").value("http://local-1:8081"))
                .andExpect(jsonPath("$[0].lastSeenAt").value("2026-07-05T12:00:00Z"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].pendingReplicationCount").value(3))
                .andExpect(jsonPath("$[1].buildingId").value("building-2"))
                .andExpect(jsonPath("$[1].baseUrl").value("http://local-2:8081"))
                .andExpect(jsonPath("$[1].lastSeenAt").value("2026-07-05T11:00:00Z"))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].pendingReplicationCount").value(0));
    }

    @Test
    void listServers_returns200EmptyArrayWhenNoServersRegistered() throws Exception {
        when(localServerRegistryPort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/internal/servers").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listServers_exposesAllFiveDtoFields() throws Exception {
        // Sanity check that the ServerHealthDto record's 5 components are all
        // serialised to JSON under the documented field names (regression guard
        // against accidental rename).
        RegisteredLocalServer s = new RegisteredLocalServer(
                new BuildingId("b-x"), "http://x:8081", Instant.parse("2026-07-05T10:00:00Z"), true);
        when(localServerRegistryPort.findAll()).thenReturn(List.of(s));
        when(outboxEventRepository.countPendingReplicationForServer(eq("b-x"))).thenReturn(7L);

        mockMvc.perform(get("/internal/servers").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buildingId").exists())
                .andExpect(jsonPath("$[0].baseUrl").exists())
                .andExpect(jsonPath("$[0].lastSeenAt").exists())
                .andExpect(jsonPath("$[0].active").exists())
                .andExpect(jsonPath("$[0].pendingReplicationCount").exists());
    }
}
