package com.gameplatform.shared.dto;

import java.util.List;

/**
 * Per-team bucket inside a {@link TeamMembersEventDto} payload. Carries the
 * {@code teamId} (UUID) and the full list of member {@code userId}s for that
 * team — the snapshot the Local side upserts into
 * {@code team_members_local}.
 *
 * @param teamId      the team id (UUID)
 * @param teamMembers the full list of member user ids for this team
 */
public record TeamMemberEntryDto(
        String teamId,
        List<String> teamMembers
) {
}