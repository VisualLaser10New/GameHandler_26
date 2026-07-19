package com.gameplatform.shared.dto;

/**
 * Rappresenta i dati restituiti al termine di una registrazione andata a buon fine.
 * Contiene l'identificativo assegnato all'utente e i dati anagrafici essenziali
 * dell'account appena creato.
 *
 * @param userId   l'identificativo univoco dell'utente registrato
 * @param username il nome utente scelto in fase di registrazione
 * @param email    l'indirizzo email associato all'account
 */
public record SignupResponseDto(
    String userId,
    String username,
    String email
) {}
