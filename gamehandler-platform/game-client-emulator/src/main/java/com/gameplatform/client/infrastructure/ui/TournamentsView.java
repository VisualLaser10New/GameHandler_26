package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.application.service.PlayerTournamentFlow;
import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.infrastructure.ui.components.ErrorPane;
import com.gameplatform.client.infrastructure.ui.components.LoadingIndicator;
import com.gameplatform.client.infrastructure.ui.components.StalenessBadge;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.GameSessionDto;
import com.gameplatform.shared.dto.TournamentDetailDto;
import com.gameplatform.shared.dto.TournamentMatchDto;
import com.gameplatform.shared.dto.TournamentParticipantViewDto;
import com.gameplatform.shared.dto.TournamentStandingDto;
import com.gameplatform.shared.dto.TournamentSummaryDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Tournaments view (PIANO §7.C line 738-740).
 * <p>
 * Combines four panels in a master-detail layout:
 * <ol>
 *   <li><b>Summary list</b> (left) — polls {@code GET /api/tournaments}
 *       and renders {@link TournamentSummaryDto} rows.</li>
 *   <li><b>Detail</b> (centre) — {@code GET /api/tournaments/{id}}
 *       returns the aggregated {@link TournamentDetailDto}; each
 *       sub-resource endpoint ({@code /standings}, {@code /matches},
 *       {@code /participants}) is also demonstrated through the flow.</li>
 *   <li><b>Registration panel</b> — {@code POST /api/tournaments/{id}/participants}
 *       opens a row in {@code admin_requests_local} with
 *       {@code status=PENDING}; the user is then redirected to
 *       {@code VIEW_ADMIN_REQUESTS} for polling.</li>
 *   <li><b>"My matches" panel</b> — {@code GET /api/players/tournaments/me/matches}
 *       + {@code POST /api/players/tournaments/matches/{matchId}/start}.</li>
 * </ol>
 */
public class TournamentsView {

    private static final long STALE_THRESHOLD_MS = Long.parseLong(
            System.getProperty("ui.stale-threshold-ms", "300000"));

    private final PlayerTournamentFlow flow;
    private final VBox root;
    private final ListView<TournamentSummaryDto> summaryList;
    private final ObservableList<TournamentSummaryDto> summaries;
    private final ListView<TournamentStandingDto> standingsList;
    private final ListView<TournamentMatchDto> matchesList;
    private final ListView<TournamentParticipantViewDto> participantsList;
    private final ListView<TournamentMatchDto> myMatchesList;
    private final Label statusLabel = new Label();
    private final Label detailHeader = new Label();
    private final LoadingIndicator loading = new LoadingIndicator();
    private final StalenessBadge staleness;
    private final ErrorPane errorPane = new ErrorPane();
    private volatile Instant latestUpdatedAt;
    private Consumer<String> onNavigate;   // accepts a VIEW_* constant

    public TournamentsView() {
        this(PlayerTournamentFlow::new);
    }

    /** Test hook — accepts a factory so subclasses can stub the flow. */
    public TournamentsView(java.util.function.Supplier<PlayerTournamentFlow> flowFactory) {
        this.flow = flowFactory.get();
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");

        Label title = new Label("Tournaments");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");

        // ── master / detail split ─────────────────────────────────────
        summaries = FXCollections.observableArrayList();
        summaryList = new ListView<>(summaries);
        summaryList.setCellFactory(param -> new ListCell<>() {
            @Override protected void updateItem(TournamentSummaryDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.name() + "  [" + item.gameType() + "]  —  " + item.status()
                        + (item.startsAt() == null ? "" : "  starts " + item.startsAt()));
                setStyle("-fx-text-fill: #eee;");
            }
        });
        summaryList.setPrefWidth(280);
        summaryList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> { if (sel != null) showTournament(sel.tournamentId()); });

        // Detail panels — three small lists arranged in a vertical column.
        standingsList = new ListView<>(FXCollections.observableArrayList());
        standingsList.setCellFactory(p -> new ListCell<>() {
            @Override protected void updateItem(TournamentStandingDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.displayName() + "  W=" + item.wins() + " L=" + item.losses()
                        + " PTS=" + item.points() + (item.rank() == null ? "" : "  rank=" + item.rank()));
                setStyle("-fx-text-fill: #eee;");
            }
        });

        matchesList = new ListView<>(FXCollections.observableArrayList());
        matchesList.setCellFactory(p -> new ListCell<>() {
            @Override protected void updateItem(TournamentMatchDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText("Round " + item.round() + ": " + item.participantA()
                        + " vs " + item.participantB()
                        + " [" + item.status() + "]"
                        + (item.winner() == null ? "" : " → " + item.winner()));
                setStyle("-fx-text-fill: #eee;");
            }
        });

        participantsList = new ListView<>(FXCollections.observableArrayList());
        participantsList.setCellFactory(p -> new ListCell<>() {
            @Override protected void updateItem(TournamentParticipantViewDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.displayName() + (item.isTeam() ? " (team)" : "")
                        + " — registered on " + item.registeredAt());
                setStyle("-fx-text-fill: #eee;");
            }
        });

        // central detail column
        VBox detail = new VBox(10,
                titledPane("Standings", standingsList),
                titledPane("Bracket (matches)",       matchesList),
                titledPane("Participants",           participantsList));
        detailHeader.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #3498db;");
        detail.getChildren().add(0, detailHeader);

        // ── toolbar: refresh + create-team-registration form + "my matches" button ──
        Button refreshBtn = new Button("Refresh tournaments");
        refreshBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6 16;");
        refreshBtn.setOnAction(e -> loadTournaments());

        Button registerSelfBtn = new Button("Register me (self)");
        registerSelfBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 6 16;");
        registerSelfBtn.setOnAction(e -> registerSelf());

        Button registerTeamBtn = new Button("Register team");
        registerTeamBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-padding: 6 16;");
        registerTeamBtn.setOnAction(e -> registerTeam());

        Button myMatchesBtn = new Button("My matches / Start");
        myMatchesBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 6 16;");
        myMatchesBtn.setOnAction(e -> loadMyMatches());

        Button startSelectedBtn = new Button("Start selected match");
        startSelectedBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 6 16;");
        startSelectedBtn.setOnAction(e -> startSelectedMatch());

        HBox toolbar = new HBox(8, refreshBtn, registerSelfBtn, registerTeamBtn,
                myMatchesBtn, startSelectedBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // ── "My matches" panel (right column) ───────────────────────────
        myMatchesList = new ListView<>(FXCollections.observableArrayList());
        myMatchesList.setCellFactory(p -> new ListCell<>() {
            @Override protected void updateItem(TournamentMatchDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.id() + "  " + item.participantA() + " vs " + item.participantB()
                        + "  [" + item.status() + "]");
                setStyle("-fx-text-fill: #eee;");
            }
        });
        VBox myColumn = titledPane("My matches (SCHEDULED)", myMatchesList);

        SplitPane split = new SplitPane();
        split.setStyle("-fx-background-color: #1e1e1e;");
        split.getItems().addAll(summaryList, detail, myColumn);
        split.setDividerPositions(0.27, 0.68);
        VBox.setVgrow(split, Priority.ALWAYS);

        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");
        staleness = new StalenessBadge(() -> Optional.ofNullable(latestUpdatedAt), STALE_THRESHOLD_MS);

        content.getChildren().addAll(title, toolbar, split, new HBox(statusLabel, staleness));

        StackPane stack = new StackPane(content, loading, errorPane);
        StackPane.setAlignment(loading, Pos.CENTER);
        StackPane.setAlignment(errorPane, Pos.CENTER);
        errorPane.setVisible(false);
        root = new VBox(stack);
        root.setStyle("-fx-padding: 0; -fx-background-color: #1e1e1e;");
    }

    public Parent getView() {
        return root;
    }

    /** Registers the navigation callback invoked when "View My Requests" is requested. */
    public void setOnNavigate(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
    }

    public void refresh() {
        loadTournaments();
    }

    // ───────────────────────────── summary list ──────────────────────────

    private void loadTournaments() {
        loading.show();
        statusLabel.setText("Loading tournaments...");
        flow.listTournaments()
                .thenAccept(list -> Platform.runLater(() -> {
                    summaries.setAll(list == null ? List.of() : list);
                    latestUpdatedAt = computeMaxUpdatedAt(list);
                    staleness.refresh();
                    statusLabel.setText((list == null ? 0 : list.size()) + " tournaments");
                    errorPane.setVisible(false);
                    loading.hide();
                }))
                .exceptionally(ex -> errorWithRetry(this::loadTournaments, ex));
    }

    private static Instant computeMaxUpdatedAt(List<TournamentSummaryDto> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .map(TournamentSummaryDto::updatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());
    }

    // ───────────────────────────── detail drill-down ─────────────────────

    private void showTournament(String tournamentId) {
        loading.show();
        detailHeader.setText("Detail: " + tournamentId);
        flow.getTournament(tournamentId)
                .thenAccept(detail -> Platform.runLater(() -> {
                    renderDetail(detail);
                    loading.hide();
                }))
                .exceptionally(this::error);
    }

    @SuppressWarnings("unchecked")
    private void renderDetail(TournamentDetailDto detail) {
        if (detail == null || detail.summary() == null) {
            detailHeader.setText("Detail: not available");
            standingsList.getItems().clear();
            matchesList.getItems().clear();
            participantsList.getItems().clear();
            return;
        }
        var s = detail.summary();
        detailHeader.setText("Detail: " + s.name() + " [" + s.gameType() + "] " + s.status()
                + "  (" + s.participantsCount() + " registered)"
                + "\nStarts at " + s.startsAt());
        standingsList.setItems(FXCollections.observableArrayList(detail.standings() == null ? List.of() : detail.standings()));
        matchesList.setItems(FXCollections.observableArrayList(detail.matches() == null ? List.of() : detail.matches()));
        participantsList.setItems(FXCollections.observableArrayList(detail.participants() == null ? List.of() : detail.participants()));
    }

    // ───────────────────────────── registration ──────────────────────────

    private void registerSelf() {
        TournamentSummaryDto sel = summaryList.getSelectionModel().getSelectedItem();
        if (sel == null) {
            statusLabel.setText("Select a tournament before registering");
            return;
        }
        statusLabel.setText("Registration in progress (PENDING)...");
        loading.show();
        flow.registerSelf(sel.tournamentId())
                .thenAccept(req -> Platform.runLater(() -> {
                    loading.hide();
                    String msg = req == null
                            ? "Registration started"
                            : "Registration PENDING (reqId=" + req.requestId() + ")";
                    statusLabel.setText(msg + " — see Admin Requests for polling");
                    // Banner / redirect: navigate to VIEW_ADMIN_REQUESTS so the
                    // user can poll the outcome.
                    if (onNavigate != null) onNavigate.accept(NavbarController.VIEW_ADMIN_REQUESTS);
                }))
                .exceptionally(this::error);
    }

    private void registerTeam() {
        TournamentSummaryDto sel = summaryList.getSelectionModel().getSelectedItem();
        if (sel == null) {
            statusLabel.setText("Select a tournament before registering");
            return;
        }
        TextInputDialog teamName = new TextInputDialog();
        teamName.setHeaderText("Team name (treating self as captain)");
        teamName.setContentText("Team name:");
        teamName.showAndWait().ifPresent(name -> {
            // For demo purposes we register a single-member team (the
            // current player); the body accepts null teamMembers.
            var body = new com.gameplatform.shared.dto.RegisterTournamentParticipantDto(name, List.of());
            loading.show();
            flow.register(sel.tournamentId(), body)
                    .thenAccept(req -> Platform.runLater(() -> {
                        loading.hide();
                        statusLabel.setText("Team registration PENDING (reqId="
                                + (req == null ? "?" : req.requestId()) + ")");
                        if (onNavigate != null) onNavigate.accept(NavbarController.VIEW_ADMIN_REQUESTS);
                    }))
                    .exceptionally(this::error);
        });
    }

    // ───────────────────────────── my matches + start ────────────────────

    @SuppressWarnings("unchecked")
    private void loadMyMatches() {
        loading.show();
        statusLabel.setText("Loading my matches...");
        flow.myMatches()
                .thenAccept(matches -> Platform.runLater(() -> {
                    myMatchesList.setItems(FXCollections.observableArrayList(matches == null ? List.of() : matches));
                    statusLabel.setText((matches == null ? 0 : matches.size()) + " my matches");
                    loading.hide();
                }))
                .exceptionally(this::error);
    }

    private void startSelectedMatch() {
        TournamentMatchDto sel = myMatchesList.getSelectionModel().getSelectedItem();
        if (sel == null) {
            statusLabel.setText("First select a match from the 'My matches' list");
            return;
        }
        loading.show();
        statusLabel.setText("Starting match " + sel.id() + "...");
        flow.startMatch(sel.id(), sel.gameId())
                .thenAccept(session -> Platform.runLater(() -> {
                    loading.hide();
                    GameSessionDto s = session;
                    String msg = s == null
                            ? "Match started (no session details)"
                            : "Match started! sessionId=" + s.id() + " game=" + s.gameId()
                                    + " status=" + s.status();
                    statusLabel.setText(msg);
                }))
                .exceptionally(this::error);
    }

    // ───────────────────────────── helpers ───────────────────────────────

    private static VBox titledPane(String headerText, ListView<?> lv) {
        Label h = new Label(headerText);
        h.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
        VBox box = new VBox(4, h, lv);
        VBox.setVgrow(lv, Priority.ALWAYS);
        box.setPadding(new Insets(4));
        return box;
    }

    private Void error(Throwable ex) {
        loading.hide();
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        Platform.runLater(() -> statusLabel.setText("Error: " + msg));
        return null;
    }

    /**
     * Variant of {@link #error(Throwable)} that surfaces the failure through
     * the reusable {@link ErrorPane} overlay (PIANO §7.C — ErrorPane gap).
     * Used for the summary-list fetch so a server-down/timeout is no longer
     * rendered as an empty list with a tiny status line; the user gets a
     * visible error card with a Retry button wired to {@code retryAction}.
     */
    private Void errorWithRetry(Runnable retryAction, Throwable ex) {
        loading.hide();
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        Platform.runLater(() -> {
            statusLabel.setText("Error: " + msg);
            errorPane.show("Tournaments unavailable", msg, retryAction);
            errorPane.setVisible(true);
        });
        return null;
    }
}