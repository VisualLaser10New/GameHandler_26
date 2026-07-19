package com.gameplatform.client.infrastructure.ui.panels;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Pannello di emulazione per il gioco degli Scacchi (Chess).
 * <p>
 * Visualizza una scacchiera 8&times;8 con pezzi Unicode nella disposizione iniziale,
 * un indicatore del turno e controlli per terminare il turno del giocatore corrente
 * o registrare un pezzo catturato.
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

    /**
     * Costruisce il pannello degli scacchi inizializzando la scacchiera,
     * l'indicatore del turno, i controlli di cattura e il pulsante di fine turno.
     */
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

    /**
     * Avvia la partita inizializzando la scacchiera, resettando i pezzi catturati
     * e aggiornando l'indicatore del turno e lo stato dei controlli.
     *
     * @param participants lista dei nomi utente dei partecipanti in ordine di sessione;
     *                     deve contenere almeno un giocatore
     */
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

    /**
     * Imposta il contesto di turno per la sincronizzazione multiplayer.
     *
     * @param turnPublisher publisher per la trasmissione dei cambi di turno
     * @param currentUser   nome utente del giocatore locale; {@code null} viene
     *                      convertito in stringa vuota
     * @see #onRemoteTurnUpdate(int, String)
     * @see #setMovePublisher(MovePublisher)
     */
    @Override
    public void setTurnContext(TurnPublisher turnPublisher, String currentUser) {
        this.turnPublisher = turnPublisher;
        this.currentUser = currentUser != null ? currentUser : "";
        applyTurnControls();
    }

    /**
     * Imposta il publisher per la trasmissione delle mosse dei pezzi agli emulatori remoti.
     *
     * @param movePublisher publisher per le mosse in uscita
     * @see #onRemoteMove(int, int, int, int, String)
     * @see #setTurnContext(TurnPublisher, String)
     */
    @Override
    public void setMovePublisher(MovePublisher movePublisher) {
        this.movePublisher = movePublisher;
    }

    /**
     * Applica l'aggiornamento del turno ricevuto da un emulatore remoto.
     * Aggiorna l'indice del turno e lo stato dei controlli solo se il nuovo indice
     * &egrave; valido (compreso tra 0 e la dimensione della lista dei partecipanti).
     *
     * @param newTurnIndex il nuovo indice del turno (base 0) nella lista dei partecipanti
     * @param playerName   il nome utente del giocatore a cui spetta il turno
     */
    @Override
    public void onRemoteTurnUpdate(int newTurnIndex, String playerName) {
        if (newTurnIndex >= 0 && newTurnIndex < players.size()) {
            this.turnIndex = newTurnIndex;
            updateTurnLabel();
            applyTurnControls();
        }
    }

    /**
     * Applica una mossa ricevuta da un emulatore remoto per mantenere sincronizzato
     * lo stato locale della scacchiera. Se la cella di origine &egrave; vuota la mossa
     * viene ignorata. Registra automaticamente la cattura se il messaggio remoto riporta
     * un pezzo catturato o se la cella di destinazione contiene un pezzo avversario.
     *
     * @param fromRow       riga di origine (base 0)
     * @param fromCol       colonna di origine (base 0)
     * @param toRow         riga di destinazione (base 0)
     * @param toCol         colonna di destinazione (base 0)
     * @param capturedPiece glifo Unicode del pezzo catturato, oppure {@code null}
     *                      o stringa vuota se non &egrave; avvenuta cattura
     * @see #setMovePublisher(MovePublisher)
     */
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

    /**
     * Arresta la partita disabilitando tutti i controlli e aggiornando
     * l'etichetta del turno con il messaggio di fine partita.
     */
    @Override
    public void onGameStopped() {
        endTurnButton.setDisable(true);
        captureButton.setDisable(true);
        captureCombo.setDisable(true);
        turnLabel.setText("Match ended");
        turnLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #f39c12; -fx-font-weight: bold;");
    }

    /**
     * Inizializza la scacchiera con la disposizione iniziale dei pezzi e
     * configura gli eventi di drag-and-drop per ogni cella.
     */
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

    /**
     * Verifica se &egrave; il turno del giocatore locale.
     *
     * @return {@code true} se la lista dei partecipanti non &egrave; vuota,
     *         il nome utente corrente non &egrave; vuoto e corrisponde al
     *         giocatore al turno corrente; {@code false} altrimenti
     */
    private boolean isMyTurn() {
        return !players.isEmpty()
                && !currentUser.isBlank()
                && currentUser.equals(players.get(turnIndex));
    }

    /**
     * Verifica se il pezzo specificato appartiene al giocatore di turno.
     * Il giocatore all'indice pari (turno 0, 2, ...) controlla i pezzi bianchi;
     * il giocatore all'indice dispari (turno 1, 3, ...) controlla i pezzi neri.
     *
     * @param piece glifo Unicode del pezzo da verificare; non deve essere {@code null}
     * @return {@code true} se il pezzo appartiene al giocatore corrente, {@code false} altrimenti
     * @see #isMyTurn()
     */
    private boolean isMyPiece(String piece) {
        boolean white = turnIndex % 2 == 0;
        return white ? WHITE_PIECES.contains(piece) : BLACK_PIECES.contains(piece);
    }

    /**
     * Termina il turno corrente e passa al giocatore successivo.
     * Aggiorna l'indicatore del turno, lo stato dei controlli e trasmette
     * il cambio di turno agli emulatori remoti. Se la lista dei partecipanti
     * &egrave; vuota, non esegue alcuna operazione.
     */
    private void endTurn() {
        if (players.isEmpty()) return;
        turnIndex = (turnIndex + 1) % players.size();
        updateTurnLabel();
        applyTurnControls();
        broadcastTurn();
    }

    /**
     * Trasmette il cambio di turno agli emulatori remoti tramite il
     * {@link TurnPublisher} se presente e se la lista dei partecipanti
     * non &egrave; vuota.
     */
    private void broadcastTurn() {
        if (turnPublisher != null && !players.isEmpty()) {
            turnPublisher.publish(turnIndex, players.get(turnIndex));
        }
    }

    /**
     * Abilita i controlli di fine turno e cattura solo quando &egrave; il turno
     * del giocatore locale. Previene l'interazione contemporanea di pi&ugrave;
     * emulatori sullo stesso turno.
     */
    private void applyTurnControls() {
        boolean myTurn = !players.isEmpty()
                && !currentUser.isBlank()
                && currentUser.equals(players.get(turnIndex));
        endTurnButton.setDisable(!myTurn);
        captureButton.setDisable(!myTurn);
        captureCombo.setDisable(!myTurn);
    }

    /**
     * Aggiorna l'etichetta del turno con il nome del giocatore corrente e il colore
     * corrispondente (Bianco o Nero). Se la lista dei partecipanti &egrave; vuota,
     * non esegue alcuna operazione.
     */
    private void updateTurnLabel() {
        if (players.isEmpty()) return;
        String current = players.get(turnIndex);
        String color = turnIndex % 2 == 0 ? "White ♔" : "Black ♚";
        turnLabel.setText("Turn of: " + current + " (" + color + ")");
        turnLabel.setStyle("-fx-font-size: 14; -fx-text-fill: " + (turnIndex % 2 == 0 ? "#f0d9b5" : "#555") + "; -fx-font-weight: bold;");
    }

    /**
     * Restituisce il nome del giocatore corrente.
     *
     * @return il nome utente del giocatore al turno corrente, oppure {@code null}
     *         se la lista dei partecipanti &egrave; vuota
     * @see #getWinnerId()
     */
    public String getCurrentPlayer() {
        if (players.isEmpty()) return null;
        return players.get(turnIndex);
    }

    /**
     * Restituisce l'identificativo del vincitore della partita.
     * Per questo emulatore, il vincitore &egrave; il giocatore corrente
     * (l'ultimo ad aver mosso).
     *
     * @return il nome utente del giocatore corrente, oppure {@code null}
     *         se la lista dei partecipanti &egrave; vuota
     * @see #getCurrentPlayer()
     * @see #getResultData()
     */
    public String getWinnerId() {
        return getCurrentPlayer();
    }

    /**
     * Restituisce i dati di risultato della partita nel formato
     * "giocatore1:numeroCatture,giocatore2:numeroCatture".
     *
     * @return stringa con coppie "giocatore:numeroCatture" separate da virgola;
     *         restituisce stringa vuota se non ci sono partecipanti
     * @see #getWinnerId()
     */
    public String getResultData() {
        StringBuilder sb = new StringBuilder();
        for (String p : players) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p).append(":").append(capturedPieces.size());
        }
        return sb.toString();
    }
}
