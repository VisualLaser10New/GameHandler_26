package com.gameplatform.shared.dto;

import java.util.List;

/**
 * DTO restituito dall'endpoint Locale {@code GET /api/auth/me}
 * (PIANO §7.B). Trasporta lo username dell'utente autenticato insieme
 * all'id utente arricchito {@code userId}, ai {@code roles} (risolti a
 * partire dalla tabella locale replicata {@code replicated_users}) e ai
 * {@code buildings} (risolti da {@code local_admin_buildings_local}
 * quando l'utente è un {@code LOCAL_ADMIN}; vuoto per i ruoli non admin).
 *
 * <p>Il costruttore breve a singolo argomento
 * {@link #UserInfoDto(String)} è mantenuto per compatibilità con il
 * contratto della FASE 2 e delega al costruttore canonico a 4 argomenti
 * con {@code userId} nullo e liste vuote.</p>
 *
 * @param username  lo username dell'utente autenticato
 * @param userId    l'id dell'utente autenticato (nullo finché non
 *                  risolto, es. risposta stubbata in unit test)
 * @param roles     la lista dei ruoli dell'utente (eventualmente vuota,
 *                  mai nulla)
 * @param buildings la lista degli edifici di cui l'utente è
 *                  {@code LOCAL_ADMIN} (eventualmente vuota, mai nulla)
 *
 * @see com.gameplatform.shared.dto.UserInfoDto#username()
 * @see com.gameplatform.shared.dto.UserInfoDto#userId()
 * @see com.gameplatform.shared.dto.UserInfoDto#roles()
 * @see com.gameplatform.shared.dto.UserInfoDto#buildings()
 */
public record UserInfoDto(
        String username,
        String userId,
        List<String> roles,
        List<String> buildings
) {
    /**
     * Costruttore breve mantenuto per compatibilità con il contratto
     * della FASE 2. Crea un'istanza delegando al costruttore canonico a
     * 4 argomenti, impostando {@code userId} a {@code null} e
     * {@code roles} e {@code buildings} a liste vuote.
     *
     * @param username lo username dell'utente autenticato
     */
    public UserInfoDto(String username) {
        this(username, null, List.of(), List.of());
    }
}
