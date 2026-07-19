package com.gameplatform.shared.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO di richiesta utilizzato per autenticare un utente verso la piattaforma.
 * Trasporta le credenziali necessarie all'accesso e richiede che entrambi i campi
 * siano valorizzati con stringhe non vuote e non costituite esclusivamente da spazi.
 *
 * @see com.gameplatform.shared.dto.LoginResponseDto
 */
public record LoginRequestDto(
    /**
     * Restituisce il nome utente fornito per l'autenticazione.
     * Il valore non è {@code null} e non è una stringa vuota o composta solo da spazi,
     * in quanto il campo è vincolato da {@link NotBlank}.
     *
     * @return il nome utente associato alla richiesta di login
     */
    @NotBlank(message = "Username must not be blank")
    String username,

    /**
     * Restituisce la password fornita per l'autenticazione.
     * Il valore non è {@code null} e non è una stringa vuota o composta solo da spazi,
     * in quanto il campo è vincolato da {@link NotBlank}.
     *
     * @return la password associata alla richiesta di login
     */
    @NotBlank(message = "Password must not be blank")
    String password
) {}
