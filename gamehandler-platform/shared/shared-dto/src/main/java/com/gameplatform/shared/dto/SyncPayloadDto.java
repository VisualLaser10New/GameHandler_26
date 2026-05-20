package com.gameplatform.shared.dto;

import java.util.List;

public record SyncPayloadDto(
    String buildingId,
    List<OutboxEventDto> events
) {}
