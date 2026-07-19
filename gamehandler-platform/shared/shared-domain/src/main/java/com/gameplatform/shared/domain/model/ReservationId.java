package com.gameplatform.shared.domain.model;

/**
 * Identificatore univoco di una prenotazione all'interno della piattaforma.
 *
 * <p>Incapsula il valore stringa che rappresenta la chiave di una prenotazione,
 * garantendo tramite il proprio {@link #ReservationId(String) costruttore} che
 * il valore non sia nullo né vuoto.</p>
 *
 * @see com.gameplatform.shared.domain.model.Reservation
 */
public record ReservationId(String value) {

    /**
     * Crea un nuovo identificatore di prenotazione a partire dal valore fornito.
     *
     * <p>Il valore non deve essere {@code null} né una stringa vuota o composta
     * unicamente da spazi in quanto, in tali casi, l'identificatore risulterebbe
     * privo di significato.</p>
     *
     * @param value il valore stringa dell'identificatore di prenotazione; non deve
     *              essere {@code null}, né vuoto, né costituito soltanto da spazi
     * @throws IllegalArgumentException se {@code value} è {@code null} o è vuoto
     *                                  (blank)
     */
    public ReservationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ReservationId cannot be null");
        }
    }
}
