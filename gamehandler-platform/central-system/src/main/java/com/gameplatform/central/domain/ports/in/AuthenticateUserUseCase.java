package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.dto.LoginResponseDto;

/**
 * Caso d'uso per l'autenticazione di un utente nel sistema centrale.
 *
 * <p>Verifica le credenziali fornite e, in caso di successo, restituisce il
 * payload di risposta contenente il token e i dati di sessione dell'utente.</p>
 */
public interface AuthenticateUserUseCase {

    /**
     * Autentica un utente a partire dalle credenziali fornite.
     *
     * @param username il nome utente dell'account da autenticare; non deve essere {@code null} né vuoto
     * @param password la password in chiaro associata all'utente; non deve essere {@code null}
     * @return il {@link LoginResponseDto} contenente il token e i dati di sessione dell'utente autenticato
     * @throws com.gameplatform.shared.domain.exception.AuthenticationException se le credenziali sono errate o l'utente non esiste
     * @see com.gameplatform.shared.dto.LoginResponseDto
     */
    LoginResponseDto authenticate(String username, String password);
}

