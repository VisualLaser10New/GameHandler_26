package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentStatus;
import java.time.Instant;
import java.util.List;

/**
 * DTO (Data Transfer Object) che rappresenta un torneo completo esposto ai client
 * tramite le API di gioco. Contiene le informazioni descrittive, di configurazione
 * e di stato di un torneo, incluse le strutture coinvolte e il conteggio dei partecipanti.
 *
 * @see com.gameplatform.shared.domain.model.GameType
 * @see com.gameplatform.shared.domain.model.TournamentStatus
 */
public record TournamentDto(
        /**
         * Identificativo univoco del torneo. Non deve essere {@code null} né vuoto.
         */
        String id,
        /**
         * Nome visualizzato del torneo. Non deve essere {@code null} né vuoto.
         */
        String name,
        /**
         * Tipologia di gioco a cui il torneo fa riferimento. Non deve essere {@code null}.
         */
        GameType gameType,
        /**
         * Indica se il torneo è a squadre ({@code true}) oppure individuale ({@code false}).
         */
        boolean teamBased,
        /**
         * Numero di membri per squadra. Se il torneo non è a squadre ({@code teamBased} {@code false})
         * il valore è tipicamente {@code 0} o {@code 1}; per i tornei a squadre è maggiore o uguale a {@code 1}.
         */
        int teamSize,
        /**
         * Stato corrente del torneo (es. programmato, in corso, concluso). Non deve essere {@code null}.
         */
        TournamentStatus status,
        /**
         * Istante di inizio del torneo. Può essere {@code null} se l'inizio non è ancora stato fissato.
         */
        Instant startsAt,
        /**
         * Istante di fine del torneo. Può essere {@code null} se il torneo è ancora in corso o senza scadenza.
         */
        Instant endsAt,
        /**
         * Elenco degli identificativi delle strutture (building) coinvolte nel torneo.
         * Può essere una lista vuota ma non deve essere {@code null}.
         */
        List<String> buildings,
        /**
         * Numero di partecipanti attualmente iscritti al torneo. Non negativo; {@code 0} indica nessun iscritto.
         */
        int participantsCount
) {
}
