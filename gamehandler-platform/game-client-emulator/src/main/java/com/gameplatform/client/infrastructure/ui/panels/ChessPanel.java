package com.gameplatform.client.infrastructure.ui.panels;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

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

    // Unicode chess pieces: index 0=white, 1=black; piece order: K,Q,R,B,N,P
    private static final String[] WHITE = {"♔", "♕", "♖", "♗", "♘", "♙"};
    private static final String[] BLACK = {"♚", "♛", "♜", "♝", "♞", "♟"};

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

    public ChessPanel() {
        root = new VBox(12);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 16;");

        turnLabel = new Label("In attesa...");
        turnLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #eee; -fx-font-weight: bold;");

        // Board grid
        boardGrid = new GridPane();
        boardGrid.setAlignment(Pos.CENTER);
        boardGrid.setHgap(0);
        boardGrid.setVgap(0);
        initBoard();

        // Capture controls
        captureCombo = new ComboBox<>();
        captureCombo.getItems().addAll(
            "♟ Pedone nero", "♞ Cavallo nero", "♝ Alfiere nero", "♜ Torre nera", "♛ Regina nera",
            "♙ Pedone bianco", "♘ Cavallo bianco", "♗ Alfiere bianco", "♖ Torre bianca", "♕ Regina bianca"
        );
        captureCombo.setPromptText("Seleziona pezzo mangiato");
        captureCombo.setStyle("-fx-background-color: #333; -fx-text-fill: #eee;");
        captureCombo.setDisable(true);

        captureButton = new Button("Registra pezzo mangiato");
        captureButton.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-padding: 6 14;");
        captureButton.setDisable(true);

        capturedLabel = new Label("Mangiati: nessuno");
        capturedLabel.setStyle("-fx-text-fill: #bbb; -fx-font-size: 12;");

        captureButton.setOnAction(e -> {
            String selected = captureCombo.getValue();
            if (selected != null) {
                capturedPieces.add(selected);
                capturedLabel.setText("Mangiati: " + String.join(", ", capturedPieces));
                captureCombo.setValue(null);
            }
        });

        HBox captureBox = new HBox(8, captureCombo, captureButton);
        captureBox.setAlignment(Pos.CENTER);

        endTurnButton = new Button("✓ Fine Turno");
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
        capturedLabel.setText("Mangiati: nessuno");
        initBoard();
        updateTurnLabel();
        endTurnButton.setDisable(false);
        captureButton.setDisable(false);
        captureCombo.setDisable(false);
    }

    @Override
    public void onGameStopped() {
        endTurnButton.setDisable(true);
        captureButton.setDisable(true);
        captureCombo.setDisable(true);
        turnLabel.setText("Partita terminata");
        turnLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #f39c12; -fx-font-weight: bold;");
    }

    private void initBoard() {
        boardGrid.getChildren().clear();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                board[row][col] = INITIAL_BOARD[row][col];
                boolean lightSquare = (row + col) % 2 == 0;
                Label cell = new Label(board[row][col]);
                cell.setMinSize(46, 46);
                cell.setAlignment(Pos.CENTER);
                cell.setStyle(
                    "-fx-font-size: 26; " +
                    "-fx-background-color: " + (lightSquare ? "#f0d9b5" : "#b58863") + "; " +
                    "-fx-border-color: transparent;"
                );
                cells[row][col] = cell;
                boardGrid.add(cell, col, row);
            }
        }
    }

    private void endTurn() {
        if (players.isEmpty()) return;
        turnIndex = (turnIndex + 1) % players.size();
        updateTurnLabel();
    }

    private void updateTurnLabel() {
        if (players.isEmpty()) return;
        String current = players.get(turnIndex);
        String color = turnIndex % 2 == 0 ? "Bianco ♔" : "Nero ♚";
        turnLabel.setText("Turno di: " + current + " (" + color + ")");
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
