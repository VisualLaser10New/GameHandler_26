package com.gameplatform.shared.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO di richiesta utilizzato per la registrazione di un nuovo utente nella piattaforma.
 * Trasporta i dati anagrafici necessari alla creazione dell'account e viene validato
 * tramite Bean Validation prima di raggiungere il caso d'uso di registrazione.
 *
 * @see com.gameplatform.shared.dto.UserResponseDto
 */
public record CreateUserRequestDto(
    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    String username,

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    String password,

    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid e-mail address")
    String email
) {}
