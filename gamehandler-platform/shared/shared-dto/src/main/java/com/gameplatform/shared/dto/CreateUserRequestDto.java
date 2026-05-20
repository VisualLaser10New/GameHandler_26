package com.gameplatform.shared.dto;

public record CreateUserRequestDto(
    String username,
    String password,
    String email
) {}
