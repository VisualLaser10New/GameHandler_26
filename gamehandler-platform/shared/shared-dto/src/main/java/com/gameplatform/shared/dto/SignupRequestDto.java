package com.gameplatform.shared.dto;

/**
 * DTO (Data Transfer Object) che incapsula i dati necessari per richiedere la
 * registrazione di un nuovo utente all'interno della piattaforma di gioco.
 *
 * <p>Trasporta le credenziali e il recapito forniti in fase di signup, utilizzati
 * dal servizio di autenticazione per creare un nuovo account.</p>
 *
 * @see com.gameplatform.shared.dto.AuthResponseDto
 */
public record SignupRequestDto(
    /**
     * Restituisce il nome utente scelto per il nuovo account.
     *
     * @return il nome utente; non è {@code null} e non è vuoto
     */
    String username,

    /**
     * Restituisce la password in chiaro associata al nuovo account.
     *
     * @return la password; non è {@code null} e non è vuota
     */
    String password,

    /**
     * Restituisce l'indirizzo email di contatto associato al nuovo account.
     *
     * @return l'indirizzo email; non è {@code null} e non è vuoto
     */
    String email
) {}
