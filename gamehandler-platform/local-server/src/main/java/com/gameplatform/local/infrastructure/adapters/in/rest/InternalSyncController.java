package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.domain.ports.in.SyncUsersUseCase;
import com.gameplatform.shared.dto.UserSyncDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/users")
public class InternalSyncController {

    private final SyncUsersUseCase syncUsersUseCase;

    public InternalSyncController(SyncUsersUseCase syncUsersUseCase) {
        this.syncUsersUseCase = syncUsersUseCase;
    }

    @PutMapping("/sync")
    public ResponseEntity<Void> syncUsers(
            @RequestBody List<UserSyncDto> users,
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        syncUsersUseCase.syncUsers(users);
        return ResponseEntity.ok().build();
    }
}
