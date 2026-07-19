package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO (Data Transfer Object) che rappresenta i dati di sincronizzazione di un utente
 * all'interno della piattaforma di gioco. Contiene le informazioni anagrafiche e di
 * autenticazione necessarie per propagare lo stato di un utente tra i vari servizi.
 *
 * <p>I componenti del record descrivono l'identità dell'utente, le sue credenziali,
 * i ruoli associati e i metadati di tracciamento dell'evento di sincronizzazione.</p>
 *
 * @see com.gameplatform.shared.dto
 */
public record UserSyncDto(
    /**
     * Identificatore univoco dell'utente all'interno della piattaforma.
     *
     * @return l'identificativo dell'utente
     */
    String userId,

    /**
     * Nome utente (username) scelto dall'utente per il proprio account.
     *
     * @return il nome utente
     */
    String username,

    /**
     * Indirizzo email dell'utente. Può essere {@code null} nei casi in cui non sia
     * disponibile o richiesto.
     *
     * @return l'email dell'utente, oppure {@code null}
     */
    String email,

    /**
     * Password dell'utente in formato hash. Non contiene mai la password in chiaro.
     *
     * @return la password hashata dell'utente
     */
    String hashedPassword,

    /**
     * Elenco dei ruoli associati all'utente (ad esempio ruoli di autorizzazione).
     *
     * @return la lista dei ruoli dell'utente
     */
    List<String> roles,

    /**
     * Istante temporale in cui si è verificato l'evento di sincronizzazione.
     * Può essere {@code null} quando non specificato.
     *
     * @return l'istante dell'evento, oppure {@code null}
     */
    Instant occurredAt,

    /**
     * Identificativo della richiesta originaria che ha generato la sincronizzazione.
     * Utile per il tracciamento distribuito. Può essere {@code null}.
     *
     * @return l'identificativo della richiesta originaria, oppure {@code null}
     */
    String originatingRequestId
) {
    /**
     * Costruttore di convenienza che crea un DTO di sincronizzazione utente senza
     * specificare email, istante dell'evento e identificativo della richiesta
     * originaria, che vengono impostati a {@code null}.
     *
     * @param userId        identificatore univoco dell'utente
     * @param username      nome utente dell'account
     * @param hashedPassword password dell'utente in formato hash
     * @param roles         elenco dei ruoli associati all'utente
     */
    public UserSyncDto(String userId, String username, String hashedPassword, List<String> roles) {
        this(userId, username, null, hashedPassword, roles, null, null);
    }

    /**
     * Costruttore di convenienza che crea un DTO di sincronizzazione utente specificando
     * l'istante dell'evento, ma senza identificativo della richiesta originaria, che
     * viene impostato a {@code null}.
     *
     * @param userId        identificatore univoco dell'utente
     * @param username      nome utente dell'account
     * @param email         indirizzo email dell'utente
     * @param hashedPassword password dell'utente in formato hash
     * @param roles         elenco dei ruoli associati all'utente
     * @param occurredAt    istante in cui si è verificato l'evento di sincronizzazione
     */
    public UserSyncDto(String userId, String username, String email, String hashedPassword,
                       List<String> roles, Instant occurredAt) {
        this(userId, username, email, hashedPassword, roles, occurredAt, null);
    }
}