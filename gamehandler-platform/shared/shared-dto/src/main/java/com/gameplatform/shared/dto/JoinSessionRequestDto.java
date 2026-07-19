package com.gameplatform.shared.dto;

/**
 * DTO (Data Transfer Object) che rappresenta la richiesta di partecipazione di un
 * utente a una sessione di gioco. Contiene l'identificativo necessario per associare
 * l'utente alla sessione selezionata.
 *
 * @see com.gameplatform.shared.dto.SessionDto
 */
public record JoinSessionRequestDto(
    /**
     * Identificativo univoco dell'utente che richiede di unirsi alla sessione.
     * Non deve essere {@code null} né una stringa vuota; in caso contrario la
     * richiesta viene considerata non valida dal servizio destinatario.
     *
     * @param userId identificativo dell'utente, non {@code null} e non vuoto
     * @return l'identificativo dell'utente associato alla richiesta, mai {@code null}
     */
    String userId
) {}
