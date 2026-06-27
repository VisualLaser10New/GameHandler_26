package com.gameplatform.local.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

import java.util.List;

@Configuration
public class JacksonConfig {

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        defaultImpl = DefaultGameResult.class
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ChessResult.class, name = "CHESS"),
        @JsonSubTypes.Type(value = DartsResult.class, name = "DARTS"),
        @JsonSubTypes.Type(value = FoosballResult.class, name = "FOOSBALL"),
        @JsonSubTypes.Type(value = MonopolyResult.class, name = "MONOPOLY"),
        @JsonSubTypes.Type(value = RiskResult.class, name = "RISK"),
        @JsonSubTypes.Type(value = RouletteResult.class, name = "ROULETTE"),
        @JsonSubTypes.Type(value = SlotResult.class, name = "SLOT")
    })
    public interface GameResultMixIn {}

    public static class DefaultGameResult implements GameResult {
        private UserId winnerId;
        private List<UserId> winnerIds;
        private WinCondition winCondition;

        public DefaultGameResult() {}

        public DefaultGameResult(UserId winnerId, List<UserId> winnerIds, WinCondition winCondition) {
            this.winnerId = winnerId;
            this.winnerIds = winnerIds;
            this.winCondition = winCondition;
        }

        public void setWinnerId(UserId winnerId) {
            this.winnerId = winnerId;
        }

        public void setWinnerIds(List<UserId> winnerIds) {
            this.winnerIds = winnerIds;
        }

        public void setWinCondition(WinCondition winCondition) {
            this.winCondition = winCondition;
        }

        @Override
        public UserId getWinnerId() {
            return winnerId;
        }

        @Override
        public List<UserId> getWinnerIds() {
            return winnerIds != null ? winnerIds : List.of();
        }

        @Override
        public WinCondition getWinCondition() {
            return winCondition;
        }
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder.mixIn(GameResult.class, GameResultMixIn.class);
    }
}
