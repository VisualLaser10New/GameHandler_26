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

/**
 * Configurazione Jackson per la serializzazione e deserializzazione JSON dei risultati di gioco.
 * <p>
 * Definisce mix-in per la gestione del polimorfismo delle implementazioni di {@link com.gameplatform.shared.domain.result.GameResult},
 * consentendo a Jackson di serializzare e deserializzare correttamente le sottoclassi concrete in base a un discriminatore di tipo.
 * </p>
 *
 * @see com.gameplatform.shared.domain.result.GameResult
 * @see com.gameplatform.shared.domain.result.ChessResult
 * @see com.gameplatform.shared.domain.result.DartsResult
 * @see com.gameplatform.shared.domain.result.FoosballResult
 * @see com.gameplatform.shared.domain.result.MonopolyResult
 * @see com.gameplatform.shared.domain.result.RiskResult
 * @see com.gameplatform.shared.domain.result.RouletteResult
 * @see com.gameplatform.shared.domain.result.SlotResult
 */
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
    /**
     * Mix-in che applica annotazioni {@link com.fasterxml.jackson.annotation.JsonTypeInfo} e
     * {@link com.fasterxml.jackson.annotation.JsonSubTypes} sull'interfaccia {@link com.gameplatform.shared.domain.result.GameResult}.
     * <p>
     * Utilizza il nome del tipo come discriminatore (proprietà "type") per la deserializzazione polimorfa,
     * con {@link DefaultGameResult} come implementazione predefinita.
     * </p>
     */
    public interface GameResultMixIn {}

    /**
     * Mix-in che esclude dalla serializzazione JSON i metodi {@code getWinnerId()} e {@code getWinnerIds()}
     * per la classe {@link com.gameplatform.shared.domain.result.RouletteResult}.
     * <p>
     * Utilizzato per evitare la duplicazione di informazioni già presenti nella struttura polimorfa del risultato.
     * </p>
     */
    public interface RouletteResultMixIn {
        @com.fasterxml.jackson.annotation.JsonIgnore
        UserId getWinnerId();

        @com.fasterxml.jackson.annotation.JsonIgnore
        List<UserId> getWinnerIds();
    }

    /**
     * Mix-in che esclude dalla serializzazione JSON i metodi {@code getWinnerId()} e {@code getWinnerIds()}
     * per la classe {@link com.gameplatform.shared.domain.result.SlotResult}.
     * <p>
     * Utilizzato per evitare la duplicazione di informazioni già presenti nella struttura polimorfa del risultato.
     * </p>
     */
    public interface SlotResultMixIn {
        @com.fasterxml.jackson.annotation.JsonIgnore
        UserId getWinnerId();

        @com.fasterxml.jackson.annotation.JsonIgnore
        List<UserId> getWinnerIds();
    }

    /**
     * Implementazione predefinita dell'interfaccia {@link com.gameplatform.shared.domain.result.GameResult}.
     * <p>
     * Utilizzata come fallback da Jackson quando il tipo di risultato non corrisponde ad alcuna sottoclasse
     * registrata in {@link GameResultMixIn}. Fornisce campi mutabili per supportare la deserializzazione.
     * </p>
     *
     * @see GameResultMixIn
     */
    public static class DefaultGameResult implements GameResult {
        private UserId winnerId;
        private List<UserId> winnerIds;
        private WinCondition winCondition;

        /**
         * Costruisce un'istanza vuota di {@code DefaultGameResult}.
         * Richiesto da Jackson per la deserializzazione.
         */
        public DefaultGameResult() {}

        /**
         * Costruisce un'istanza di {@code DefaultGameResult} con i valori specificati.
         *
         * @param winnerId    l'identificativo del vincitore singolo
         * @param winnerIds   la lista degli identificativi dei vincitori (per giochi con più vincitori)
         * @param winCondition la condizione di vittoria associata al risultato
         */
        public DefaultGameResult(UserId winnerId, List<UserId> winnerIds, WinCondition winCondition) {
            this.winnerId = winnerId;
            this.winnerIds = winnerIds;
            this.winCondition = winCondition;
        }

        /**
         * Imposta l'identificativo del vincitore singolo.
         *
         * @param winnerId l'identificativo del vincitore da assegnare
         */
        public void setWinnerId(UserId winnerId) {
            this.winnerId = winnerId;
        }

        /**
         * Imposta la lista degli identificativi dei vincitori.
         *
         * @param winnerIds la lista degli identificativi dei vincitori da assegnare
         */
        public void setWinnerIds(List<UserId> winnerIds) {
            this.winnerIds = winnerIds;
        }

        /**
         * Imposta la condizione di vittoria associata al risultato.
         *
         * @param winCondition la condizione di vittoria da assegnare
         */
        public void setWinCondition(WinCondition winCondition) {
            this.winCondition = winCondition;
        }

        /**
         * Restituisce l'identificativo del vincitore singolo.
         *
         * @return l'identificativo del vincitore, o {@code null} se non impostato
         */
        @Override
        public UserId getWinnerId() {
            return winnerId;
        }

        /**
         * Restituisce la lista degli identificativi dei vincitori.
         *
         * @return la lista degli identificativi dei vincitori, oppure una lista vuota se non impostata
         */
        @Override
        public List<UserId> getWinnerIds() {
            return winnerIds != null ? winnerIds : List.of();
        }

        /**
         * Restituisce la condizione di vittoria associata al risultato.
         *
         * @return la condizione di vittoria, o {@code null} se non impostata
         */
        @Override
        public WinCondition getWinCondition() {
            return winCondition;
        }
    }

    /**
     * Configura l'ObjectMapper di Jackson registrando i mix-in per la serializzazione polimorfa
     * delle classi {@link com.gameplatform.shared.domain.result.GameResult},
     * {@link com.gameplatform.shared.domain.result.RouletteResult} e
     * {@link com.gameplatform.shared.domain.result.SlotResult}.
     *
     * @return un personalizzatore del {@code Jackson2ObjectMapperBuilder} con i mix-in configurati
     * @see Jackson2ObjectMapperBuilderCustomizer
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .mixIn(GameResult.class, GameResultMixIn.class)
                .mixIn(RouletteResult.class, RouletteResultMixIn.class)
                .mixIn(SlotResult.class, SlotResultMixIn.class);
    }
}
