package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/sync")
public class SyncController {

    private final ReceiveSyncDataUseCase receiveSyncDataUseCase;

    public SyncController(ReceiveSyncDataUseCase receiveSyncDataUseCase) {
        this.receiveSyncDataUseCase = receiveSyncDataUseCase;
    }

    @PostMapping("/receive")
    public ResponseEntity<Void> receiveSync(@RequestBody SyncPayloadDto payload) {
        receiveSyncDataUseCase.receiveSyncPayload(payload);
        return ResponseEntity.ok().build();
    }
}

