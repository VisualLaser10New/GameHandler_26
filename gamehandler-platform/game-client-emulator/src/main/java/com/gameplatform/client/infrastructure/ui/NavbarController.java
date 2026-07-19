package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.security.HttpClientHelper;
import com.gameplatform.shared.domain.security.Role;
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
 * Barra di navigazione JavaFX sensibile al ruolo dell'utente.
 * <p>
 * Ricostruisce i pulsanti visibili ogni volta che l'utente si autentica:
 * <ul>
 *   <li><b>PLAYER</b> — Games · My Stats · My Matches · Tournaments</li>
 *   <li><b>LOCAL_ADMIN</b> — Games · Aggregated Stats · Local Dashboard</li>
 *   <li><b>GAME_ADMIN</b> — Game Admin</li>
 *   <li><b>PLATFORM_ADMIN</b> — tutte le voci (superuser)</li>
 * </ul>
 * Gli utenti con più ruoli vedono l'unione delle voci con deduplicazione.
 * La barra espone anche un pulsante Logout sul lato destro.
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

    /**
     * Costruisce la barra di navigazione.
     * <p>
     * Imposta lo stile scuro della barra e l'allineamento a sinistra.
     */
    public NavbarController() {
        bar.setStyle("-fx-padding: 8 12; -fx-background-color: #151515;"
                + " -fx-border-color: #333; -fx-border-width: 0 0 1 0;");
        bar.setAlignment(Pos.CENTER_LEFT);
    }

    /**
     * Restituisce il nodo JavaFX della barra di navigazione.
     *
     * @return il nodo {@link javafx.scene.Node} della barra
     */
    public javafx.scene.Node getNode() {
        return bar;
    }

    /**
     * Registra il callback di navigazione invocato quando l'utente clicca un pulsante.
     *
     * @param navigateHandler l'azione da eseguire con il nome della vista di destinazione; può essere null
     */
    public void setOnNavigate(Consumer<String> navigateHandler) {
        this.navigateHandler = navigateHandler;
    }

    /**
     * Registra il callback di logout (cancella la sessione e naviga al login).
     *
     * @param callback l'azione da eseguire per il logout; può essere null
     */
    public void setOnLogout(Runnable callback) {
        this.onLogout = callback;
    }

    /**
     * Ricostruisce la barra in base ai ruoli correnti dell'utente.
     * <p>
     * Sicuro da chiamare da qualsiasi thread: le mutazioni del grafo
     * della scena sono avvolte in {@code Platform.runLater}.
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

        boolean isPlayer        = roles.contains(Role.PLAYER.name());
        boolean isLocalAdmin    = roles.contains(Role.LOCAL_ADMIN.name());
        boolean isGameAdmin     = roles.contains(Role.GAME_ADMIN.name());
        boolean isPlatformAdmin = roles.contains(Role.PLATFORM_ADMIN.name());

        if (isPlayer || isLocalAdmin || isPlatformAdmin) {
            addButton("Games", VIEW_GAME_SELECTION);
        }
        if (isPlayer || isPlatformAdmin) {
            addButton("My Stats",     VIEW_MY_STATISTICS);
            addButton("My Matches",    VIEW_MY_MATCHES);
            addButton("Tournaments",  VIEW_TOURNAMENTS);
        }
        if (isLocalAdmin || isPlatformAdmin) {
            addButton("Aggregated Stats", VIEW_STATISTICS);
            addButton("Local Dashboard",  VIEW_ADMIN_LOCAL);
        }
        if (isGameAdmin || isPlatformAdmin) {
            addButton("Game Admin", VIEW_ADMIN_GAME);
        }
        if (isPlatformAdmin) {
            addButton("Platform Admin", VIEW_ADMIN_PLATFORM);
            addButton("Admin Requests",  VIEW_ADMIN_REQUESTS);
        }
        renderBar();
    }

    /**
     * Aggiunge un pulsante alla barra di navigazione, con deduplicazione.
     * <p>
     * Se esiste già un pulsante per la stessa vista di destinazione,
     * la nuova aggiunta viene ignorata (LinkedHashMap mantiene l'ordine
     * di prima inserzione).
     *
     * @param label      il testo del pulsante; non null
     * @param targetView la costante VIEW_* di destinazione; non null
     */
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

    /**
     * Renderizza la barra con i pulsanti correnti e il pulsante Logout.
     * <p>
     * Aggiunge tutti i pulsanti delle voci alla barra, inserisce uno
     * spacer elastico a destra e infine il pulsante Logout.
     */
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

    /**
     * Esegue la navigazione verso la vista specificata.
     *
     * @param viewName il nome della vista di destinazione; non null
     */
    private void navigate(String viewName) {
        if (navigateHandler != null) navigateHandler.accept(viewName);
    }
}