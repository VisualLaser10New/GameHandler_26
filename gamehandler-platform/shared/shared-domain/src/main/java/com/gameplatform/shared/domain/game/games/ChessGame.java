package com.gameplatform.shared.domain.game.games;

import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.ArrayList;
import java.util.List;

public class ChessGame implements GameLifecycle {
    private boolean running;
    private List<UserId> participants;
    private StopReason stopReason;
    private String boardState;
    private int turnIndex;
    private GameSessionId sessionId;

    public ChessGame(GameSessionId sessionId) {
        this.running = false;
        this.participants = new ArrayList<>();
        this.boardState = this.initialBoard();
        this.stopReason = null;
        this.turnIndex = 0;
        this.sessionId = sessionId;
    }

    @Override
    public GameSessionId getSessionId() {
        return sessionId;
    }

    @Override
    public List<UserId> getParticipants() {
        return participants;
    }

    public void setParticipants(List<UserId> participants) {
        this.participants = participants;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    public String getBoardState() {
        return boardState;
    }

    public void setBoardState(String boardState) {
        this.boardState = boardState;
    }

    public int getTurnIndex() {
        return turnIndex;
    }

    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    @Override
    public void start(List<UserId> participants) {
        this.running = true;
        this.participants = participants;
        this.stopReason = null;
    }

    @Override
    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
    }

    @Override
    public void pause() {
        this.running = false;
    }

    @Override
    public void resume() {
        this.running = true;
    }

    @Override
    public GameStatus getStatus() {
        return running ? GameStatus.IN_PROGRESS : GameStatus.COMPLETED;
    }

    @Override
    public GameType getGameType() {
        return GameType.CHESS;
    }

    @Override
    public int getMinPlayers() {
        return 2;
    }

    @Override
    public int getMaxPlayers() {
        return 2;
    }

    public void endTurn() {
        if (!this.running) {
            throw new IllegalStateException("ChessGame is not running");
        }
        turnIndex = (turnIndex + 1) % 2;
    }

    public String serializeBoardState() {
        return boardState;
    }

    private String initialBoard() {
        return "Manco hai iniziato la partita";
    }
}
