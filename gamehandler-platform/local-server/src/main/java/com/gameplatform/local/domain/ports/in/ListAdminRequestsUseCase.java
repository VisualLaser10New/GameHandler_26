package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.AdminRequestDto;

import java.util.List;
import java.util.Optional;

/**
 * Use case (PIANO §7.B): returns the admin-request rows owned by the
 * given acting user, or a single one by {@code requestId}. The
 * {@code actingUserId == principal} filter is enforced by the
 * controller to prevent cross-user reads.
 */
public interface ListAdminRequestsUseCase {

    List<AdminRequestDto> listByActingUser(String actingUserId);

    Optional<AdminRequestDto> findByRequestId(String requestId);
}