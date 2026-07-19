package com.gameplatform.shared.domain.model;

/**
 * Enumera gli stati possibili di una macchina da gioco all'interno della piattaforma.
 *
 * <p>Ogni costante rappresenta una condizione distinta del ciclo di vita della macchina,
 * dalla disponibilità all'utilizzo fino alla manutenzione, ed è utilizzata per
 * determinare le operazioni consentite e la visibilità della macchina stessa.</p>
 *
 * @see com.gameplatform.shared.domain.model.GameMachine
 */
public enum GameMachineStatus {
    /**
     * Indica che la macchina è libera e utilizzabile da un qualsiasi giocatore.
     */
    AVAILABLE,

    /**
     * Indica che la macchina è stata riservata da un giocatore e non è temporaneamente
     * disponibile per gli altri utenti fino alla presa in uso o alla scadenza della riserva.
     */
    RESERVED,

    /**
     * Indica che la macchina è attualmente in uso attivo da parte di un giocatore.
     */
    IN_USE,

    /**
     * Indica che la macchina è indisponibile in quanto sottoposta a operazioni di
     * manutenzione o riparazione.
     */
    MAINTENANCE,

    /**
     * Indica che la macchina è posizionata in una lobby in attesa dell'avvio di una
     * sessione di gioco o dell'ingresso di altri partecipanti.
     */
    LOBBY
}
