package com.gameplatform.shared.dto;

public record SignupResponseDto(
    String userId,
    String username,
    String email
) {}
