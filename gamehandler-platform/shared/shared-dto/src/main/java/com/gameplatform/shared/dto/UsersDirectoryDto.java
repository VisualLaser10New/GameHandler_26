package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Proiezione in sola lettura di un utente destinata alla directory degli utenti
 * esposta dall'endpoint locale {@code GET /api/admin/users}, riservato al ruolo
 * PLATFORM_ADMIN. I dati sono ricavati dalla replica {@code replicated_users};
 * la {@code hashedPassword} non viene volutamente esposta in questa rappresentazione.
 *
 * <p>Questo record funge da DTO (Data Transfer Object) per il trasferimento sicuro
 * delle informazioni essenziali di un utente verso i client amministrativi, garantendo
 * che dati sensibili non transitino sulla rete.</p>
 *
 * @param userId     l'identificativo univoco dell'utente
 * @param username   il nome utente utilizzato per il login
 * @param email      l'indirizzo email dell'utente (può essere {@code null})
 * @param roles      l'elenco dei ruoli associati all'utente
 * @param updatedAt  l'istante dell'ultima mutazione dei dati utente
 *
 * @see com.gameplatform.shared.dto
 */
public record UsersDirectoryDto(
        String userId,
        String username,
        String email,
        List<String> roles,
        Instant updatedAt
) {
}
