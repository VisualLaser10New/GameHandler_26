package com.gameplatform.client.infrastructure.ui.panels;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Emulation panel for Chess (Scacchi).
 * <p>
 * Displays an 8×8 Unicode chessboard with an initial piece layout,
 * a turn indicator, and controls to end the current player's turn
 * or register a captured piece.
 */
public class ChessPanel implements GamePanel {

    // Unicode chess pieces
    private static final java.util.Set<String> WHITE_PIECES = java.util.Set.of("♔","♕","♖","♗","♘","♙");
    private static final java.util.Set<String> BLACK_PIECES = java.util.Set.of("♚","♛","♜","♝","♞","♟");

    private static final String[][] INITIAL_BOARD = {
        {"♜","♞","♝","♛","♚","♝","♞","♜"},
        {"♟","♟","♟","♟","♟","♟","♟","♟"},
        {"","","","","","","",""},
        {"","","","","","","",""},
        {"","","","","","","",""},
        {"","","","","","","",""},
        {"♙","♙","♙","♙","♙","♙","♙","♙"},
        {"♖","♘","♗","♕","♔","♗","♘","♖"}
    };

    private final VBox root;
    private final GridPane boardGrid;
    private final Label turnLabel;
    private final Label capturedLabel;
    private final Button endTurnButton;
    private final ComboBox<String> captureCombo;
    private final Button captureButton;
    private final List<String> capturedPieces = new ArrayList<>();
    private final String[][] board = new String[8][8];
    private final Label[][] cells = new Label[8][8];

    private List<String> players = new ArrayList<>();
    private int turnIndex = 0;
    private TurnPublisher turnPublisher;
    private MovePublisher movePublisher;
    private String currentUser = "";

    public ChessPanel() {
        root = new VBox(12);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 16;");

        turnLabel = new Label("Waiting...");
        turnLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #eee; -fx-font-weight: bold;");

        // Board grid
        boardGrid = new GridPane();
        boardGrid.setAlignment(Pos.CENTER);
        boardGrid.setHgap(0);
        boardGrid.setVgap(0);
        // Make the 8x8 board scale with the available space: each column
        // and row gets 12.5% of the grid's width/height. This replaces the
        // fixed 52px per cell that caused the board to overflow the centre
        // and push the bottom buttonBar off-screen.
        for (int i = 0; i < 8; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(12.5);
            cc.setHgrow(Priority.ALWAYS);
            cc.setFillWidth(true);
            boardGrid.getColumnConstraints().add(cc);

            RowConstraints rc = new RowConstraints();
            rc.setPercentHeight(12.5);
            rc.setVgrow(Priority.ALWAYS);
            rc.setFillHeight(true);
            boardGrid.getRowConstraints().add(rc);
        }
        boardGrid.setMinSize(0, 0);
        // Let the board absorb any leftover vertical space inside the
        // VBox so the squares grow instead of leaving empty space below.
        VBox.setVgrow(boardGrid, Priority.ALWAYS);
        initBoard();

        // Capture controls
        captureCombo = new ComboBox<>();
        captureCombo.getItems().addAll(
            "♟ Black Pawn", "♞ Black Knight", "♝ Black Bishop", "♜ Black Rook", "♛ Black Queen",
            "♙ White Pawn", "♘ White Knight", "♗ White Bishop", "♖ White Rook", "♕ White Queen"
        );
        captureCombo.setPromptText("Select captured piece");
        captureCombo.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        captureCombo.setDisable(true);

        captureButton = new Button("Record captured piece");
        captureButton.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-padding: 6 14;");
        captureButton.setDisable(true);

        capturedLabel = new Label("Captured: none");
        capturedLabel.setStyle("-fx-text-fill: #bbb; -fx-font-size: 12;");

        captureButton.setOnAction(e -> {
            String selected = captureCombo.getValue();
            if (selected != null) {
                capturedPieces.add(selected);
                capturedLabel.setText("Captured: " + String.join(", ", capturedPieces));
                captureCombo.setValue(null);
            }
        });

        HBox captureBox = new HBox(8, captureCombo, captureButton);
        captureBox.setAlignment(Pos.CENTER);

        endTurnButton = new Button("✓ End Turn");
        endTurnButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 24;");
        endTurnButton.setDisable(true);
        endTurnButton.setOnAction(e -> endTurn());

        root.getChildren().addAll(turnLabel, boardGrid, captureBox, capturedLabel, endTurnButton);
    }

    @Override
    public Parent getView() { return root; }

    @Override
    public void onGameStarted(List<String> participants) {
        this.players = new ArrayList<>(participants);
        this.turnIndex = 0;
        this.capturedPieces.clear();
        capturedLabel.setText("Captured: none");
        initBoard();
        updateTurnLabel();
        applyTurnControls();
    }

    @Override
    public void setTurnContext(TurnPublisher turnPublisher, String currentUser) {
        this.turnPublisher = turnPublisher;
        this.currentUser = currentUser != null ? currentUser : "";
        applyTurnControls();
    }

    @Override
    public void setMovePublisher(MovePublisher movePublisher) {
        this.movePublisher = movePublisher;
    }

    @Override
    public void onRemoteTurnUpdate(int newTurnIndex, String playerName) {
        if (newTurnIndex >= 0 && newTurnIndex < players.size()) {
            this.turnIndex = newTurnIndex;
            updateTurnLabel();
            applyTurnControls();
        }
    }

    @Override
    public void onRemoteMove(int fromRow, int fromCol, int toRow, int toCol, String capturedPiece) {
        // Apply a move made by the remote player so the local board
        // stays in sync with the opponent's view.  This is invoked from
        // the JavaFX thread by GamePlayView.
        String piece = board[fromRow][fromCol];
        if (piece.isEmpty()) return; // stale/invalid move
        // Record a capture if the remote reported one (or if the local
        // board still has an enemy piece on the target cell).
        String target = board[toRow][toCol];
        if (capturedPiece != null && !capturedPiece.isEmpty()) {
            capturedPieces.add(capturedPiece);
            capturedLabel.setText("Captured: " + String.join(", ", capturedPieces));
        } else if (!target.isEmpty() && !target.equals(piece)) {
            capturedPieces.add(target);
            capturedLabel.setText("Captured: " + String.join(", ", capturedPieces));
        }
        board[toRow][toCol] = piece;
        board[fromRow][fromCol] = "";
        cells[toRow][toCol].setText(piece);
        cells[fromRow][fromCol].setText("");
    }

    @Override
    public void onGameStopped() {
        endTurnButton.setDisable(true);
        captureButton.setDisable(true);
        captureCombo.setDisable(true);
        turnLabel.setText("Match ended");
        turnLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #f39c12; -fx-font-weight: bold;");
    }

    private void initBoard() {
        boardGrid.getChildren().clear();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                board[row][col] = INITIAL_BOARD[row][col];
                final int r = row;
                final int c = col;
                boolean lightSquare = (row + col) % 2 == 0;
                Label cell = new Label(board[row][col]);
                cell.setMinSize(0, 0);
                cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                cell.setAlignment(Pos.CENTER);
                cell.setStyle(
                    "-fx-font-size: 28; " +
                    "-fx-background-color: " + (lightSquare ? "#f0d9b5" : "#b58863") + "; " +
                    "-fx-border-color: transparent;"
                );
                cells[row][col] = cell;

                // ── Drag-and-drop: start dragging a piece ──
                cell.setOnDragDetected(e -> {
                    String piece = board[r][c];
                    if (piece.isEmpty() || !isMyTurn() || !isMyPiece(piece)) {
                        e.consume();
                        return;
                    }
                    Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(r + "," + c);
                    db.setContent(content);
                    e.consume();
                });

                // ── Drag-and-drop: accept drag over a target cell ──
                cell.setOnDragOver(e -> {
                    if (e.getGestureSource() != cell
                            && e.getDragboard().hasString()
                            && isMyTurn()) {
                        e.acceptTransferModes(TransferMode.MOVE);
                    }
                    e.consume();
                });

                // ── Drag-and-drop: drop the piece on this cell ──
                cell.setOnDragDropped(e -> {
                    Dragboard db = e.getDragboard();
                    boolean success = false;
                    if (db.hasString()) {
                        String[] src = db.getString().split(",");
                        int srcRow = Integer.parseInt(src[0]);
                        int srcCol = Integer.parseInt(src[1]);
                        String piece = board[srcRow][srcCol];
                        if (!piece.isEmpty() && isMyPiece(piece)
                                && !(srcRow == r && srcCol == c)) {
                            // Capture: if target has an enemy piece, record it
                            String target = board[r][c];
                            String captured = null;
                            if (!target.isEmpty()) {
                                capturedPieces.add(target);
                                captured = target;
                                capturedLabel.setText("Captured: " + String.join(", ", capturedPieces));
                            }
                            // Move the piece
                            board[r][c] = piece;
                            board[srcRow][srcCol] = "";
                            cells[r][c].setText(piece);
                            cells[srcRow][srcCol].setText("");
                            // Broadcast the move to the remote emulator
                            if (movePublisher != null) {
                                movePublisher.publish(srcRow, srcCol, r, c, captured);
                            }
                            success = true;
                        }
                    }
                    e.setDropCompleted(success);
                    e.consume();
                });

                cell.setOnDragDone(DragEvent::consume);

                boardGrid.add(cell, col, row);
            }
        }
    }

    /** Returns true if it is the local user's turn. */
    private boolean isMyTurn() {
        return !players.isEmpty()
                && !currentUser.isBlank()
                && currentUser.equals(players.get(turnIndex));
    }

    /**
     * Returns true if the given Unicode chess piece belongs to the
     * player whose turn it currently is. Player 0 (turnIndex even)
     * controls White; player 1 (turnIndex odd) controls Black.
     */
    private boolean isMyPiece(String piece) {
        boolean white = turnIndex % 2 == 0;
        return white ? WHITE_PIECES.contains(piece) : BLACK_PIECES.contains(piece);
    }

    private void endTurn() {
        if (players.isEmpty()) return;
        turnIndex = (turnIndex + 1) % players.size();
        updateTurnLabel();
        applyTurnControls();
        broadcastTurn();
    }

    private void broadcastTurn() {
        if (turnPublisher != null && !players.isEmpty()) {
            turnPublisher.publish(turnIndex, players.get(turnIndex));
        }
    }

    /**
     * Enables the "Fine Turno" / capture controls only when it is the
     * local user's turn. Prevents both emulators from acting at the
     * same time — the root cause of the bug where both players saw
     * simultaneously "their" turn.
     */
    private void applyTurnControls() {
        boolean myTurn = !players.isEmpty()
                && !currentUser.isBlank()
                && currentUser.equals(players.get(turnIndex));
        endTurnButton.setDisable(!myTurn);
        captureButton.setDisable(!myTurn);
        captureCombo.setDisable(!myTurn);
    }

    private void updateTurnLabel() {
        if (players.isEmpty()) return;
        String current = players.get(turnIndex);
        String color = turnIndex % 2 == 0 ? "White ♔" : "Black ♚";
        turnLabel.setText("Turn of: " + current + " (" + color + ")");
        turnLabel.setStyle("-fx-font-size: 14; -fx-text-fill: " + (turnIndex % 2 == 0 ? "#f0d9b5" : "#555") + "; -fx-font-weight: bold;");
    }

    /** Returns the current player's name (winner candidate). */
    public String getCurrentPlayer() {
        if (players.isEmpty()) return null;
        return players.get(turnIndex);
    }

    /** Returns the winner. In chess, the player who captured the enemy king wins;
     *  for this emulator, we treat the current player (last to move) as winner. */
    public String getWinnerId() {
        return getCurrentPlayer();
    }

    /** Returns comma-separated "player:capturedCount" pairs. */
    public String getResultData() {
        StringBuilder sb = new StringBuilder();
        for (String p : players) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p).append(":").append(capturedPieces.size());
        }
        return sb.toString();
    }
}
