package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * DTO (Data Transfer Object) che rappresenta la risposta restituita dal servizio di autenticazione
 * a seguito di un accesso avvenuto con successo. Contiene il token di sessione, l'identificativo
 * dell'utente autenticato e l'istante di scadenza del token.
 *
 * @see com.gameplatform.shared.dto.LoginRequestDto
 */
public record LoginResponseDto(
    String token,
    String userId,
    Instant expiresAt
) {}
