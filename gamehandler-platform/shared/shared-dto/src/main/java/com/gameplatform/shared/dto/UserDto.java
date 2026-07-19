package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO (Data Transfer Object) che rappresenta un utente della piattaforma.
 * <p>
 * Trasporta i dati essenziali di un utente tra i vari livelli e servizi del
 * sistema, incapsulando l'identificativo, le credenziali pubbliche, i ruoli
 * associati e la data di creazione dell'account.
 */
public record UserDto(
    /**
     * Identificativo univoco dell'utente all'interno del sistema.
     *
     * @return l'identificativo univoco dell'utente
     */
    String id,

    /**
     * Nome utente utilizzato per il login e la visualizzazione pubblica.
     *
     * @return il nome utente associato all'account
     */
    String username,

    /**
     * Indirizzo email dell'utente, utilizzato per le comunicazioni e il recupero credenziali.
     *
     * @return l'indirizzo email dell'utente
     */
    String email,

    /**
     * Elenco dei ruoli assegnati all'utente, che determinano i permessi e le
     * autorizzazioni disponibili sulla piattaforma.
     *
     * @return la lista dei ruoli associati all'utente
     */
    List<String> roles,

    /**
     * Istanza temporale di creazione dell'account utente.
     *
     * @return la data e l'ora di creazione dell'utente
     */
    Instant createdAt
) {

    /**
     * Crea una nuova istanza di {@code UserDto} a partire dai dati forniti.
     *
     * @param id        identificativo univoco dell'utente
     * @param username  nome utente utilizzato per il login e la visualizzazione
     * @param email     indirizzo email dell'utente
     * @param roles     elenco dei ruoli assegnati all'utente
     * @param createdAt data e ora di creazione dell'account
     */
    public UserDto {}
}
