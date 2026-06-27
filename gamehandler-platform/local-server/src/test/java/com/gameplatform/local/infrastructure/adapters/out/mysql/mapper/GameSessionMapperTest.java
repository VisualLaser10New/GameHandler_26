package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameSessionJpaEntity;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.ChessResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameSessionMapperTest {

    private final GameSessionMapper mapper = new GameSessionMapper(new ObjectMapper());

    @Test
    void toEntityAndBackIsSymmetricWithoutResult() {
        Instant started = Instant.parse("2026-01-01T10:00:00Z");
        GameSession domain = new GameSession(
            new GameSessionId("s-1"), new GameId("g-1"), GameType.CHESS,
            new BuildingId("b-1"), GameStatus.IN_PROGRESS, started, null, null,
            null, null, null, List.of(new UserId("u-1"), new UserId("u-2")));

        GameSessionJpaEntity entity = mapper.toEntity(domain);

        assertThat(entity.getId()).isEqualTo("s-1");
        assertThat(entity.getParticipants()).hasSize(2);
        assertThat(entity.getParticipants().get(0).getSessionId()).isEqualTo("s-1");

        GameSession back = mapper.toDomain(entity);
        assertThat(back).usingRecursiveComparison().isEqualTo(domain);
    }

    @Test
    void toDomainNullReturnsNull() {
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void nullParticipantsBecomesEmptyList() {
        GameSession domain = new GameSession(
            new GameSessionId("s-2"), new GameId("g-2"), GameType.DARTS,
            new BuildingId("b-2"), GameStatus.WAITING, Instant.now(), null, null,
            null, null, null, null);

        GameSessionJpaEntity entity = mapper.toEntity(domain);
        assertThat(entity.getParticipants()).isEmpty();

        GameSession back = mapper.toDomain(entity);
        assertThat(back.getParticipants()).isEmpty();
    }

    /**
     * Documenta il bug di deserializzazione: GameResult e' un'interfaccia senza
     * annotazioni Jackson (@JsonTypeInfo/@JsonSubTypes), quindi readValue(GameResult.class)
     * fallisce e il mapper scarta il result (log warning + result=null).
     * La serializzazione funziona, il round-trip NO: il result viene perso.
     */
    @Test
    void roundTripLosesResultDueToMissingJacksonTypeInfo() {
        Instant started = Instant.parse("2026-01-01T10:00:00Z");
        Instant ended = Instant.parse("2026-01-01T11:00:00Z");
        ChessResult result = new ChessResult(
            new UserId("u-1"), List.of(new UserId("u-1")), "checkmate", "fen", WinCondition.WIN);
        GameSession domain = new GameSession(
            new GameSessionId("s-3"), new GameId("g-3"), GameType.CHESS,
            new BuildingId("b-3"), GameStatus.COMPLETED, started, ended, 3600,
            new UserId("u-1"), WinCondition.WIN, result, List.of(new UserId("u-1")));

        GameSessionJpaEntity entity = mapper.toEntity(domain);
        // serializzazione OK: il JSON viene scritto
        assertThat(entity.getResultData()).isNotBlank();

        GameSession back = mapper.toDomain(entity);
        // BUG: il result viene perso in deserializzazione
        assertThat(back.getResult()).isNull();
    }
}
