package com.gameplatform.shared.dto;

/**
 * DTO (Data Transfer Object) utilizzato come corpo della richiesta per
 * aggiornare un gioco all'interno del catalogo di un edificio da parte di un
 * amministratore locale (LOCAL_ADMIN).
 *
 * <p>Entrambi i campi sono opzionali (nullable): almeno uno dei due deve essere
 * valorizzato, come validato dal servizio. Il campo {@code status}, quando
 * fornito, deve corrispondere a uno dei valori dell'enumerazione
 * {@code GameMachineStatus} gestiti dal flusso amministrativo (attualmente
 * {@code AVAILABLE} e {@code MAINTENANCE}).</p>
 *
 * @param name   il nuovo nome leggibile dell'elemento, oppure {@code null} per
 *               lasciarlo invariato
 * @param status il nuovo valore dell'enumerazione {@code GameMachineStatus}
 *               ({@code AVAILABLE} o {@code MAINTENANCE}), oppure {@code null}
 *               per lasciarlo invariato
 *
 * @see com.gameplatform.shared.dto.GameMachineStatus
 */
public record UpdateGameRequestDto(
        String name,
        String status
) {
}