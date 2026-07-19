package com.gameplatform.shared.domain.security;

import java.time.Instant;

/**
 * Contenitore immutabile che associa la stringa compatta del JWT al relativo
 * claim {@code exp} incapsulato, offrendo una singola fonte di verità per la
 * scadenza del token.
 *
 * <p>Collodato nel modulo {@code shared-domain} affinché sia il
 * {@code TokenProviderPort} del {@code central-system} sia il
 * {@code TokenGeneratorPort} del {@code local-server} possano condividere il
 * medesimo tipo di ritorno senza duplicarne la definizione.</p>
 *
 * @param token     stringa JWT in forma compatta; non deve essere {@code null} né vuota
 * @param expiresAt istante di scadenza ({@code exp}) incapsulato nel JWT; non deve essere {@code null}
 *
 * @see com.gameplatform.shared.domain.security.TokenProviderPort
 * @see com.gameplatform.shared.domain.security.TokenGeneratorPort
 */
public record TokenWithExpiry(String token, Instant expiresAt) {
    /**
     * Costruisce una nuova istanza di {@code TokenWithExpiry} validando i componenti
     * forniti.
     *
     * <p>Verifica che il token non sia {@code null} né vuoto e che l'istante di
     * scadenza non sia {@code null}, garantendo l'invarianza del record.</p>
     *
     * @param token     stringa JWT in forma compatta; se {@code null} o vuota viene
     *                  lanciata un'eccezione
     * @param expiresAt istante di scadenza incapsulato nel JWT; se {@code null}
     *                  viene lanciata un'eccezione
     *
     * @throws IllegalArgumentException se {@code token} è {@code null} o vuoto, oppure
     *                                  se {@code expiresAt} è {@code null}
     */
    public TokenWithExpiry {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token cannot be null or blank");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt cannot be null");
        }
    }
}
