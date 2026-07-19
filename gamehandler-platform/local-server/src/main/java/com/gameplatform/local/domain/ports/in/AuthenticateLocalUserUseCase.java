package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.LoginResponseDto;

/**
 * Use case per l'autenticazione di un utente locale tramite credenziali.
 * Verifica la corrispondenza tra username e password e restituisce una
 * risposta contenente il token di sessione e le informazioni dell'utente.
 *
 * @see com.gameplatform.shared.dto.LoginResponseDto
 */
public interface AuthenticateLocalUserUseCase {
    /**
     * Autentica un utente locale con le credenziali fornite.
     *
     * @param username nome utente dell'account
     * @param password password in chiaro dell'account
     * @return la risposta di login contenente token e dati utente
     */
    LoginResponseDto authenticate(String username, String password);
}
