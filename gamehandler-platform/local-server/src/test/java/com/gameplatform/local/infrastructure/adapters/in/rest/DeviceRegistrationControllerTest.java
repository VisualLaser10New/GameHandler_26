package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.GameJpaRepository;
import com.gameplatform.local.infrastructure.security.LocalAdminBuildingAuthorizationManager;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

/**
 * Slice test for {@link DeviceRegistrationController} covering the LOCAL_ADMIN
 * + building enforcement applied in VIOL-2 (PIANO: "spetta a LOCAL_ADMIN
 * configurare i dispositivi"). Follows the local {@code standaloneSetup} +
 * Mockito convention; Spring Security method security
 * ({@code @PreAuthorize("hasRole('LOCAL_ADMIN')")} at class level) is bypassed
 * by design in standaloneSetup (mirrored from
 * {@code GameAdminControllerTest}/{@code AdminRequestsControllerTest}): the
 * {@link LocalAdminBuildingAuthorizationManager} mock fully controls the
 * building authorization, and the game-vs-building match is asserted via the
 * {@link GameJpaRepository} mock.
 *
 * <p>The full CSR-signing happy path (200 with certificate + caCertificate in
 * body) is intentionally NOT covered here: it requires real {@code ca.crt}/
 * {@code ca.key} resources (or deep mocking of the BouncyCastle pipeline)
 * which are not present in the test classpath. The policy gates (403 admin not
 * authorized, 403 game not in catalog, 403 game not in admin's building) are
 * the regression-relevant contract of this patch.</p>
 */
@ExtendWith(MockitoExtension.class)
class DeviceRegistrationControllerTest {

    private static final String BUILDING_ID = "building-1";

    @Mock private GameJpaRepository gameRepository;
    @Mock private ResourceLoader resourceLoader;
    @Mock private LocalAdminBuildingAuthorizationManager authorizationManager;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new DeviceRegistrationController(gameRepository, resourceLoader,
                                authorizationManager, BUILDING_ID))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static GameJpaEntity gameIn(String gameId, String buildingId) {
        return new GameJpaEntity(gameId, "CHESS", "Chess Table 1", buildingId, GameMachineStatus.AVAILABLE);
    }

    @Test
    void register_403_whenAdminNotAuthorizedForBuilding() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(false);

        mvc.perform(post("/api/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"g1\",\"csr\":\"...\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(gameRepository);
    }

    @Test
    void register_403_whenGameNotFoundInCatalog() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        when(gameRepository.findById("g-missing")).thenReturn(Optional.empty());

        mvc.perform(post("/api/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"g-missing\",\"csr\":\"...\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_403_whenGameBelongsToAnotherBuilding() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        when(gameRepository.findById("g-other")).thenReturn(
                Optional.of(gameIn("g-other", "building-2")));

        mvc.perform(post("/api/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"g-other\",\"csr\":\"...\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_400_whenGameIdMissingAfterAuthorizationOk() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);

        mvc.perform(post("/api/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"csr\":\"...\"}"))
                .andExpect(status().isBadRequest());
    }
}
