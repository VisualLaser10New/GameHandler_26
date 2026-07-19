package com.gameplatform.shared.dto;

import java.util.List;

/**
 * Rappresenta i dati di trasferimento (DTO) di un team all'interno della piattaforma.
 * Trasporta le informazioni essenziali di un gruppo di giocatori: identificativo,
 * denominazione e lista dei membri che lo compongono.
 */
public record TeamDto(
        /**
         * Identificativo univoco del team.
         * Può essere {@code null} se il team non è ancora stato persistito o non è stato associato a un identificatore.
         */
        String id,
        /**
         * Nome descrittivo del team.
         * Non deve essere {@code null} né vuoto per un team valido.
         */
        String name,
        /**
         * Lista dei membri che appartengono al team.
         * Può essere {@code null} o vuota nel caso in cui il team non contenga ancora alcun membro.
         */
        List<String> members
) {
}
