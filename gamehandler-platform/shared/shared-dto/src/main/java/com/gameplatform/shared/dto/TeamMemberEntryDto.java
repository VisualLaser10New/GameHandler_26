package com.gameplatform.shared.dto;

import java.util.List;

/**
 * Rappresenta il raggruppamento per singolo team all'interno del payload di un
 * {@link TeamMembersEventDto}. Associa l'identificativo del team (UUID) alla lista
 * completa degli identificativi utente dei suoi membri, costituendo la snapshot
 * che il lato Local inserisce o aggiorna nella tabella {@code team_members_local}.
 *
 * @param teamId      l'identificativo del team (UUID); non deve essere {@code null} né vuoto
 * @param teamMembers la lista completa degli identificativi utente dei membri del team;
 *                    non deve essere {@code null}; pu&ograve; essere vuota per indicare un team senza membri
 *
 * @see TeamMembersEventDto
 */
public record TeamMemberEntryDto(
        String teamId,
        List<String> teamMembers
) {
}