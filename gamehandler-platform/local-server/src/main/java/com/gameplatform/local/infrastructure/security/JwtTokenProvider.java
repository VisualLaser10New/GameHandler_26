package com.gameplatform.local.infrastructure.security;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.TokenGeneratorPort;
import com.gameplatform.shared.domain.security.TokenWithExpiry;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Implementazione di {@link TokenGeneratorPort} per la generazione di
 * token JWT firmati con chiave privata RSA (algoritmo RS256).
 *
 * <p>Genera token con una durata di 1 ora contenenti il nome utente,
 * l'identificativo dell'utente e i ruoli come claims. Utilizza un
 * {@link java.time.Clock} iniettato per garantire la determinazione
 * del timestamp corrente nei test e in produzione.</p>
 *
 * @see JwtTokenValidator
 * @see com.gameplatform.local.domain.ports.out.TokenGeneratorPort
 */
public class JwtTokenProvider implements TokenGeneratorPort {

    private final PrivateKey privateKey;
    private final Clock clock;

    /**
     * Costruisce un nuovo provider con la chiave privata e l'orologio
     * specificati.
     *
     * @param privateKey chiave privata per la firma dei token JWT
     * @param clock      orologio per la determinazione del timestamp
     *                   corrente
     */
    public JwtTokenProvider(PrivateKey privateKey, Clock clock) {
        this.privateKey = privateKey;
        this.clock = clock;
    }

    /**
     * Costruisce un nuovo provider con la chiave privata specificata e
     * l'orologio di sistema UTC come predefinito.
     *
     * @param privateKey chiave privata per la firma dei token JWT
     */
    public JwtTokenProvider(PrivateKey privateKey) {
        this(privateKey, Clock.systemUTC());
    }

    /**
     * Genera un token JWT per l'utente specificato al timestamp indicato.
     *
     * <p>Metodo deprecato: si preferisce
     * {@link #generateTokenWithExpiry(User, Instant)} che restituisce
     * anche la data di scadenza.</p>
     *
     * @param user utente per cui generare il token
     * @param now  timestamp corrente per l'emissione
     * @return token JWT come stringa
     * @deprecated dalla versione B11, sostituito da
     *             {@link #generateTokenWithExpiry(User, Instant)}
     */
    @Override
    @Deprecated(since = "B11", forRemoval = true)
    public String generateToken(User user, Instant now) {
        return generateTokenWithExpiry(user, now).token();
    }

    /**
     * Genera un token JWT per l'utente specificato con scadenza a 1 ora
     * dal timestamp indicato.
     *
     * <p>Il token contiene i seguenti claims:
     * <ul>
     *   <li><em>subject</em>: il nome utente</li>
     *   <li>{@code userId}: l'identificativo univoco dell'utente</li>
     *   <li>{@code roles}: la lista dei ruoli dell'utente</li>
     * </ul>
     * Il token è firmato con l'algoritmo RS256 utilizzando la chiave
     * privata configurata.</p>
     *
     * @param user utente per cui generare il token
     * @param now  timestamp corrente per l'emissione
     * @return contenitore con il token JWT e la data di scadenza
     */
    @Override
    public TokenWithExpiry generateTokenWithExpiry(User user, Instant now) {
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        String token = Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getUserId().value())
                .claim("roles", user.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        return new TokenWithExpiry(token, expiresAt);
    }

    /**
     * Genera un token JWT per l'utente specificato utilizzando l'orologio
     * configurato per il timestamp corrente.
     *
     * @param user utente per cui generare il token
     * @return token JWT come stringa
     */
    public String generateToken(User user) {
        return generateToken(user, Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
    }
}

