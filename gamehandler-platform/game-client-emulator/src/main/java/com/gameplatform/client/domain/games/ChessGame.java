package com.gameplatform.client.domain.games;

import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.ArrayList;
import java.util.List;

public class ChessGame {
    private boolean running;
    private List<UserId> participants;
    private StopReason stopReason;
    private String boardState;
    private int turnIndex;

    public ChessGame() {
        this.running = false;
        this.participants = new ArrayList<>();
        this.boardState = this.initialBoard();
        this.stopReason = null;
        this.turnIndex = 0;
    }

    public void start(List<UserId> participants) {
        this.running = true;
        this.participants = participants;
        this.stopReason = null;
    }

    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
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
