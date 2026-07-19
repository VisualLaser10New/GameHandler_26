package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO che rappresenta l'evento di registrazione di un nuovo utente all'interno della piattaforma.
 * Contiene i dati essenziali dell'utente al momento della creazione dell'account e viene utilizzato
 * per propagare l'informazione di registrazione tra i diversi servizi del sistema.
 *
 * @see com.gameplatform.shared.dto
 */
public record UserRegisteredEventDto(
    /**
     * Identificativo univoco dell'utente generato al momento della registrazione.
     *
     * @return l'identificatore univoco dell'utente.
     */
    String userId,

    /**
     * Nome utente scelto dall'utente durante la registrazione.
     *
     * @return il nome utente associato all'account.
     */
    String username,

    /**
     * Indirizzo email dell'utente, utilizzato per il contatto e l'identificazione.
     *
     * @return l'email dell'utente.
     */
    String email,

    /**
     * Password dell'utente memorizzata in forma cifrata (hash).
     * Non contiene mai la password in chiaro per garantire la sicurezza dei dati.
     *
     * @return la password cifrata dell'utente.
     */
    String hashedPassword,

    /**
     * Elenco dei ruoli assegnati all'utente al momento della registrazione.
     * Definisce i permessi e le autorizzazioni disponibili per l'account.
     *
     * @return la lista dei ruoli associati all'utente.
     */
    List<String> roles,

    /**
     * Istant4 temporale in cui è avvenuta la registrazione dell'utente.
     *
     * @return l'istante di creazione dell'account.
     */
    Instant createdAt
) {
    /**
     * Crea una nuova istanza di {@code UserRegisteredEventDto} contenente i dati relativi
     * alla registrazione di un utente.
     *
     * @param userId         identificativo univoco dell'utente.
     * @param username       nome utente scelto dall'utente.
     * @param email          indirizzo email dell'utente.
     * @param hashedPassword password dell'utente in forma cifrata.
     * @param roles          elenco dei ruoli assegnati all'utente.
     * @param createdAt      istante temporale della registrazione.
     */
}
