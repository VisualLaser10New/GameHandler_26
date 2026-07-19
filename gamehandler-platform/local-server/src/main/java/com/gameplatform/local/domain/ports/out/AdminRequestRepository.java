package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.AdminRequestLocal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository out-port per la persistenza locale delle richieste amministrative
 * asincrone verso il sistema centrale.
 * <p>
 * Una nuova richiesta viene persistita con stato {@code PENDING} atomicamente
 * assieme all'evento outbox. Il ciclo di vita si conclude con la transizione
 * a {@code COMPLETED} tramite {@link #markCompleted}, oppure a {@code FAILED}
 * tramite {@link #markFailed} quando non si riceve risposta entro il timeout
 * configurato. Entrambe le operazioni sono aggiornamenti condizionali che
 * modificano solo righe in stato {@code PENDING}, garantendo idempotenza in
 * caso di consegna duplicata dello stesso evento di ritorno.
 * </p>
 *
 * @see AdminRequestLocal
 */
public interface AdminRequestRepository {

    /**
     * Salva una nuova richiesta amministrativa.
     *
     * @param request la richiesta amministrativa da persistere
     * @return la richiesta amministrativa persistita
     */
    AdminRequestLocal save(AdminRequestLocal request);

    /**
     * Cerca una richiesta amministrativa in base al suo identificativo.
     *
     * @param requestId l'identificativo della richiesta
     * @return un {@code Optional} contenente la richiesta, vuoto se non trovata
     */
    Optional<AdminRequestLocal> findByRequestId(String requestId);

    /**
     * Restituisce tutte le richieste amministrative effettuate da un determinato utente.
     *
     * @param actingUserId l'identificativo dell'utente che ha effettuato la richiesta
     * @return la lista delle richieste dell'utente specificato
     */
    List<AdminRequestLocal> findByActingUserId(String actingUserId);

    /**
     * Restituisce tutte le richieste amministrative di un determinato utente
     * filtrate per stato.
     *
     * @param actingUserId l'identificativo dell'utente che ha effettuato la richiesta
     * @param status       lo stato delle richieste da filtrare
     * @return la lista delle richieste corrispondenti ai criteri specificati
     */
    List<AdminRequestLocal> findByActingUserIdAndStatus(String actingUserId, String status);

    /**
     * Transita atomicamente una richiesta amministrativa dallo stato
     * {@code PENDING} a {@code COMPLETED}, memorizzando il JSON dei dati
     * risultato. Operazione idempotente: una seconda chiamata su una riga
     * gi&agrave; in stato {@code COMPLETED} non ha effetto.
     *
     * @param requestId  l'identificativo della richiesta da completare
     * @param resultData il JSON contenente i dati risultato
     * @param now        il timestamp corrente per l'aggiornamento
     * @return il numero di righe effettivamente modificate (0 se la riga era
     *         gi&agrave; risolta o non esiste)
     */
    int markCompleted(String requestId, String resultData, Instant now);

    /**
     * Transita atomicamente una richiesta amministrativa dallo stato
     * {@code PENDING} a {@code FAILED}, memorizzando il JSON con il motivo
     * del fallimento. Operazione idempotente.
     *
     * @param requestId l'identificativo della richiesta da marcare come fallita
     * @param reason    il JSON contenente il motivo del fallimento
     * @param now       il timestamp corrente per l'aggiornamento
     * @return il numero di righe effettivamente modificate
     */
    int markFailed(String requestId, String reason, Instant now);

    /**
     * Restituisce le richieste amministrative in stato {@code PENDING} la cui
     * data di creazione &egrave; precedente alla soglia specificata.
     *
     * @param threshold il timestamp limite per la selezione delle richieste scadute
     * @return la lista delle richieste pending pi&ugrave; vecchie della soglia
     */
    List<AdminRequestLocal> findPendingOlderThan(Instant threshold);
}