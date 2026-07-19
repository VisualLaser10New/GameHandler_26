package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Record che rappresenta un evento memorizzato nella tabella outbox in attesa di
 * essere pubblicato verso il sistema di messaggistica. Incapsula i dati essenziali
 * per identificare, tipizzare e ricostruire un evento di dominio in modo affidabile.
 *
 * @see com.gameplatform.shared.dto.OutboxEventDto#eventId()
 * @see com.gameplatform.shared.dto.OutboxEventDto#eventType()
 * @see com.gameplatform.shared.dto.OutboxEventDto#payload()
 * @see com.gameplatform.shared.dto.OutboxEventDto#createdAt()
 */
public record OutboxEventDto(
    /**
     * Identificativo univoco dell'evento. Non deve essere {@code null} e consente
     * di correlare e deduplicare l'evento durante la pubblicazione.
     *
     * @return l'identificativo univoco dell'evento, mai {@code null}
     */
    String eventId,

    /**
     * Tipo logico dell'evento di dominio (ad esempio il nome dell'operazione o
     * della classe di evento). Non deve essere {@code null} e determina come il
     * consumatore interpreta il contenuto del payload.
     *
     * @return il tipo dell'evento, mai {@code null}
     */
    String eventType,

    /**
     * Contenuto serializzato dell'evento (tipicamente in formato JSON). Non deve
     * essere {@code null}; una stringa vuota rappresenta un evento privo di dati.
     *
     * @return il payload dell'evento, mai {@code null}
     */
    String payload,

    /**
     * Istante di creazione dell'evento. Non deve essere {@code null} e viene
     * utilizzato per ordinare e tracciare la temporizzazione degli eventi.
     *
     * @return l'istante di creazione dell'evento, mai {@code null}
     */
    Instant createdAt
) {}
