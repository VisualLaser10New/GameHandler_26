package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.security.HttpClientHelper;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Role-aware JavaFX navbar (PIANO §7.C line 732-733).
 * <p>
 * Rebuilds the visible buttons every time the user authenticates:
 * <ul>
 *   <li><b>PLAYER</b> — Games · My Stats · My Matches · Tournaments</li>
 *   <li><b>LOCAL_ADMIN</b> — adds Local Dashboard</li>
 *   <li><b>GAME_ADMIN</b> — adds Game Admin (CRUD definitions)</li>
 *   <li><b>PLATFORM_ADMIN</b> — adds Users & Roles · Tournaments Admin ·
 *       Server Monitor · Requests · plus read-only entries for the
 *       LOCAL_ADMIN/GAME_ADMIN dashboards</li>
 * </ul>
 * Multi-role users see the union of the entries with de-duplication: an
 * identical target view appears as a single button bound to the same
 * handler (the underlying dashboard itself decides which buttons to show
 * based on the role, so a PLATFORM_ADMIN visiting the LocalAdmin
 * Dashboard gets read-only mode).
 * <p>
 * The bar also exposes a Logout button on the right edge.
 */
public class NavbarController {

    /** Single source of truth for the view names — shared with {@link MainView}. */
    public static final String VIEW_LOGIN             = "login";
    public static final String VIEW_SIGNUP            = "signup";
    public static final String VIEW_GAME_SELECTION    = "game_selection";
    public static final String VIEW_LOBBY             = "lobby";
    public static final String VIEW_GAME_PLAY         = "game_play";
    public static final String VIEW_STATISTICS         = "statistics";
    public static final String VIEW_TOURNAMENTS        = "tournaments";
    public static final String VIEW_TOURNAMENT_DETAIL  = "tournament_detail";
    public static final String VIEW_MY_STATISTICS      = "my_statistics";
    public static final String VIEW_MY_MATCHES         = "my_matches";
    public static final String VIEW_ADMIN_LOCAL        = "admin_local";
    public static final String VIEW_ADMIN_GAME         = "admin_game";
    public static final String VIEW_ADMIN_PLATFORM     = "admin_platform";
    public static final String VIEW_ADMIN_REQUESTS    = "admin_requests";

    private final HBox bar = new HBox(8);
    /** Maps target VIEW_* constant → button so we can de-duplicate when rebuilding. */
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private Consumer<String> navigateHandler;
    private Runnable onLogout;

    public NavbarController() {
        bar.setStyle("-fx-padding: 8 12; -fx-background-color: #151515;"
                + " -fx-border-color: #333; -fx-border-width: 0 0 1 0;");
        bar.setAlignment(Pos.CENTER_LEFT);
    }

    public javafx.scene.Node getNode() {
        return bar;
    }

    /** Registers the navigation callback invoked when the user clicks a button. */
    public void setOnNavigate(Consumer<String> navigateHandler) {
        this.navigateHandler = navigateHandler;
    }

    /** Registers the logout callback (clears session + navigates to login). */
    public void setOnLogout(Runnable callback) {
        this.onLogout = callback;
    }

    /**
     * Rebuilds the bar from the current {@link HttpClientHelper#getRoles()};
     * safe to call on any thread — wraps the scene-graph mutation in
     * {@code Platform.runLater}.
     */
    public void rebuild() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::rebuild);
            return;
        }
        buttons.clear();
        List<String> roles = HttpClientHelper.getRoles();
        if (roles == null || roles.isEmpty()) {
            renderBar();
            return;
        }

        // PLAYER always sees the player entries (LOCAL/GAME/PLATFORM admins
        // inherit the player scope since they are typically also players).
        if (roles.contains("PLAYER") || roles.contains("LOCAL_ADMIN")
                || roles.contains("GAME_ADMIN") || roles.contains("PLATFORM_ADMIN")) {
            addButton("Games",       VIEW_GAME_SELECTION);
            addButton("My Stats",    VIEW_MY_STATISTICS);
            addButton("My Matches",   VIEW_MY_MATCHES);
            addButton("Tournaments", VIEW_TOURNAMENTS);
            addButton("Aggregated Stats", VIEW_STATISTICS);
        }
        if (roles.contains("LOCAL_ADMIN") || roles.contains("PLATFORM_ADMIN")) {
            addButton("Local Dashboard", VIEW_ADMIN_LOCAL);
        }
        if (roles.contains("GAME_ADMIN") || roles.contains("PLATFORM_ADMIN")) {
            addButton("Game Admin", VIEW_ADMIN_GAME);
        }
        if (roles.contains("PLATFORM_ADMIN")) {
            addButton("Platform Admin", VIEW_ADMIN_PLATFORM);
            addButton("Admin Requests",  VIEW_ADMIN_REQUESTS);
        }
        renderBar();
    }

    private void addButton(String label, String targetView) {
        // De-duplicate when the same target would otherwise appear twice
        // (e.g. PLAYER + GAME_ADMIN both want "Games"): LinkedHashMap keeps
        // the first insert order.
        if (buttons.containsKey(targetView)) return;
        Button btn = new Button(label);
        btn.setStyle("-fx-background-color: #333; -fx-text-fill: #ccc;"
                + " -fx-padding: 4 14; -fx-background-radius: 3;");
        btn.setOnAction(e -> navigate(targetView));
        buttons.put(targetView, btn);
    }

    private void renderBar() {
        bar.getChildren().clear();
        for (Button b : new ArrayList<>(buttons.values())) {
            bar.getChildren().add(b);
        }
        // Logout button on the right edge — uses a spacer Region that grows.
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().add(spacer);
        Button logout = new Button("Logout");
        logout.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white;"
                + " -fx-padding: 4 14; -fx-background-radius: 3;");
        logout.setOnAction(e -> {
            if (onLogout != null) onLogout.run();
        });
        bar.getChildren().add(logout);
    }

    private void navigate(String viewName) {
        if (navigateHandler != null) navigateHandler.accept(viewName);
    }
}