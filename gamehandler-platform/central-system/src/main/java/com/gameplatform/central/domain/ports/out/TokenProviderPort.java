package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.shared.domain.security.TokenWithExpiry;
import java.time.Instant;

/**
 * Porta per la generazione dei token di autenticazione.
 *
 * <p>Astrae i dettagli di sicurezza e di firma dei token dal livello
 * applicativo di dominio/servizio, esponendo sia la generazione del token
 * compatta sia quella corredata della scadenza embedded.</p>
 *
 * @see User
 * @see TokenWithExpiry
 */
public interface TokenProviderPort {

    /**
     * Genera un token firmato per l'utente autenticato, a partire dal timestamp indicato.
     *
     * <p><strong>Deprecato dalla versione B11.</strong> Utilizzare
     * {@link #generateTokenWithExpiry(User, Instant)} affinché il chiamante
     * riceva l'esatto claim {@code exp} embedded nel JWT, eliminando la deriva
     * tra il token e lo {@code expiresAt} pubblicizzato nella risposta di login
     * (singola fonte di verità).</p>
     *
     * @param user l'utente autenticato; non deve essere {@code null}
     * @param now  il timestamp di inizio validità; non deve essere {@code null}
     * @return la stringa del token generato
     * @throws IllegalArgumentException se {@code user} o {@code now} sono {@code null}
     * @deprecated dalla versione B11, usare {@link #generateTokenWithExpiry(User, Instant)}
     */
    @Deprecated(since = "B11", forRemoval = true)
    String generateToken(User user, Instant now);

    /**
     * Genera un token firmato per l'utente autenticato, a partire dal timestamp
     * indicato, restituendo sia la stringa JWT compatta sia il claim {@code exp}
     * in essa embedded (singola fonte di verità per la scadenza del token).
     *
     * @param user l'utente autenticato; non deve essere {@code null}
     * @param now  il timestamp di inizio validità; non deve essere {@code null}
     * @return un {@link TokenWithExpiry} contenente il JWT compatto e il suo claim {@code exp}
     * @throws IllegalArgumentException se {@code user} o {@code now} sono {@code null}
     * @see #generateToken(User, Instant)
     */
    TokenWithExpiry generateTokenWithExpiry(User user, Instant now);

    /**
     * Restituisce la durata di validità del token configurata sul provider, in millisecondi.
     *
     * @return la durata di vita del token, in millisecondi, sempre positiva
     */
    long getTokenExpirationMs();
}
