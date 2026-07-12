package com.gameplatform.shared.dto;

import java.util.List;

public record TeamDto(
        String id,
        String name,
        List<String> members
) {
}
