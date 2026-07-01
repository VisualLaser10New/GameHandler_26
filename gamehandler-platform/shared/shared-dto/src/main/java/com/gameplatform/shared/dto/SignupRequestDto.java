package com.gameplatform.shared.dto;

public record SignupRequestDto(
    String username,
    String password,
    String email
) {}
