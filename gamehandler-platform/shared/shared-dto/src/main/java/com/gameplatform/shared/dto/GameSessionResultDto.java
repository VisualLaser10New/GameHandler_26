package com.gameplatform.shared.dto;

import java.util.List;

public record GameSessionResultDto(
    GameSessionDto session,
    List<String> participants
) {}
