package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.ports.in.AssignLocalAdminBuildingsUseCase;
import com.gameplatform.central.domain.ports.in.GetLocalAdminBuildingsUseCase;
import com.gameplatform.shared.dto.AssignLocalAdminBuildingsDto;
import com.gameplatform.shared.dto.LocalAdminBuildingsDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the LOCAL_ADMIN &harr; building binding (central
 * Source-of-Truth). Per decision A3, this is a PLATFORM_ADMIN-only surface in
 * FASE 1 — no LOCAL_ADMIN calls central here, and the JWT carries no
 * {@code buildings} claim.
 *
 * <p>Exception-to-HTTP-status mapping is delegated to {@link GlobalExceptionHandler}:
 * <ul>
 *   <li>{@code UserNotFoundException} → 404 Not Found</li>
 *   <li>{@code MethodArgumentNotValidException} → 400 Bad Request</li>
 *   <li>{@code IllegalArgumentException} → 400 Bad Request</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/admin/local")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class LocalAdminController {

    private final AssignLocalAdminBuildingsUseCase assignUseCase;
    private final GetLocalAdminBuildingsUseCase getUseCase;

    public LocalAdminController(AssignLocalAdminBuildingsUseCase assignUseCase,
                                GetLocalAdminBuildingsUseCase getUseCase) {
        this.assignUseCase = assignUseCase;
        this.getUseCase = getUseCase;
    }

    @PostMapping("/buildings")
    public ResponseEntity<Void> assignBuildings(@Valid @RequestBody AssignLocalAdminBuildingsDto request) {
        assignUseCase.assignBuildings(request.userId(), request.buildingIds());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/buildings")
    public ResponseEntity<Void> revokeBuildings(@Valid @RequestBody AssignLocalAdminBuildingsDto request) {
        assignUseCase.revokeBuildings(request.userId(), request.buildingIds());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/buildings")
    public ResponseEntity<LocalAdminBuildingsDto> getBuildings(@RequestParam("userId") String userId) {
        return ResponseEntity.ok(new LocalAdminBuildingsDto(userId, getUseCase.getBuildingsForUser(userId)));
    }
}