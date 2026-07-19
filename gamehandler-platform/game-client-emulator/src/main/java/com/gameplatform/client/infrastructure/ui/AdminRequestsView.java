package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.infrastructure.ui.components.LoadingIndicator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.gameplatform.shared.dto.AdminRequestDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Vista di polling delle richieste admin dell'utente autenticato.
 * <p>
 * Recupera periodicamente (ogni 8 secondi) le richieste tramite
 * {@code GET /api/admin/requests} e le visualizza come schede il cui
 * stato visivo deriva dal campo {@code status}: PENDING mostra un
 * indicatore di attesa, COMPLETED mostra un segno di spunta verde,
 * FAILED mostra un banner rosso con il motivo dell'errore.
 */
public class AdminRequestsView {

    private static final long POLL_INTERVAL_MS = 8_000L;

    private final VBox root;
    private final VBox cardsContainer;
    private final Label statusLabel = new Label();
    private final LoadingIndicator loading = new LoadingIndicator();
    private final ObservableList<AdminRequestDto> rows = FXCollections.observableArrayList();

    private javafx.animation.Timeline poller;
    private volatile Instant latestUpdatedAt;

    /**
     * Costruisce la vista delle richieste admin.
     * <p>
     * Inizializza il layout con titolo, pulsante di refresh manuale,
     * contenitore per le schede delle richieste e indicatore di caricamento.
     */
    public AdminRequestsView() {
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");

        Label title = new Label("Admin requests status");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");
        Label sub = new Label("Polling GET /api/admin/requests every " + (POLL_INTERVAL_MS / 1000) + "s");
        sub.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");

        Button refreshBtn = new Button("Refresh now");
        refreshBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6 16;");
        refreshBtn.setOnAction(e -> refresh());

        HBox toolbar = new HBox(8, refreshBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        cardsContainer = new VBox(6);

        ScrollPane scroll = new ScrollPane(cardsContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #1e1e1e; -fx-background-color: #1e1e1e;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");

        content.getChildren().addAll(title, sub, toolbar, scroll, statusLabel);

        StackPane stack = new StackPane(content, loading);
        StackPane.setAlignment(loading, Pos.CENTER);
        root = new VBox(stack);
        root.setStyle("-fx-padding: 0; -fx-background-color: #1e1e1e;");
    }

    /**
     * Restituisce il nodo radice JavaFX per questa vista.
     *
     * @return il nodo {@link Parent} radice
     */
    public Parent getView() {
        return root;
    }

    /**
     * Avvia il polling periodico delle richieste admin.
     * <p>
     * Invocato da {@link MainView} quando l'utente naviga verso questa vista.
     * Esegue un refresh immediato e avvia un timer con intervallo di 8 secondi.
     */
    public void onEnter() {
        refresh();
        if (poller == null) {
            poller = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                    Duration.millis(POLL_INTERVAL_MS), e -> refresh()));
            poller.setCycleCount(javafx.animation.Animation.INDEFINITE);
        }
        poller.play();
    }

    /**
     * Arresta il polling periodico delle richieste admin.
     * <p>
     * Invocato da {@link MainView} quando l'utente abbandona questa vista.
     * Non produce effetti se il poller non era stato avviato.
     */
    public void onLeave() {
        if (poller != null) poller.stop();
    }

    /**
     * Recupera le richieste admin dal server e aggiorna la visualizzazione.
     * <p>
     * Effettua una chiamata asincrona {@code GET /api/admin/requests}.
     * In caso di risposta nulla imposta una lista vuota. Aggiorna il
     * timestamp {@code latestUpdatedAt} con il valore più recente tra
     * i campi {@code completedAt} e {@code createdAt} delle richieste
     * restituite.
     */
    private void refresh() {
        loading.show();
        statusLabel.setText("Loading requests...");
        ApiClient.instance().get("/api/admin/requests", new TypeReference<List<AdminRequestDto>>() {})
                .thenAccept(list -> Platform.runLater(() -> {
                    rows.setAll(list == null ? List.<AdminRequestDto>of() : list);
                    latestUpdatedAt = list == null ? null
                            : list.stream()
                                    .map(a -> a.completedAt() != null ? a.completedAt() : a.createdAt())
                                    .filter(java.util.Objects::nonNull)
                                    .max(Comparator.naturalOrder())
                                    .orElse(Instant.now());
                    render();
                    statusLabel.setText((list == null ? 0 : list.size()) + " requests");
                    loading.hide();
                }))
                .exceptionally(this::error);
    }

    /**
     * Aggiorna il contenitore delle schede con le richieste correnti.
     * <p>
     * Se la lista {@code rows} è vuota mostra un messaggio informativo.
     * Altrimenti genera una scheda per ogni richiesta tramite
     * {@link #buildCard(AdminRequestDto)}.
     */
    private void render() {
        cardsContainer.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label("No pending requests");
            empty.setStyle("-fx-text-fill: #999;");
            cardsContainer.getChildren().add(empty);
            return;
        }
        for (AdminRequestDto r : rows) {
            cardsContainer.getChildren().add(buildCard(r));
        }
    }

    /**
     * Costruisce una scheda visiva per una richiesta admin.
     * <p>
     * La scheda mostra il tipo di evento, l'identificativo richiesta,
     * i metadati (ruolo, edificio, date di creazione/completamento) e
     * lo stato corrente con indicatore visivo appropriato. Se la
     * richiesta contiene un identificativo torneo, aggiunge un campo
     * selezionabile con pulsante di copia.
     *
     * @param r la richiesta admin da visualizzare; non null
     * @return una {@link VBox} contenente la scheda renderizzata
     */
    private VBox buildCard(AdminRequestDto r) {
        VBox card = new VBox(4);
        card.setStyle("-fx-padding: 10; -fx-background-color: #2a2a2a;"
                + " -fx-border-color: #444; -fx-background-radius: 4;");
        card.setPrefWidth(Region.USE_COMPUTED_SIZE);

        Label header = new Label(r.eventType());
        header.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");

        Label reqIdCaption = new Label("reqId:");
        reqIdCaption.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");
        TextField requestIdField = selectableField(r.requestId());
        HBox requestRow = new HBox(6, reqIdCaption, requestIdField);
        requestRow.setAlignment(Pos.CENTER_LEFT);

        Label meta = new Label("role=" + r.actingRole()
                + "  ·  building=" + r.buildingId()
                + "  ·  created=" + (r.createdAt() == null ? "?" : r.createdAt())
                + (r.completedAt() == null ? "" : "  ·  completed=" + r.completedAt()));
        meta.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");

        HBox statusRow = new HBox(8);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        String status = r.status() == null ? "" : r.status().toUpperCase();
        Label state = new Label();
        switch (status) {
            case "PENDING" -> {
                ProgressIndicator pi = new ProgressIndicator();
                pi.setMaxSize(20, 20);
                pi.setStyle("-fx-progress-color: #f39c12;");
                state.setText("waiting for confirmation…");
                state.setStyle("-fx-text-fill: #f39c12;");
                statusRow.getChildren().addAll(pi, state);
            }
            case "COMPLETED" -> {
                state.setText("✓ COMPLETED — " + readableResult(r));
                state.setStyle("-fx-text-fill: #2ecc71;");
                statusRow.getChildren().add(state);
            }
            case "FAILED" -> {
                state.setText("Operation not confirmed within timeout — retry/recheck"
                        + "  (reason: " + readableResult(r) + ")");
                state.setStyle("-fx-text-fill: #e74c3c;");
                statusRow.getChildren().add(state);
            }
            default -> {
                state.setText(status);
                state.setStyle("-fx-text-fill: #ccc;");
                statusRow.getChildren().add(state);
            }
        }

        card.getChildren().addAll(header, requestRow, meta, statusRow);

        String tournamentId = extractTournamentId(r);
        if (tournamentId != null) {
            Label tourCaption = new Label("Tournament id:");
            tourCaption.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
            TextField tournamentIdField = selectableField(tournamentId);
            HBox.setHgrow(tournamentIdField, Priority.ALWAYS);
            Button copyBtn = new Button("Copy");
            copyBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 4 10;");
            copyBtn.setOnAction(e -> copyToClipboard(tournamentId, copyBtn));
            HBox tourRow = new HBox(6, tourCaption, tournamentIdField, copyBtn);
            tourRow.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(tourRow);
        }

        return card;
    }

    /**
     * Crea un campo di testo non modificabile con testo selezionabile.
     * <p>
     * Il campo restituito non è editabile e non riceve il focus tramite
     * tabulazione, ma permette la selezione e la copia del testo.
     *
     * @param value il testo da visualizzare; se null viene sostituito
     *              con una stringa vuota
     * @return un {@link TextField} in sola lettura
     */
    private static TextField selectableField(String value) {
        TextField tf = new TextField(value == null ? "" : value);
        tf.setEditable(false);
        tf.setFocusTraversable(false);
        tf.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-padding: 4 6; -fx-background-radius: 4;");
        return tf;
    }

    /**
     * Copia un testo negli appunti di sistema e mostra un feedback visivo.
     * <p>
     * Dopo la copia, il testo del pulsante viene temporaneamente sostituito
     * con "Copied" per 1,2 secondi, quindi ripristinato al valore originale.
     *
     * @param text   il testo da copiare; non null
     * @param source il pulsante su cui mostrare il feedback visivo; non null
     */
    private static void copyToClipboard(String text, Button source) {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
        String original = source.getText();
        source.setText("Copied");
        source.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 4 10;");
        javafx.animation.Timeline back = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1.2),
                        e -> { source.setText(original);
                               source.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 4 10;"); }));
        back.play();
    }

    /**
     * Estrae l'identificativo del torneo da una richiesta admin.
     * <p>
     * Preferisce il campo {@code resultData} JSON (disponibile quando la
     * richiesta è COMPLETED) e, in assenza, utilizza il campo {@code payload}
     * JSON per eventi lifecycle/registrazione che incorporano
     * {@code tournamentId} in anticipo.
     *
     * @param r la richiesta admin da cui estrarre l'identificativo; non null
     * @return l'identificativo del torneo, o null se non presente in
     *         nessuno dei due campi JSON
     */
    static String extractTournamentId(AdminRequestDto r) {
        String tid = readJsonField(r.resultData(), "tournamentId");
        if (tid != null && !tid.isBlank()) return tid;
        return readJsonField(r.payload(), "tournamentId");
    }

    /**
     * Legge il valore di un campo JSON da una stringa.
     *
     * @param json  la stringa JSON da analizzare; può essere null o vuota
     * @param field il nome del campo da estrarre; non null
     * @return il valore testuale del campo, o null se il JSON è nullo,
     *         vuoto, non contiene il campo, o il campo è nullo
     */
    private static String readJsonField(String json, String field) {
        if (json == null || json.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = m.readTree(json);
            if (node != null && node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Estrae un messaggio leggibile dal campo {@code resultData}.
     * <p>
     * Cerca prima il campo {@code reason}, poi {@code status} nel JSON
     * del risultato. Se nessuno dei due è presente, tronca il testo
     * a 80 caratteri.
     *
     * @param r la richiesta admin contenente il risultato; non null
     * @return una stringa leggibile che descrive il risultato; non null
     */
    private static String readableResult(AdminRequestDto r) {
        String rd = r.resultData();
        if (rd == null || rd.isBlank()) return r.status();
        // Simple heuristic: trim surrounding quotes / braces; look for "reason":"…"
        try {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = m.readTree(rd);
            if (node.has("reason")) return node.get("reason").asText();
            if (node.has("status")) return node.get("status").asText();
        } catch (Exception ignored) {}
        return rd.length() > 80 ? rd.substring(0, 80) + "…" : rd;
    }

    /**
     * Gestisce un errore asincrono delle chiamate API.
     * <p>
     * Nasconde l'indicatore di caricamento, risale la catena delle
     * eccezioni fino alla causa radice e aggiorna l'etichetta di
     * stato con il messaggio di errore.
     *
     * @param ex l'eccezione da gestire; può essere null
     * @return sempre null
     */
    private Void error(Throwable ex) {
        loading.hide();
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        Platform.runLater(() -> statusLabel.setText("Error: " + msg));
        return null;
    }
}