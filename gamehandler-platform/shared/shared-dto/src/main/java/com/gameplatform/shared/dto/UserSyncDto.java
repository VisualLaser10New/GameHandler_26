package com.gameplatform.shared.dto;

import java.util.List;

public record UserSyncDto(
    String userId,
    String username,
    String hashedPassword,
    List<String> roles
) {}
