package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.UsersDirectoryDto;

import java.util.List;

/**
 * Use case (PIANO §7.B, deviation D1): returns a directory projection of
 * every locally replicated user ({@code replicated_users}), excluding the
 * {@code hashedPassword} field. Used by the {@code GET /api/admin/users}
 * PLATFORM_ADMIN endpoint.
 */
public interface ListUsersDirectoryUseCase {

    List<UsersDirectoryDto> listAllUsers();
}