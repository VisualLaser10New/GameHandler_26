package com.gameplatform.central.domain.ports.in;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;

/**
 * Caso d'uso che aggiorna i dati di un utente esistente nel sistema
 * centrale, quali password e ruoli associati.
 */
public interface UpdateUserUseCase {

    /**
     * Aggiorna la password e i ruoli dell'utente identificato.
     *
     * @param id l'identificativo dell'utente da aggiornare; non deve essere {@code null}
     * @param newPassword la nuova password in chiaro; se {@code null} o vuota la password non viene modificata
     * @param newRoles la lista dei nuovi ruoli da associare all'utente; non deve essere {@code null}; se vuota l'utente non avrà ruoli associati
     * @param originatingRequestId l'identificativo della richiesta origine per la tracciabilità; può essere {@code null} nel caso di chiamata REST diretta
     * @return l'entità {@link User} rappresentante l'utente aggiornato
     * @throws com.gameplatform.shared.domain.exception.UserNotFoundException se l'utente non esiste
     * @see #updateUser(UserId, String, List)
     */
    User updateUser(UserId id, String newPassword, List<String> newRoles, String originatingRequestId);

    /**
     * Aggiorna la password e i ruoli dell'utente tramite chiamata REST diretta, senza identificativo di origine.
     *
     * @param id l'identificativo dell'utente da aggiornare; non deve essere {@code null}
     * @param newPassword la nuova password in chiaro; se {@code null} o vuota la password non viene modificata
     * @param newRoles la lista dei nuovi ruoli da associare all'utente; non deve essere {@code null}
     * @return l'entità {@link User} rappresentante l'utente aggiornato
     * @throws com.gameplatform.shared.domain.exception.UserNotFoundException se l'utente non esiste
     * @see #updateUser(UserId, String, List, String)
     */
    default User updateUser(UserId id, String newPassword, List<String> newRoles) {
        return updateUser(id, newPassword, newRoles, null);
    }
}