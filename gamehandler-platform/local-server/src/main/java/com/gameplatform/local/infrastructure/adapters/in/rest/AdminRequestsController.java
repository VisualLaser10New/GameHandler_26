package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.ports.in.ListAdminRequestsUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Self-service admin-request poll endpoints (PIANO §7.B): an authenticated
 * client reads its own admin-request rows (filter by {@code actingUserId
 * == principal}) so it can poll the eventual COMPLETED / FAILED state of
 * the async {@code *_REQUESTED} flows (W6/W9/W10/W12). The
 * {@code actingUserId == principal} cross-user read filter is enforced
 * here; only requests owned by the authenticated principal are returned.
 *
 * <p>Spec contract: {@code isAuthenticated()} (no class-level
 * {@code @PreAuthorize}); method security is enforced by the
 * {@code currentUserService} role mapping and the explicit
 * {@code actingUserId == principal} filter.</p>
 */
@RestController
@RequestMapping("/api/admin/requests")
public class AdminRequestsController {

    private final ListAdminRequestsUseCase listAdminRequestsUseCase;
    private final CurrentUserService currentUserService;

    public AdminRequestsController(ListAdminRequestsUseCase listAdminRequestsUseCase,
                                    CurrentUserService currentUserService) {
        this.listAdminRequestsUseCase = listAdminRequestsUseCase;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<AdminRequestDto>> listMyRequests() {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(listAdminRequestsUseCase.listByActingUser(currentUserId.get().value()));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<AdminRequestDto> getMyRequest(@PathVariable String requestId) {
        Optional<UserId> currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return listAdminRequestsUseCase.findByRequestId(requestId)
                .filter(dto -> currentUserId.get().value().equals(dto.actingUserId()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}