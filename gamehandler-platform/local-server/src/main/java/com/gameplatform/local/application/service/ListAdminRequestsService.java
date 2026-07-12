package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.AdminRequestLocal;
import com.gameplatform.local.domain.ports.in.ListAdminRequestsUseCase;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.shared.dto.AdminRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read use case (PIANO §7.B): returns the admin-request rows owned by
 * the given acting user, or a single one by {@code requestId}. The
 * {@code actingUserId == principal} filter is enforced by the
 * controller to prevent cross-user reads.
 */
@Service
@Transactional(readOnly = true)
public class ListAdminRequestsService implements ListAdminRequestsUseCase {

    private final AdminRequestRepository adminRequestRepository;

    public ListAdminRequestsService(AdminRequestRepository adminRequestRepository) {
        this.adminRequestRepository = adminRequestRepository;
    }

    @Override
    public List<AdminRequestDto> listByActingUser(String actingUserId) {
        if (actingUserId == null || actingUserId.isBlank()) {
            return List.of();
        }
        return adminRequestRepository.findByActingUserId(actingUserId).stream()
                .map(ListAdminRequestsService::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<AdminRequestDto> findByRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return adminRequestRepository.findByRequestId(requestId).map(ListAdminRequestsService::toDto);
    }

    private static AdminRequestDto toDto(AdminRequestLocal request) {
        return new AdminRequestDto(
                request.getRequestId(),
                request.getEventType(),
                request.getActingUserId(),
                request.getActingRole(),
                request.getBuildingId(),
                request.getPayloadJson(),
                request.getStatus(),
                request.getResultDataJson(),
                request.getCreatedAt(),
                request.getCompletedAt(),
                request.getOutboxEventId()
        );
    }
}