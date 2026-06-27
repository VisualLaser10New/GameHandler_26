package com.gameplatform.shared.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for user authentication.
 * Both fields are required to be non-blank.
 */
public record LoginRequestDto(
    @NotBlank(message = "Username must not be blank")
    String username,

    @NotBlank(message = "Password must not be blank")
    String password
) {}
