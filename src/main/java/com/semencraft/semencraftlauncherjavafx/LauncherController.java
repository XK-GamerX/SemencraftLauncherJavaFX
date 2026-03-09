package com.semencraft.semencraftlauncherjavafx;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.value.ObservableDoubleValue;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LauncherController {

    @FXML private HBox titleBar;
    @FXML private ImageView imgTitleBarIcon;
    @FXML private Button btnMinimize;
    @FXML private Button btnMaxRestore;
    @FXML private Button btnClose;

    @FXML private StackPane sidebarGlass;
    @FXML private Region sidebarBlurLayer;
    @FXML private ScrollPane sidebarScroll;
    @FXML private VBox sidebar;
    @FXML private ImageView imgLogo;
    @FXML private Button btnNavHome;
    @FXML private Button btnNavSkin;
    @FXML private Button btnNavSettings;
    @FXML private Pane semmiePane;
    @FXML private ImageView imgSemmie;
    @FXML private Label lblUsername;
    @FXML private Button btnDiscord;

    @FXML private StackPane centerLayer;
    @FXML private VBox centerContent;
    @FXML private ImageView imgTitle;
    @FXML private ImageView imgPlayBtn;
    @FXML private Button btnPlay;
    @FXML private Label lblStatus;
    @FXML private Region titleGap;
    @FXML private Region statusGap;
    @FXML private VBox settingsView;
    @FXML private ScrollPane settingsScroll;
    @FXML private VBox settingsRoot;
    @FXML private Button btnSettingsTabGeneral;
    @FXML private Button btnSettingsTabModpacks;
    @FXML private Button btnSettingsTabSystem;
    @FXML private Label lblJavaRuntimeStatus;
    @FXML private VBox paneSettingsGeneral;
    @FXML private VBox paneSettingsModpacks;
    @FXML private VBox paneSettingsSystem;
    @FXML private ComboBox<String> cmbModpack;
    @FXML private TextField txtModSearch;
    @FXML private ComboBox<String> cmbModsTypeFilter;
    @FXML private ComboBox<String> cmbModsSectionFilter;
    @FXML private Label lblModsCount;
    @FXML private Button btnModsPrevPage;
    @FXML private Label lblModsPage;
    @FXML private Button btnModsNextPage;
    @FXML private VBox modsListContainer;
    @FXML private Slider sliderRam;
    @FXML private Label lblRamValue;
    @FXML private ComboBox<String> cmbLaunchMode;
    @FXML private TextField txtResolution;
    @FXML private CheckBox chkBorderlessWindow;
    @FXML private CheckBox chkCloseLauncherOnStart;
    @FXML private CheckBox chkDiscordRpc;
    @FXML private HBox settingsActionBar;
    @FXML private Button btnSaveSettings;
    @FXML private Button btnDiscardSettings;
    @FXML private Label lblSettingsStatus;
    @FXML private Label lblSystemJavaDetails;

    @FXML private StackPane loadingOverlay;
    @FXML private VBox loadingCard;
    @FXML private ImageView imgLoadTitle;
    @FXML private Label lblLoadStatus;
    @FXML private Label lblTimer;
    @FXML private Label lblTotalPercent;
    @FXML private Label lblDownloadPercent;
    @FXML private Region progressFillTotal;
    @FXML private Region progressFillDownload;
    @FXML private Region progressIndTotal;
    @FXML private Region progressIndDownload;
    @FXML private StackPane progressTrackTotal;
    @FXML private StackPane progressTrackDownload;
    @FXML private HBox timerRow;
    @FXML private StackPane startupOverlay;
    @FXML private Label lblStartupStatus;
    @FXML private StackPane startupProgressTrack;
    @FXML private Region startupProgressFill;
    @FXML private Label lblStartupPercent;

    @FXML private StackPane login;
    @FXML private LoginController loginController;

    private Stage stage;
    private Timeline progressTimeline;
    private Timeline totalIndicatorLoop;
    private Timeline downloadIndicatorLoop;
    private Timeline settingsStatusFade;
    private double windowDragOffsetX;
    private double windowDragOffsetY;
    private boolean windowControlsBound;
    private boolean windowSceneFallbackBound;
    private boolean draggingFromTitleArea;
    private boolean loggedIn;
    private boolean settingsListenersBound;
    private boolean settingsWatchEnabled;
    private boolean launchInProgress;
    private boolean minecraftRunning;
    private boolean servicesShutdown;
    private boolean rpcEnabledByUser = true;
    private ViewMode viewMode = ViewMode.HOME;
    private final GaussianBlur loginBlurLight = new GaussianBlur(11);
    private final GaussianBlur loginBlurHeavy = new GaussianBlur(18);
    private final ColorAdjust playDisabledEffect = new ColorAdjust(0.0, -0.12, -0.68, 0.0);
    private final DiscordRpcService discordRpc = new DiscordRpcService();
    private final ModpackManager modpackManager = new ModpackManager();
    private LauncherStorage.LauncherConfig persistedConfig = LauncherStorage.LauncherConfig.defaults();
    private ModpackManager.Catalog modpackCatalog;
    private ModpackManager.Selection persistedModSelection;
    private ModpackManager.Selection workingModSelection;
    private final List<ModpackManager.OptionalEntry> filteredModEntries = new ArrayList<>();
    private final Map<String, Image> entryIconCache = new HashMap<>();
    private Image playImageDefault;
    private Image playImageHover;
    private Image playImagePressed;
    private Image modEntryIcon;
    private Image resourcepackEntryIcon;
    private Timeline minecraftStatePoller;
    private Process minecraftProcess;
    private String activeLoaderVersion = "";
    private String cachedMinecraftDirLower;
    private int modsPageIndex;
    private boolean suppressModpackTemplateEvents;
    private boolean settingsActionBarVisible;
    private Timeline actionButtonsFloatTimeline;
    private boolean startupCatalogReady;
    private boolean startupJavaReady;
    private boolean startupDismissed;
    private long startupOverlayShownAtMs;
    private SettingsSection currentSettingsSection = SettingsSection.GENERAL;

    private double totalProgress;
    private double downloadProgress;
    private double startupProgressValue;

    private static final int MODS_PAGE_SIZE = 7;
    private static final String FILTER_TYPE_ALL_LABEL = "Todos los mods";
    private static final String FILTER_TYPE_MODS_LABEL = "Solo mods";
    private static final String FILTER_TYPE_RESOURCEPACKS_LABEL = "Solo resourcepacks";
    private static final String FILTER_SECTION_ALL_LABEL = "Todas las secciones";
    private static final double PROGRESS_FALLBACK_W = 390.0;
    private static final String[] LOAD_STEPS = {
            "Verificando cliente...",
            "Descargando assets...",
            "Descargando librerias...",
            "Preparando runtime...",
            "Aplicando configuracion...",
            "Finalizando inicio...",
            "Listo. Iniciando SemenCraft..."
    };

    private enum ViewMode {
        HOME,
        SETTINGS
    }

    private enum SettingsSection {
        GENERAL,
        MODPACKS,
        SYSTEM
    }

    @FXML
    public void initialize() {
        beginStartupOverlay();
        loadImages();
        updateStartupProgress(0.18, "Cargando interfaz...");
        setupSidebarGlass();
        setupStageResolver();
        setupResponsiveBindings();
        setupNavButtons();
        setupDiscordButton();
        setupLoadingVisuals();
        setupSettingsView();
        updateStartupProgress(0.34, "Aplicando configuracion...");
        bootstrapSavedSession();

        if (loginController != null) {
            loginController.setOnSuccess(this::onLoginSuccess);
        }

        showView(ViewMode.HOME, false);
        startDiscordRpc();
        startMinecraftStatePolling();
        animateEntrance();
        scheduleSemmie();
        updateStartupProgress(0.48, "Preparando recursos...");
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        if (stage != null) {
            stage.setOnHidden(event -> shutdownBackgroundServices());
        }
        setupWindowControls();
    }

    private void setupStageResolver() {
        titleBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            if (newScene.getWindow() instanceof Stage stageWindow) {
                this.stage = stageWindow;
                setupWindowControls();
            }
            newScene.windowProperty().addListener((wObs, oldWindow, newWindow) -> {
                if (newWindow instanceof Stage stageWindow) {
                    this.stage = stageWindow;
                    setupWindowControls();
                }
            });
        });
    }

    private void setupSidebarGlass() {
        sidebarBlurLayer.setEffect(new GaussianBlur(56));
        sidebarBlurLayer.setCache(true);
        sidebarScroll.setPannable(true);
        sidebarScroll.setFitToHeight(true);
        sidebarScroll.viewportBoundsProperty().addListener((obs, oldV, newV) -> sidebar.setMinHeight(newV.getHeight()));
        Platform.runLater(() -> sidebar.setMinHeight(sidebarScroll.getViewportBounds().getHeight()));
    }

    private void setupWindowControls() {
        Stage resolvedStage = resolveStage();
        if (resolvedStage == null || titleBar == null || windowControlsBound) {
            return;
        }
        windowControlsBound = true;

        resolvedStage.maximizedProperty().addListener((obs, oldV, isMax) -> updateMaxRestoreButton(isMax));
        updateMaxRestoreButton(resolvedStage.isMaximized());

        configureWindowButtonUi(btnMinimize);
        configureWindowButtonUi(btnMaxRestore);
        configureWindowButtonUi(btnClose);
        titleBar.setPickOnBounds(true);
        titleBar.setOnMousePressed(this::onTitleBarPressed);
        titleBar.setOnMouseDragged(this::onTitleBarDragged);
        titleBar.setOnMouseClicked(this::onTitleBarClicked);
        bindWindowFallbackSceneHandlers();
    }

    private void configureWindowButtonUi(Button button) {
        if (button == null) {
            return;
        }
        button.setMouseTransparent(false);
        button.setPickOnBounds(true);
        button.setFocusTraversable(false);
    }

    private void bindWindowFallbackSceneHandlers() {
        if (windowSceneFallbackBound || titleBar == null) {
            return;
        }
        Scene scene = titleBar.getScene();
        if (scene == null) {
            titleBar.sceneProperty().addListener((obs, oldScene, newScene) -> bindWindowFallbackSceneHandlers());
            return;
        }
        windowSceneFallbackBound = true;

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onSceneWindowPressed);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onSceneWindowDragged);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> draggingFromTitleArea = false);
        scene.addEventFilter(MouseEvent.MOUSE_CLICKED, this::onSceneWindowClicked);
    }

    private void onSceneWindowPressed(MouseEvent event) {
        if (!isPrimaryMouse(event) || isWindowControlTarget(event.getTarget())) {
            return;
        }
        double sx = event.getSceneX();
        double sy = event.getSceneY();
        if (!isInTitleBar(sx, sy) || isInWindowButtons(sx, sy)) {
            return;
        }
        Stage targetStage = resolveStage();
        if (targetStage == null) {
            return;
        }
        draggingFromTitleArea = true;
        windowDragOffsetX = event.getScreenX() - targetStage.getX();
        windowDragOffsetY = event.getScreenY() - targetStage.getY();
    }

    private void onSceneWindowDragged(MouseEvent event) {
        if (!draggingFromTitleArea) {
            return;
        }
        Stage targetStage = resolveStage();
        if (targetStage == null || targetStage.isMaximized()) {
            return;
        }
        targetStage.setX(event.getScreenX() - windowDragOffsetX);
        targetStage.setY(event.getScreenY() - windowDragOffsetY);
        event.consume();
    }

    private void onSceneWindowClicked(MouseEvent event) {
        if (!isPrimaryMouse(event) || isWindowControlTarget(event.getTarget())) {
            return;
        }
        double sx = event.getSceneX();
        double sy = event.getSceneY();
        if (!isInTitleBar(sx, sy)) {
            return;
        }

        if (isInside(btnClose, sx, sy)) {
            onClose();
            event.consume();
            return;
        }
        if (isInside(btnMinimize, sx, sy)) {
            onMinimize();
            event.consume();
            return;
        }
        if (isInside(btnMaxRestore, sx, sy)) {
            onMaxRestore();
            event.consume();
            return;
        }
        if (event.getClickCount() == 2) {
            toggleMaxRestore();
            event.consume();
        }
    }

    private static boolean isPrimaryMouse(MouseEvent event) {
        return event != null && (event.isPrimaryButtonDown() || event.getButton() == MouseButton.PRIMARY);
    }

    private boolean isInTitleBar(double sceneX, double sceneY) {
        return isInside(titleBar, sceneX, sceneY);
    }

    private boolean isInWindowButtons(double sceneX, double sceneY) {
        return isInside(btnMinimize, sceneX, sceneY)
                || isInside(btnMaxRestore, sceneX, sceneY)
                || isInside(btnClose, sceneX, sceneY);
    }

    private static boolean isInside(Node node, double sceneX, double sceneY) {
        if (node == null || !node.isVisible()) {
            return false;
        }
        Bounds b = node.localToScene(node.getBoundsInLocal());
        return b != null && b.contains(sceneX, sceneY);
    }

    private void onTitleBarPressed(MouseEvent event) {
        if (isWindowControlTarget(event.getTarget())) {
            return;
        }
        Stage targetStage = resolveStage();
        if (targetStage == null) {
            return;
        }
        windowDragOffsetX = event.getScreenX() - targetStage.getX();
        windowDragOffsetY = event.getScreenY() - targetStage.getY();
    }

    private void onTitleBarDragged(MouseEvent event) {
        if (isWindowControlTarget(event.getTarget())) {
            return;
        }
        Stage targetStage = resolveStage();
        if (targetStage == null || targetStage.isMaximized()) {
            return;
        }
        targetStage.setX(event.getScreenX() - windowDragOffsetX);
        targetStage.setY(event.getScreenY() - windowDragOffsetY);
    }

    private void onTitleBarClicked(MouseEvent event) {
        if (isWindowControlTarget(event.getTarget()) || event.getClickCount() != 2) {
            return;
        }
        toggleMaxRestore();
    }

    private Stage resolveStage() {
        if (stage != null) {
            return stage;
        }
        if (titleBar != null && titleBar.getScene() != null && titleBar.getScene().getWindow() instanceof Stage stageWindow) {
            stage = stageWindow;
            return stageWindow;
        }
        return null;
    }

    private void updateMaxRestoreButton(boolean maximized) {
        if (btnMaxRestore != null) {
            btnMaxRestore.setText(maximized ? "[R]" : "[ ]");
        }
    }

    private boolean isWindowControlTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        return isDescendant(node, btnMinimize)
                || isDescendant(node, btnMaxRestore)
                || isDescendant(node, btnClose);
    }

    private static boolean isDescendant(Node node, Node ancestor) {
        Node current = node;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void toggleMaxRestore() {
        Stage targetStage = resolveStage();
        if (targetStage == null) {
            return;
        }
        targetStage.setMaximized(!targetStage.isMaximized());
    }

    private void setupResponsiveBindings() {
        Platform.runLater(() -> {
            if (sidebar.getScene() == null) {
                return;
            }

            imgLogo.fitHeightProperty().bind(clamp(sidebar.heightProperty().multiply(0.16), 74, 142));

            DoubleBinding semmieSize = clamp(sidebar.heightProperty().multiply(0.162), 84, 138);
            imgSemmie.fitWidthProperty().bind(semmieSize);
            imgSemmie.fitHeightProperty().bind(semmieSize);
            semmiePane.prefHeightProperty().bind(clamp(sidebar.heightProperty().multiply(0.20), 104, 170));

            imgTitle.fitHeightProperty().bind(clamp(centerLayer.heightProperty().multiply(0.50), 250, 600));
            titleGap.prefHeightProperty().bind(clamp(centerLayer.heightProperty().multiply(0.028), 10, 24));
            statusGap.prefHeightProperty().bind(clamp(centerLayer.heightProperty().multiply(0.032), 8, 24));

            DoubleBinding playSize = Bindings.createDoubleBinding(() -> {
                double raw = centerLayer.getHeight() * 0.30;
                double clamped = Math.max(196.0, Math.min(332.0, raw));
                return Math.rint(clamped);
            }, centerLayer.heightProperty());
            imgPlayBtn.fitWidthProperty().bind(playSize);
            imgPlayBtn.fitHeightProperty().bind(playSize);

            btnPlay.prefWidthProperty().bind(clamp(centerLayer.widthProperty().multiply(0.34), 230, 390));
            btnPlay.prefHeightProperty().bind(clamp(centerLayer.heightProperty().multiply(0.11), 58, 84));

            DoubleBinding loadTrackWidth = clamp(loadingOverlay.widthProperty().multiply(0.37), 240, 420);
            progressTrackTotal.prefWidthProperty().bind(loadTrackWidth);
            progressTrackDownload.prefWidthProperty().bind(loadTrackWidth);
            timerRow.prefWidthProperty().bind(loadTrackWidth);

            loadingCard.maxWidthProperty().bind(clamp(loadingOverlay.widthProperty().multiply(0.58), 340, 580));
            imgLoadTitle.fitHeightProperty().bind(clamp(loadingOverlay.heightProperty().multiply(0.17), 90, 164));
            if (startupProgressTrack != null && startupOverlay != null) {
                startupProgressTrack.prefWidthProperty().bind(clamp(startupOverlay.widthProperty().multiply(0.26), 190, 310));
                startupProgressTrack.widthProperty().addListener((obs, oldV, newV) -> updateStartupProgress(startupProgressValue, null));
            }

            if (settingsRoot != null) {
                settingsRoot.maxWidthProperty().bind(clamp(centerLayer.widthProperty().multiply(0.92), 680, 1040));
            }
        });
    }

    private void setupLoadingVisuals() {
        clipToBounds(progressTrackTotal);
        clipToBounds(progressTrackDownload);

        applyProgressFallbackStyle(progressFillTotal,
                "-fx-background-color: linear-gradient(to right, #6ea8ff 0%, #4d78d5 100%); -fx-background-radius: 999;");
        applyProgressFallbackStyle(progressFillDownload,
                "-fx-background-color: linear-gradient(to right, #f3b56b 0%, #d68636 100%); -fx-background-radius: 999;");
        applyProgressFallbackStyle(progressIndTotal,
                "-fx-background-color: rgba(236, 244, 255, 0.95); -fx-background-radius: 999;");
        applyProgressFallbackStyle(progressIndDownload,
                "-fx-background-color: rgba(255, 240, 220, 0.95); -fx-background-radius: 999;");

        progressFillTotal.setManaged(true);
        progressFillDownload.setManaged(true);
        progressFillTotal.setVisible(true);
        progressFillDownload.setVisible(true);
        progressFillTotal.setMinHeight(8);
        progressFillTotal.setPrefHeight(8);
        progressFillTotal.setMaxHeight(8);
        progressFillDownload.setMinHeight(8);
        progressFillDownload.setPrefHeight(8);
        progressFillDownload.setMaxHeight(8);
        setRegionWidth(progressFillTotal, 0.0);
        setRegionWidth(progressFillDownload, 0.0);

        progressIndTotal.setMouseTransparent(true);
        progressIndDownload.setMouseTransparent(true);
        progressIndTotal.setManaged(true);
        progressIndDownload.setManaged(true);
        progressIndTotal.setVisible(true);
        progressIndDownload.setVisible(true);
        progressIndTotal.setMinSize(74, 8);
        progressIndTotal.setPrefSize(74, 8);
        progressIndTotal.setMaxSize(74, 8);
        progressIndDownload.setMinSize(74, 8);
        progressIndDownload.setPrefSize(74, 8);
        progressIndDownload.setMaxSize(74, 8);
        progressIndTotal.setTranslateX(-120);
        progressIndDownload.setTranslateX(-120);

        progressTrackTotal.widthProperty().addListener((obs, oldV, newV) -> updateProgressBars());
        progressTrackDownload.widthProperty().addListener((obs, oldV, newV) -> updateProgressBars());
        progressTrackTotal.insetsProperty().addListener((obs, oldV, newV) -> updateProgressBars());
        progressTrackDownload.insetsProperty().addListener((obs, oldV, newV) -> updateProgressBars());

        lblTimer.setText("00:00");
        lblTotalPercent.setText("0%");
        lblDownloadPercent.setText("0%");
        totalProgress = 0.0;
        downloadProgress = 0.0;
        updateProgressBars();
    }

    private static void applyProgressFallbackStyle(Region region, String style) {
        if (region == null || style == null || style.isBlank()) {
            return;
        }
        String existing = region.getStyle();
        if (existing == null || existing.isBlank()) {
            region.setStyle(style);
            return;
        }
        if (!existing.contains("-fx-background-color")) {
            region.setStyle(existing + " " + style);
        }
    }

    private static void clipToBounds(Region region) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        region.setClip(clip);
    }

    private void beginStartupOverlay() {
        startupCatalogReady = false;
        startupJavaReady = false;
        startupDismissed = false;
        startupOverlayShownAtMs = System.currentTimeMillis();
        if (startupOverlay == null) {
            startupDismissed = true;
            return;
        }
        startupOverlay.setVisible(true);
        startupOverlay.setManaged(true);
        startupOverlay.setMouseTransparent(false);
        startupOverlay.setOpacity(1.0);
        startupOverlay.toFront();
        if (startupProgressFill != null) {
            setRegionWidth(startupProgressFill, 0.0);
        }
        startupProgressValue = 0.0;
        if (lblStartupPercent != null) {
            lblStartupPercent.setText("0%");
        }
        if (lblStartupStatus != null) {
            lblStartupStatus.setText("Cargando Launcher");
        }
    }

    private void updateStartupProgress(double progress, String status) {
        if (startupDismissed || startupOverlay == null) {
            return;
        }
        double clamped = clamp(progress, 0.0, 1.0);
        startupProgressValue = clamped;
        if (lblStartupStatus != null && status != null && !status.isBlank()) {
            lblStartupStatus.setText(status);
        }
        if (lblStartupPercent != null) {
            lblStartupPercent.setText(String.format(Locale.US, "%.0f%%", clamped * 100.0));
        }
        if (startupProgressFill != null && startupProgressTrack != null) {
            double trackWidth = resolveTrackContentWidth(startupProgressTrack);
            setRegionWidth(startupProgressFill, Math.rint(trackWidth * clamped));
        }
    }

    private void tryFinishStartupOverlay() {
        if (startupDismissed || startupOverlay == null) {
            return;
        }
        if (!startupCatalogReady || !startupJavaReady) {
            return;
        }
        startupDismissed = true;
        String user = lblUsername == null ? "" : lblUsername.getText();
        String welcome = (user == null || user.isBlank() || "--".equals(user) || "-".equals(user))
                ? "Bienvenido a SemenCraft"
                : "Bienvenido, " + user;
        updateStartupProgress(1.0, welcome);
        if (lblStartupPercent != null) {
            lblStartupPercent.setText("100%");
        }
        long elapsed = System.currentTimeMillis() - startupOverlayShownAtMs;
        int waitMs = (int) Math.max(0, 900 - elapsed) + 1000;
        delay(waitMs, () -> {
            FadeTransition out = fade(startupOverlay, 0.0, 340);
            out.setOnFinished(event -> {
                startupOverlay.setVisible(false);
                startupOverlay.setManaged(false);
                startupOverlay.setMouseTransparent(true);
            });
            out.play();
        });
    }

    private void onLoginSuccess(String username) {
        loggedIn = true;
        lblUsername.setText(username);
        LauncherStorage.saveCredentials(username);
        refreshLauncherRuntimeStateUi();
        FadeTransition transition = fade(login, 0.0, 360);
        transition.setOnFinished(event -> {
            setLoginOverlayVisible(false);
        });
        transition.play();
    }

    private void bootstrapSavedSession() {
        String storedUser = LauncherStorage.loadSavedUsername();
        if (storedUser == null || storedUser.isBlank()) {
            return;
        }
        loggedIn = true;
        lblUsername.setText(storedUser);
        if (login != null) {
            login.setVisible(false);
            login.setManaged(false);
            login.setMouseTransparent(true);
            login.setOpacity(0.0);
        }
        applyLoginBackdropFx(false);
        refreshLauncherRuntimeStateUi();
    }

    private void startDiscordRpc() {
        applyDiscordRpcPreference(rpcEnabledByUser);
    }

    private void applyDiscordRpcPreference(boolean enabled) {
        rpcEnabledByUser = enabled;
        if (!rpcEnabledByUser) {
            discordRpc.shutdown();
            return;
        }
        if (!discordRpc.hasConfiguredAppId()) {
            if (viewMode == ViewMode.SETTINGS && lblSettingsStatus != null) {
                showSettingsStatus("RPC requiere " + DiscordRpcService.appIdEnvironmentVariable());
            }
            return;
        }
        discordRpc.start();
        refreshLauncherRuntimeStateUi();
    }

    private void startMinecraftStatePolling() {
        if (minecraftStatePoller != null) {
            return;
        }
        minecraftStatePoller = new Timeline(new KeyFrame(Duration.seconds(2.2), event -> refreshMinecraftRunningState()));
        minecraftStatePoller.setCycleCount(Animation.INDEFINITE);
        minecraftStatePoller.play();
        refreshMinecraftRunningState();
    }

    private void stopMinecraftStatePolling() {
        if (minecraftStatePoller != null) {
            minecraftStatePoller.stop();
            minecraftStatePoller = null;
        }
    }

    private void refreshMinecraftRunningState() {
        boolean detected = detectMinecraftRunning();
        if (minecraftRunning != detected) {
            minecraftRunning = detected;
            if (!minecraftRunning) {
                activeLoaderVersion = "";
            }
        }
        refreshLauncherRuntimeStateUi();
    }

    private boolean detectMinecraftRunning() {
        Process tracked = minecraftProcess;
        if (tracked != null) {
            if (tracked.isAlive()) {
                return true;
            }
            minecraftProcess = null;
        }

        String gameDirLower = normalizedMinecraftDirLower();
        try {
            return ProcessHandle.allProcesses()
                    .filter(ProcessHandle::isAlive)
                    .anyMatch(process -> isLikelyMinecraftProcess(process, gameDirLower));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isLikelyMinecraftProcess(ProcessHandle process, String gameDirLower) {
        ProcessHandle.Info info = process.info();
        String commandLine = info.commandLine().orElse("");
        if (commandLine.isBlank()) {
            return false;
        }

        String lower = commandLine.toLowerCase(Locale.ROOT).replace('\\', '/');
        if (!lower.contains("java")) {
            return false;
        }
        if (!lower.contains(gameDirLower)) {
            return false;
        }
        return lower.contains("net.fabricmc.loader.impl.launch.knot.knotclient")
                || lower.contains("net.minecraft.client.main.main")
                || lower.contains(gameDirLower + "/versions/")
                || lower.contains(gameDirLower + "/libraries/");
    }

    private String normalizedMinecraftDirLower() {
        if (cachedMinecraftDirLower != null && !cachedMinecraftDirLower.isBlank()) {
            return cachedMinecraftDirLower;
        }
        Path gameDir = LauncherStorage.minecraftDirectory().toAbsolutePath().normalize();
        cachedMinecraftDirLower = gameDir.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return cachedMinecraftDirLower;
    }

    private void trackMinecraftProcess(Process process, String loaderVersion) {
        minecraftProcess = process;
        activeLoaderVersion = loaderVersion == null ? "" : loaderVersion.trim();
        if (process != null) {
            process.onExit().thenRun(() -> Platform.runLater(this::refreshMinecraftRunningState));
        }
        refreshMinecraftRunningState();
    }

    private void refreshLauncherRuntimeStateUi() {
        applyPlayAvailability();
        if (launchInProgress) {
            return;
        }
        if (minecraftRunning) {
            String suffix = (activeLoaderVersion == null || activeLoaderVersion.isBlank())
                    ? ""
                    : " (Fabric " + activeLoaderVersion + ")";
            if (lblStatus != null) {
                lblStatus.setText("En ejecucion" + suffix);
            }
            pushDiscordPresence(DiscordRpcService.LauncherRpcState.PLAYING, "Jugando en SemenCraft");
            return;
        }
        if (viewMode == ViewMode.SETTINGS) {
            if (lblStatus != null) {
                lblStatus.setText("Configurando launcher");
            }
            pushDiscordPresence(DiscordRpcService.LauncherRpcState.SETTINGS, "Ajustando configuracion");
            return;
        }
        if (lblStatus != null) {
            lblStatus.setText("Listo para jugar");
        }
        pushDiscordPresence(DiscordRpcService.LauncherRpcState.IDLE, "En el menu principal");
    }

    private void applyPlayAvailability() {
        boolean blocked = launchInProgress || minecraftRunning;
        if (imgPlayBtn != null) {
            imgPlayBtn.setMouseTransparent(blocked);
            imgPlayBtn.setDisable(blocked);
            imgPlayBtn.setOpacity(blocked ? 0.50 : 1.0);
            imgPlayBtn.setEffect(blocked ? playDisabledEffect : null);
            imgPlayBtn.setCursor(blocked ? Cursor.DEFAULT : Cursor.HAND);
        }
        if (btnPlay != null) {
            btnPlay.setDisable(blocked);
            btnPlay.setCursor(blocked ? Cursor.DEFAULT : Cursor.HAND);
        }
    }

    private void pushDiscordPresence(DiscordRpcService.LauncherRpcState state, String details) {
        if (!rpcEnabledByUser) {
            return;
        }
        String username = lblUsername == null ? "" : lblUsername.getText();
        discordRpc.updatePresence(state, details, username);
    }

    private void updateInstallPresence(String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        String lower = status.toLowerCase(Locale.ROOT);
        if (lower.contains("iniciando minecraft") || lower.contains("minecraft iniciado")) {
            pushDiscordPresence(DiscordRpcService.LauncherRpcState.LAUNCHING, status);
            return;
        }
        if (lower.contains("descargando")
                || lower.contains("consultando")
                || lower.contains("preparando")
                || lower.contains("extrayendo")
                || lower.contains("assets")
                || lower.contains("indice")
                || lower.contains("librerias")
                || lower.contains("versiones")) {
            pushDiscordPresence(DiscordRpcService.LauncherRpcState.INSTALLING, status);
        }
    }

    private void shutdownBackgroundServices() {
        if (servicesShutdown) {
            return;
        }
        servicesShutdown = true;
        stopMinecraftStatePolling();
        discordRpc.shutdown();
    }

    private void loadImages() {
        Image logo = loadAsset("title.png");
        if (logo != null) {
            imgLogo.setImage(logo);
            imgTitle.setImage(logo);
            imgLoadTitle.setImage(logo);
            imgTitle.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.70), 34, 0.26, 0, 9);");
        }

        Image launcherIcon = loadAssetAny(
                "circular-black.bmp",
                "circular-black.png",
                "icon.png"
        );
        if (launcherIcon != null && imgTitleBarIcon != null) {
            imgTitleBarIcon.setImage(launcherIcon);
        }

        Image semmie = loadAsset("semmie.gif");
        if (semmie == null) {
            semmie = loadAsset("semmie.png");
        }
        if (semmie == null) {
            semmie = loadAsset("semmie.webp");
        }
        if (semmie != null) {
            imgSemmie.setImage(semmie);
            imgSemmie.setScaleX(-1.0);
        }

        playImageDefault = loadAsset("jugar.png");
        playImageHover = loadAsset("jugar_hover.png");
        playImagePressed = loadAsset("jugar_clicked.png");
        if (playImageHover == null) {
            playImageHover = playImageDefault;
        }
        if (playImagePressed == null) {
            playImagePressed = playImageDefault;
        }

        if (playImageDefault != null) {
            imgPlayBtn.setImage(playImageDefault);
            imgPlayBtn.setSmooth(false);
            imgPlayBtn.setCache(false);
            imgPlayBtn.setTranslateY(6);
            imgPlayBtn.setVisible(true);
            imgPlayBtn.setManaged(true);
            imgPlayBtn.setCursor(Cursor.HAND);
            attachPlayHover(imgPlayBtn);
            btnPlay.setVisible(false);
            btnPlay.setManaged(false);
        }
    }

    static Image loadAsset(String name) {
        try (InputStream is = MainApp.class.getResourceAsStream("assets/" + name)) {
            if (is != null) {
                return new Image(is);
            }
        } catch (Exception ignored) {
        }

        try {
            File file = new File("src/main/resources/com/semencraft/semencraftlauncherjavafx/assets/" + name);
            if (file.exists()) {
                return new Image(file.toURI().toString());
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static Image loadAssetAny(String... names) {
        if (names == null) {
            return null;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Image image = loadAsset(name);
            if (image != null) {
                return image;
            }
        }
        return null;
    }

    private static ImageView makeIcon(Image img, int size) {
        ImageView view = new ImageView(img);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    static void attachSmoothHover(Node node, double scaleTo, double yTo) {
        Timeline enter = new Timeline(new KeyFrame(Duration.millis(180),
                new KeyValue(node.scaleXProperty(), scaleTo, Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0)),
                new KeyValue(node.scaleYProperty(), scaleTo, Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0)),
                new KeyValue(node.translateYProperty(), yTo, Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0))
        ));
        Timeline exit = new Timeline(new KeyFrame(Duration.millis(220),
                new KeyValue(node.scaleXProperty(), 1.0, Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0)),
                new KeyValue(node.scaleYProperty(), 1.0, Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0)),
                new KeyValue(node.translateYProperty(), 0.0, Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0))
        ));
        node.setOnMouseEntered(event -> {
            exit.stop();
            enter.playFromStart();
        });
        node.setOnMouseExited(event -> {
            enter.stop();
            exit.playFromStart();
        });
    }

    private void attachPlayHover(ImageView playNode) {
        playNode.setOnMouseEntered(event -> {
            playNode.setImage(playImageHover != null ? playImageHover : playImageDefault);
        });
        playNode.setOnMouseExited(event -> {
            playNode.setImage(playImageDefault);
        });
        playNode.setOnMousePressed(event -> {
            playNode.setImage(playImagePressed != null ? playImagePressed : playImageDefault);
        });
        playNode.setOnMouseReleased(event -> {
            playNode.setImage(playNode.isHover() ? playImageHover : playImageDefault);
        });
        playNode.setOnMouseClicked(event -> onPlay());
    }

    private static void attachSlideHover(Node node) {
        Timeline enter = new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(node.translateXProperty(), 5.0, Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0))));
        Timeline exit = new Timeline(new KeyFrame(Duration.millis(200),
                new KeyValue(node.translateXProperty(), 0.0, Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0))));
        node.setOnMouseEntered(event -> {
            exit.stop();
            enter.playFromStart();
        });
        node.setOnMouseExited(event -> {
            enter.stop();
            exit.playFromStart();
        });
    }

    private void setupNavButtons() {
        applyNavIcon(btnNavHome, "icon.png", "  Inicio");
        applyNavIcon(btnNavSkin, "steve.jpg", "  Skin Manager");
        applyNavIcon(btnNavSettings, "configurations.png", "  Configuracion");

        for (Button button : new Button[]{btnNavHome, btnNavSkin, btnNavSettings}) {
            attachSlideHover(button);
        }
    }

    private void applyNavIcon(Button btn, String asset, String label) {
        Image img = loadAsset(asset);
        if (img != null) {
            btn.setGraphic(makeIcon(img, 26));
            btn.setGraphicTextGap(12);
        }
        btn.setText(label);
    }

    private void setupDiscordButton() {
        Image img = loadAsset("discord.png");
        if (img != null) {
            btnDiscord.setGraphic(makeIcon(img, 22));
            btnDiscord.setGraphicTextGap(12);
        }
        btnDiscord.setText("  Discord Oficial");
        attachSmoothHover(btnDiscord, 1.02, -2);
    }

    private void setupSettingsView() {
        LauncherStorage.ensureEnvironment();
        modEntryIcon = loadAsset("icon.png");
        resourcepackEntryIcon = loadAsset("circular-blue.png");

        if (settingsScroll != null) {
            settingsScroll.setPannable(true);
            settingsScroll.setFitToWidth(true);
        }
        if (settingsView != null) {
            settingsView.setVisible(false);
            settingsView.setManaged(false);
        }
        if (settingsActionBar != null) {
            settingsActionBar.setMaxHeight(Region.USE_PREF_SIZE);
            settingsActionBar.setVisible(false);
            settingsActionBar.setManaged(false);
            settingsActionBar.setMouseTransparent(true);
            settingsActionBarVisible = false;
            stopActionButtonsFloatAnimation();
        }

        if (cmbModpack != null) {
            cmbModpack.getItems().setAll(
                    ModpackManager.TEMPLATE_NORMAL,
                    ModpackManager.TEMPLATE_OPTIMIZED,
                    ModpackManager.TEMPLATE_CUSTOM
            );
        }
        if (cmbLaunchMode != null) {
            cmbLaunchMode.getItems().setAll(
                    "Ventana",
                    "Pantalla completa",
                    "Ventana maximizada"
            );
        }
        if (cmbModsTypeFilter != null) {
            cmbModsTypeFilter.getItems().setAll(
                    FILTER_TYPE_ALL_LABEL,
                    FILTER_TYPE_MODS_LABEL,
                    FILTER_TYPE_RESOURCEPACKS_LABEL
            );
            cmbModsTypeFilter.getSelectionModel().select(FILTER_TYPE_ALL_LABEL);
            cmbModsTypeFilter.setPromptText("Tipo de filtro");
        }
        if (cmbModsSectionFilter != null) {
            cmbModsSectionFilter.getItems().setAll(FILTER_SECTION_ALL_LABEL);
            cmbModsSectionFilter.getSelectionModel().select(FILTER_SECTION_ALL_LABEL);
            cmbModsSectionFilter.setPromptText("Seccion de mods");
        }
        installComboPopupAnimation(cmbModpack);
        installComboPopupAnimation(cmbLaunchMode);
        installComboPopupAnimation(cmbModsTypeFilter);
        installComboPopupAnimation(cmbModsSectionFilter);

        persistedConfig = LauncherStorage.loadConfig();
        applyConfigToControls(persistedConfig);
        registerSettingsDirtyListeners();
        installSettingsTooltips();
        loadModpackCatalogAsync();
        refreshJavaRuntimeStatusAsync();
        applySettingsSection(SettingsSection.GENERAL, false);
        refreshSaveState();
        if (lblSettingsStatus != null) {
            lblSettingsStatus.setOpacity(0.0);
            lblSettingsStatus.setText("");
        }
    }

    private void installSettingsTooltips() {
        installSideTooltip(chkBorderlessWindow, "Quita bordes de la ventana para un look mas limpio.");
        installSideTooltip(chkCloseLauncherOnStart, "Si se activa, el launcher se cierra al abrir Minecraft.");
        installSideTooltip(chkDiscordRpc, "Activa/desactiva Discord Rich Presence para el launcher.");
        installSideTooltip(cmbModpack, "Normal: todo activado. Optimizada: rendimiento recomendado. Custom: cambios manuales.");
        installSideTooltip(cmbLaunchMode, "Selecciona como se abrira el juego al iniciar.");
        installSideTooltip(txtResolution, "Formato recomendado: ancho x alto, por ejemplo 1280x720.");
        installSideTooltip(sliderRam, "Asigna memoria al juego. No uses mas de lo que tu PC soporta.");
        installSideTooltip(txtModSearch, "Busca por nombre interno del mod leyendo metadata del .jar.");
        installSideTooltip(cmbModsSectionFilter, "Filtra por secciones de mods.");
        installSideTooltip(cmbModsTypeFilter, "Filtra por tipo: todos, mods o resourcepacks.");
    }

    private static void installSideTooltip(Control control, String text) {
        if (control == null || text == null || text.isBlank()) {
            return;
        }
        Tooltip tip = new Tooltip(text);
        tip.getStyleClass().add("hover-tip");
        tip.setShowDelay(Duration.millis(110));
        tip.setHideDelay(Duration.millis(80));

        control.hoverProperty().addListener((obs, oldV, hovering) -> {
            if (!hovering || control.getScene() == null || control.getScene().getWindow() == null) {
                tip.hide();
                return;
            }
            Point2D side = control.localToScreen(control.getWidth() + 12, control.getHeight() * 0.45);
            if (side != null) {
                tip.show(control, side.getX(), side.getY());
            }
        });

        control.sceneProperty().addListener((obs, oldScene, newScene) -> tip.hide());
    }

    private static void installComboPopupAnimation(ComboBox<?> combo) {
        if (combo == null) {
            return;
        }
        if (Boolean.TRUE.equals(combo.getProperties().get("popup-anim-installed"))) {
            return;
        }
        combo.getProperties().put("popup-anim-installed", Boolean.TRUE);
        combo.getProperties().put("popup-close-animating", Boolean.FALSE);
        combo.getProperties().put("popup-close-allow-hide", Boolean.FALSE);

        combo.addEventFilter(ComboBoxBase.ON_HIDING, event -> {
            if (Boolean.TRUE.equals(combo.getProperties().get("popup-close-allow-hide"))) {
                combo.getProperties().put("popup-close-allow-hide", Boolean.FALSE);
                return;
            }
            if (Boolean.TRUE.equals(combo.getProperties().get("popup-close-animating"))) {
                event.consume();
                return;
            }
            Node popupContent = resolveComboPopupContent(combo);
            if (popupContent == null || !popupContent.isVisible()) {
                return;
            }
            event.consume();
            combo.getProperties().put("popup-close-animating", Boolean.TRUE);
            Timeline close = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(popupContent.opacityProperty(), 1.0),
                            new KeyValue(popupContent.translateYProperty(), 0.0),
                            new KeyValue(popupContent.scaleYProperty(), 1.0)),
                    new KeyFrame(Duration.millis(120),
                            new KeyValue(popupContent.opacityProperty(), 0.0),
                            new KeyValue(popupContent.translateYProperty(), -8.0),
                            new KeyValue(popupContent.scaleYProperty(), 0.93))
            );
            close.setOnFinished(done -> {
                combo.getProperties().put("popup-close-animating", Boolean.FALSE);
                combo.getProperties().put("popup-close-allow-hide", Boolean.TRUE);
                combo.hide();
            });
            close.play();
        });

        combo.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                Timeline pulse = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(combo.scaleXProperty(), 1.0), new KeyValue(combo.scaleYProperty(), 1.0)),
                        new KeyFrame(Duration.millis(120), new KeyValue(combo.scaleXProperty(), 1.03), new KeyValue(combo.scaleYProperty(), 1.03)),
                        new KeyFrame(Duration.millis(220), new KeyValue(combo.scaleXProperty(), 1.0), new KeyValue(combo.scaleYProperty(), 1.0))
                );
                pulse.play();
                Platform.runLater(() -> {
                    Node popupContent = resolveComboPopupContent(combo);
                    if (popupContent == null) {
                        return;
                    }
                    popupContent.setOpacity(0.0);
                    popupContent.setTranslateY(-9.0);
                    popupContent.setScaleY(0.94);
                    Timeline reveal = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                    new KeyValue(popupContent.opacityProperty(), 0.0),
                                    new KeyValue(popupContent.translateYProperty(), -9.0),
                                    new KeyValue(popupContent.scaleYProperty(), 0.94)),
                            new KeyFrame(Duration.millis(180),
                                    new KeyValue(popupContent.opacityProperty(), 1.0),
                                    new KeyValue(popupContent.translateYProperty(), 0.0),
                                    new KeyValue(popupContent.scaleYProperty(), 1.0))
                    );
                    reveal.play();
                });
                return;
            }
            Platform.runLater(() -> {
                Node popupContent = resolveComboPopupContent(combo);
                if (popupContent != null) {
                    popupContent.setTranslateY(0.0);
                    popupContent.setScaleY(1.0);
                }
            });
            Timeline settle = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(combo.translateYProperty(), 0.0),
                            new KeyValue(combo.opacityProperty(), 1.0)),
                    new KeyFrame(Duration.millis(90),
                            new KeyValue(combo.translateYProperty(), 2.0),
                            new KeyValue(combo.opacityProperty(), 0.94)),
                    new KeyFrame(Duration.millis(190),
                            new KeyValue(combo.translateYProperty(), 0.0),
                            new KeyValue(combo.opacityProperty(), 1.0))
            );
            settle.play();
        });
    }

    private static Node resolveComboPopupContent(ComboBox<?> combo) {
        if (combo == null) {
            return null;
        }
        Skin<?> skin = combo.getSkin();
        if (skin instanceof ComboBoxListViewSkin<?> listSkin) {
            return listSkin.getPopupContent();
        }
        return null;
    }

    private void registerSettingsDirtyListeners() {
        if (settingsListenersBound) {
            return;
        }
        settingsListenersBound = true;

        if (sliderRam != null) {
            sliderRam.valueProperty().addListener((obs, oldV, newV) -> {
                updateRamLabel();
                onSettingsEdited();
            });
        }
        if (cmbModpack != null) {
            cmbModpack.valueProperty().addListener((obs, oldV, newV) -> onTemplateSelectionChanged());
        }
        if (cmbLaunchMode != null) {
            cmbLaunchMode.valueProperty().addListener((obs, oldV, newV) -> onSettingsEdited());
        }
        if (txtResolution != null) {
            txtResolution.textProperty().addListener((obs, oldV, newV) -> onSettingsEdited());
        }
        if (chkBorderlessWindow != null) {
            chkBorderlessWindow.selectedProperty().addListener((obs, oldV, newV) -> onSettingsEdited());
        }
        if (chkCloseLauncherOnStart != null) {
            chkCloseLauncherOnStart.selectedProperty().addListener((obs, oldV, newV) -> onSettingsEdited());
        }
        if (chkDiscordRpc != null) {
            chkDiscordRpc.selectedProperty().addListener((obs, oldV, newV) -> {
                onSettingsEdited();
                applyDiscordRpcPreference(Boolean.TRUE.equals(newV));
            });
        }
        if (txtModSearch != null) {
            txtModSearch.textProperty().addListener((obs, oldV, newV) -> {
                modsPageIndex = 0;
                refreshModEntriesView();
            });
        }
        if (cmbModsTypeFilter != null) {
            cmbModsTypeFilter.valueProperty().addListener((obs, oldV, newV) -> {
                if (FILTER_TYPE_RESOURCEPACKS_LABEL.equals(newV)
                        && cmbModsSectionFilter != null
                        && !FILTER_SECTION_ALL_LABEL.equals(cmbModsSectionFilter.getValue())) {
                    cmbModsSectionFilter.getSelectionModel().select(FILTER_SECTION_ALL_LABEL);
                }
                modsPageIndex = 0;
                refreshModEntriesView();
            });
        }
        if (cmbModsSectionFilter != null) {
            cmbModsSectionFilter.valueProperty().addListener((obs, oldV, newV) -> {
                modsPageIndex = 0;
                refreshModEntriesView();
            });
        }
    }

    private void onSettingsEdited() {
        if (!settingsWatchEnabled) {
            return;
        }
        refreshSaveState();
    }

    private void applyConfigToControls(LauncherStorage.LauncherConfig config) {
        settingsWatchEnabled = false;
        if (sliderRam != null) {
            sliderRam.setValue(config.ramGb());
        }
        if (cmbModpack != null) {
            suppressModpackTemplateEvents = true;
            selectComboValue(cmbModpack, ModpackManager.normalizeTemplate(config.modpack()), ModpackManager.TEMPLATE_NORMAL);
            suppressModpackTemplateEvents = false;
        }
        if (cmbLaunchMode != null) {
            selectComboValue(cmbLaunchMode, config.launchMode(), "Ventana");
        }
        if (txtResolution != null) {
            txtResolution.setText(config.windowResolution());
        }
        if (chkBorderlessWindow != null) {
            chkBorderlessWindow.setSelected(config.borderlessWindow());
        }
        if (chkCloseLauncherOnStart != null) {
            chkCloseLauncherOnStart.setSelected(config.closeLauncherOnStart());
        }
        if (chkDiscordRpc != null) {
            chkDiscordRpc.setSelected(config.discordRpcEnabled());
        }
        rpcEnabledByUser = config.discordRpcEnabled();
        applyDiscordRpcPreference(rpcEnabledByUser);
        settingsWatchEnabled = true;
        updateRamLabel();
    }

    private static void selectComboValue(ComboBox<String> comboBox, String value, String fallback) {
        if (comboBox == null) {
            return;
        }
        String safeValue = (value == null || value.isBlank()) ? fallback : value;
        if (!comboBox.getItems().contains(safeValue)) {
            comboBox.getItems().add(safeValue);
        }
        comboBox.getSelectionModel().select(safeValue);
    }

    private LauncherStorage.LauncherConfig captureCurrentConfig() {
        int ram = sliderRam != null ? (int) Math.round(sliderRam.getValue()) : 6;
        String modpack = cmbModpack != null && cmbModpack.getValue() != null
                ? ModpackManager.normalizeTemplate(cmbModpack.getValue())
                : ModpackManager.TEMPLATE_NORMAL;
        String mode = cmbLaunchMode != null && cmbLaunchMode.getValue() != null ? cmbLaunchMode.getValue() : "Ventana";
        String resolution = txtResolution != null && !txtResolution.getText().trim().isEmpty()
                ? txtResolution.getText().trim()
                : "1280x720";
        boolean borderless = chkBorderlessWindow != null && chkBorderlessWindow.isSelected();
        boolean closeLauncher = chkCloseLauncherOnStart != null && chkCloseLauncherOnStart.isSelected();
        boolean discordRpc = chkDiscordRpc == null || chkDiscordRpc.isSelected();
        return new LauncherStorage.LauncherConfig(modpack, ram, mode, resolution, borderless, closeLauncher, discordRpc);
    }

    private void refreshSaveState() {
        if (btnSaveSettings == null) {
            return;
        }
        boolean dirty = hasPendingSettingsChanges();
        btnSaveSettings.setDisable(!dirty);
        if (btnDiscardSettings != null) {
            btnDiscardSettings.setDisable(!dirty);
        }
        if (settingsActionBar != null) {
            updateSettingsActionBarVisibility(dirty);
        }
    }

    private void updateSettingsActionBarVisibility(boolean visible) {
        if (settingsActionBar == null) {
            return;
        }
        if (visible == settingsActionBarVisible && settingsActionBar.isManaged() == visible) {
            return;
        }
        settingsActionBarVisible = visible;
        if (visible) {
            settingsActionBar.setManaged(true);
            settingsActionBar.setVisible(true);
            settingsActionBar.setMouseTransparent(false);
            settingsActionBar.setOpacity(0.0);
            settingsActionBar.setTranslateY(20);
            ParallelTransition in = parallel(
                    fade(settingsActionBar, 1.0, 220),
                    slideY(settingsActionBar, 0.0, 220)
            );
            in.play();
            startActionButtonsFloatAnimation();
            return;
        }
        stopActionButtonsFloatAnimation();
        ParallelTransition out = parallel(
                fade(settingsActionBar, 0.0, 180),
                slideY(settingsActionBar, 18.0, 180)
        );
        out.setOnFinished(event -> {
            settingsActionBar.setVisible(false);
            settingsActionBar.setManaged(false);
            settingsActionBar.setMouseTransparent(true);
            settingsActionBar.setTranslateY(0.0);
        });
        out.play();
    }

    private void startActionButtonsFloatAnimation() {
        if (btnSaveSettings == null || btnDiscardSettings == null) {
            return;
        }
        stopActionButtonsFloatAnimation();
        actionButtonsFloatTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(btnSaveSettings.translateYProperty(), 0.0),
                        new KeyValue(btnDiscardSettings.translateYProperty(), 0.0)),
                new KeyFrame(Duration.millis(900),
                        new KeyValue(btnSaveSettings.translateYProperty(), -2.5),
                        new KeyValue(btnDiscardSettings.translateYProperty(), -2.5)),
                new KeyFrame(Duration.millis(1800),
                        new KeyValue(btnSaveSettings.translateYProperty(), 0.0),
                        new KeyValue(btnDiscardSettings.translateYProperty(), 0.0))
        );
        actionButtonsFloatTimeline.setCycleCount(Animation.INDEFINITE);
        actionButtonsFloatTimeline.play();
    }

    private void stopActionButtonsFloatAnimation() {
        if (actionButtonsFloatTimeline != null) {
            actionButtonsFloatTimeline.stop();
            actionButtonsFloatTimeline = null;
        }
        if (btnSaveSettings != null) {
            btnSaveSettings.setTranslateY(0.0);
        }
        if (btnDiscardSettings != null) {
            btnDiscardSettings.setTranslateY(0.0);
        }
    }

    private void updateRamLabel() {
        if (sliderRam == null || lblRamValue == null) {
            return;
        }
        int ramGb = (int) Math.round(sliderRam.getValue());
        lblRamValue.setText(ramGb + " GB");
    }

    private boolean hasPendingSettingsChanges() {
        if (!captureCurrentConfig().equals(persistedConfig)) {
            return true;
        }
        ModpackManager.Selection currentSelection = captureCurrentModSelection();
        ModpackManager.Selection savedSelection = persistedModSelection;
        if (currentSelection == null && savedSelection == null) {
            return false;
        }
        if (currentSelection == null || savedSelection == null) {
            return true;
        }
        if (!currentSelection.selectedTemplate().equals(savedSelection.selectedTemplate())) {
            return true;
        }
        if (!currentSelection.baseTemplate().equals(savedSelection.baseTemplate())) {
            return true;
        }
        return !currentSelection.enabledOptionalIds().equals(savedSelection.enabledOptionalIds());
    }

    private void loadModpackCatalogAsync() {
        setModListMessage("Cargando modpack...");
        updateStartupProgress(0.56, "Cargando modpacks desde GitHub...");
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return modpackManager.loadCatalog();
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .thenAccept(catalog -> Platform.runLater(() -> {
                    if (catalog == null) {
                        setModListMessage("No se pudo cargar SemencraftModpacks.");
                        startupCatalogReady = true;
                        updateStartupProgress(0.76, "Modpacks no disponibles (modo fallback).");
                        tryFinishStartupOverlay();
                        return;
                    }
                    modpackCatalog = catalog;
                    initializeModpackSelectionState();
                    refreshModEntriesView();
                    refreshSaveState();
                    startupCatalogReady = true;
                    updateStartupProgress(0.82, "Modpacks listos.");
                    tryFinishStartupOverlay();
                }));
    }

    private void initializeModpackSelectionState() {
        if (modpackCatalog == null) {
            return;
        }
        entryIconCache.clear();
        String template = cmbModpack != null && cmbModpack.getValue() != null
                ? ModpackManager.normalizeTemplate(cmbModpack.getValue())
                : ModpackManager.TEMPLATE_NORMAL;
        ModpackManager.Selection initialSelection;
        if (ModpackManager.TEMPLATE_CUSTOM.equals(template)) {
            ModpackManager.Selection savedCustom = modpackManager.loadSavedCustomSelection(modpackCatalog);
            initialSelection = savedCustom != null
                    ? modpackManager.normalizeSelection(modpackCatalog, savedCustom)
                    : modpackManager.defaultSelectionForTemplate(modpackCatalog, ModpackManager.TEMPLATE_CUSTOM);
        } else {
            initialSelection = modpackManager.defaultSelectionForTemplate(modpackCatalog, template);
        }
        persistedModSelection = modpackManager.normalizeSelection(modpackCatalog, initialSelection);
        workingModSelection = modpackManager.normalizeSelection(modpackCatalog, initialSelection);
        rebuildSectionFilterOptions();
    }

    private void rebuildSectionFilterOptions() {
        if (cmbModsSectionFilter == null) {
            return;
        }
        List<String> options = new ArrayList<>();
        options.add(FILTER_SECTION_ALL_LABEL);
        if (modpackCatalog != null) {
            for (String section : modpackCatalog.sections()) {
                if (!ModpackManager.SECTION_RESOURCEPACKS.equalsIgnoreCase(section)) {
                    options.add(section);
                }
            }
        }
        String selected = cmbModsSectionFilter.getValue();
        cmbModsSectionFilter.getItems().setAll(options);
        if (selected != null && options.contains(selected)) {
            cmbModsSectionFilter.getSelectionModel().select(selected);
        } else {
            cmbModsSectionFilter.getSelectionModel().select(FILTER_SECTION_ALL_LABEL);
        }
    }

    private void onTemplateSelectionChanged() {
        if (suppressModpackTemplateEvents || !settingsWatchEnabled) {
            return;
        }
        if (modpackCatalog == null) {
            onSettingsEdited();
            return;
        }
        String template = cmbModpack == null ? ModpackManager.TEMPLATE_NORMAL : ModpackManager.normalizeTemplate(cmbModpack.getValue());
        if (ModpackManager.TEMPLATE_CUSTOM.equals(template)) {
            ModpackManager.Selection savedCustom = modpackManager.loadSavedCustomSelection(modpackCatalog);
            workingModSelection = savedCustom != null
                    ? modpackManager.normalizeSelection(modpackCatalog, savedCustom)
                    : modpackManager.defaultSelectionForTemplate(modpackCatalog, ModpackManager.TEMPLATE_CUSTOM);
        } else {
            workingModSelection = modpackManager.defaultSelectionForTemplate(modpackCatalog, template);
        }
        modsPageIndex = 0;
        refreshModEntriesView();
        onSettingsEdited();
    }

    private void refreshModEntriesView() {
        if (modsListContainer == null) {
            return;
        }
        modsListContainer.getChildren().clear();
        filteredModEntries.clear();

        if (modpackCatalog == null) {
            setModListMessage("Cargando modpack...");
            updateModPaginationState();
            return;
        }

        String query = txtModSearch == null || txtModSearch.getText() == null
                ? ""
                : txtModSearch.getText().trim().toLowerCase(Locale.ROOT);
        String typeFilter = cmbModsTypeFilter == null || cmbModsTypeFilter.getValue() == null
                ? FILTER_TYPE_ALL_LABEL
                : cmbModsTypeFilter.getValue();
        String sectionFilter = cmbModsSectionFilter == null || cmbModsSectionFilter.getValue() == null
                ? FILTER_SECTION_ALL_LABEL
                : cmbModsSectionFilter.getValue();

        for (ModpackManager.OptionalEntry entry : modpackCatalog.optionalEntries()) {
            if (!matchesTypeFilter(entry, typeFilter)) {
                continue;
            }
            if (!FILTER_SECTION_ALL_LABEL.equals(sectionFilter) && !sectionFilter.equals(entry.section())) {
                continue;
            }
            if (!query.isBlank()) {
                String searchable = entry.displayName().toLowerCase(Locale.ROOT);
                if (!searchable.contains(query)) {
                    continue;
                }
            }
            filteredModEntries.add(entry);
        }

        int totalPages = Math.max(1, (int) Math.ceil(filteredModEntries.size() / (double) MODS_PAGE_SIZE));
        modsPageIndex = Math.max(0, Math.min(modsPageIndex, totalPages - 1));
        int from = modsPageIndex * MODS_PAGE_SIZE;
        int to = Math.min(filteredModEntries.size(), from + MODS_PAGE_SIZE);

        if (filteredModEntries.isEmpty()) {
            setModListMessage("No hay resultados para ese filtro.");
        } else {
            for (int i = from; i < to; i++) {
                modsListContainer.getChildren().add(buildModEntryRow(filteredModEntries.get(i)));
            }
        }
        updateModPaginationState();
    }

    private boolean matchesTypeFilter(ModpackManager.OptionalEntry entry, String typeFilter) {
        if (entry == null) {
            return false;
        }
        if (FILTER_TYPE_ALL_LABEL.equals(typeFilter)) {
            return true;
        }
        if (FILTER_TYPE_MODS_LABEL.equals(typeFilter)) {
            return entry.type() == ModpackManager.EntryType.MOD;
        }
        if (FILTER_TYPE_RESOURCEPACKS_LABEL.equals(typeFilter)) {
            return entry.type() == ModpackManager.EntryType.RESOURCEPACK;
        }
        return true;
    }

    private Node buildModEntryRow(ModpackManager.OptionalEntry entry) {
        HBox row = new HBox(10);
        row.getStyleClass().add("mod-item-row");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Image icon = resolveEntryIcon(entry);
        ImageView iconView = makeIcon(icon, 30);
        iconView.setFitHeight(30);
        iconView.setFitWidth(30);

        Label lblName = new Label(entry.displayName());
        lblName.getStyleClass().add("mod-item-name");
        Label lblFile = new Label(entry.fileName());
        lblFile.getStyleClass().add("mod-item-file");
        Label lblSection = new Label(entry.section());
        lblSection.getStyleClass().add("mod-item-section");

        VBox textCol = new VBox(2, lblName, lblFile, lblSection);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        ToggleButton toggle = new ToggleButton();
        toggle.getStyleClass().add("mod-toggle-btn");
        boolean enabled = workingModSelection != null && workingModSelection.isEnabled(entry.id());
        toggle.setSelected(enabled);
        toggle.setText(enabled ? "Activado" : "Desactivado");
        toggle.setOnAction(event -> {
            boolean nextValue = toggle.isSelected();
            toggle.setText(nextValue ? "Activado" : "Desactivado");
            applyModEntryToggle(entry, nextValue);
        });

        row.getChildren().addAll(iconView, textCol, toggle);
        return row;
    }

    private Image resolveEntryIcon(ModpackManager.OptionalEntry entry) {
        if (entry == null) {
            return modEntryIcon;
        }
        Image cached = entryIconCache.get(entry.id());
        if (cached != null) {
            return cached;
        }
        Image resolved = null;
        byte[] raw = entry.iconBytes();
        if (raw != null && raw.length > 0) {
            try {
                resolved = new Image(new ByteArrayInputStream(raw));
                if (resolved.isError()) {
                    resolved = null;
                }
            } catch (Exception ignored) {
                resolved = null;
            }
        }
        if (resolved == null) {
            resolved = entry.type() == ModpackManager.EntryType.MOD ? modEntryIcon : resourcepackEntryIcon;
        }
        if (resolved != null) {
            entryIconCache.put(entry.id(), resolved);
        }
        return resolved;
    }

    private void applyModEntryToggle(ModpackManager.OptionalEntry entry, boolean enabled) {
        if (modpackCatalog == null || entry == null) {
            return;
        }
        ModpackManager.Selection base = captureCurrentModSelection();
        if (base == null) {
            base = modpackManager.defaultSelectionForTemplate(modpackCatalog, ModpackManager.TEMPLATE_NORMAL);
        }
        LinkedHashSet<String> nextEnabled = new LinkedHashSet<>(base.enabledOptionalIds());
        if (enabled) {
            nextEnabled.add(entry.id());
        } else {
            nextEnabled.remove(entry.id());
        }

        String comboTemplate = cmbModpack == null ? ModpackManager.TEMPLATE_NORMAL : ModpackManager.normalizeTemplate(cmbModpack.getValue());
        String baseTemplate = base.baseTemplate();
        String selectedTemplate = comboTemplate;
        if (!ModpackManager.TEMPLATE_CUSTOM.equals(comboTemplate)) {
            selectedTemplate = ModpackManager.TEMPLATE_CUSTOM;
            baseTemplate = comboTemplate;
            suppressModpackTemplateEvents = true;
            selectComboValue(cmbModpack, ModpackManager.TEMPLATE_CUSTOM, ModpackManager.TEMPLATE_CUSTOM);
            suppressModpackTemplateEvents = false;
        }
        workingModSelection = new ModpackManager.Selection(selectedTemplate, baseTemplate, nextEnabled);
        refreshModEntriesView();
        onSettingsEdited();
    }

    private void updateModPaginationState() {
        int totalPages = Math.max(1, (int) Math.ceil(filteredModEntries.size() / (double) MODS_PAGE_SIZE));
        if (lblModsPage != null) {
            lblModsPage.setText("Pagina " + (modsPageIndex + 1) + "/" + totalPages);
        }
        if (lblModsCount != null) {
            lblModsCount.setText(filteredModEntries.size() + " elemento(s)");
        }
        if (btnModsPrevPage != null) {
            btnModsPrevPage.setDisable(modsPageIndex <= 0);
        }
        if (btnModsNextPage != null) {
            btnModsNextPage.setDisable(modsPageIndex >= totalPages - 1);
        }
    }

    private void setModListMessage(String message) {
        if (modsListContainer == null) {
            return;
        }
        modsListContainer.getChildren().clear();
        Label label = new Label(message);
        label.getStyleClass().add("settings-hint");
        modsListContainer.getChildren().add(label);
    }

    private ModpackManager.Selection captureCurrentModSelection() {
        if (modpackCatalog == null) {
            return persistedModSelection;
        }
        String selectedTemplate = cmbModpack == null || cmbModpack.getValue() == null
                ? ModpackManager.TEMPLATE_NORMAL
                : ModpackManager.normalizeTemplate(cmbModpack.getValue());

        if (workingModSelection == null) {
            return modpackManager.defaultSelectionForTemplate(modpackCatalog, selectedTemplate);
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>(workingModSelection.enabledOptionalIds());
        String baseTemplate = workingModSelection.baseTemplate();
        if (!ModpackManager.TEMPLATE_CUSTOM.equals(selectedTemplate)) {
            baseTemplate = selectedTemplate;
            ids = modpackManager.defaultEnabledIds(modpackCatalog, selectedTemplate);
        }
        return modpackManager.normalizeSelection(modpackCatalog, new ModpackManager.Selection(selectedTemplate, baseTemplate, ids));
    }

    private void restorePersistedSettings() {
        applyConfigToControls(persistedConfig);
        if (persistedModSelection != null) {
            workingModSelection = new ModpackManager.Selection(
                    persistedModSelection.selectedTemplate(),
                    persistedModSelection.baseTemplate(),
                    new LinkedHashSet<>(persistedModSelection.enabledOptionalIds())
            );
            suppressModpackTemplateEvents = true;
            selectComboValue(cmbModpack, persistedModSelection.selectedTemplate(), ModpackManager.TEMPLATE_NORMAL);
            suppressModpackTemplateEvents = false;
        }
        modsPageIndex = 0;
        refreshModEntriesView();
        refreshSaveState();
    }

    private boolean saveAllSettings() {
        LauncherStorage.LauncherConfig currentConfig = captureCurrentConfig();
        ModpackManager.Selection currentSelection = captureCurrentModSelection();
        LauncherStorage.saveConfig(currentConfig);
        persistedConfig = currentConfig;

        if (currentSelection != null) {
            persistedModSelection = currentSelection;
            workingModSelection = currentSelection;
            if (ModpackManager.TEMPLATE_CUSTOM.equals(currentSelection.selectedTemplate())) {
                modpackManager.saveCustomSelection(currentSelection);
            }
        }
        refreshSaveState();
        return true;
    }

    private void shakePendingActionButtons() {
        if (btnSaveSettings == null || btnDiscardSettings == null) {
            return;
        }
        Timeline saveShake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(btnSaveSettings.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(45), new KeyValue(btnSaveSettings.translateXProperty(), -6)),
                new KeyFrame(Duration.millis(90), new KeyValue(btnSaveSettings.translateXProperty(), 6)),
                new KeyFrame(Duration.millis(135), new KeyValue(btnSaveSettings.translateXProperty(), -4)),
                new KeyFrame(Duration.millis(180), new KeyValue(btnSaveSettings.translateXProperty(), 4)),
                new KeyFrame(Duration.millis(230), new KeyValue(btnSaveSettings.translateXProperty(), 0))
        );
        Timeline discardShake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(btnDiscardSettings.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(45), new KeyValue(btnDiscardSettings.translateXProperty(), 6)),
                new KeyFrame(Duration.millis(90), new KeyValue(btnDiscardSettings.translateXProperty(), -6)),
                new KeyFrame(Duration.millis(135), new KeyValue(btnDiscardSettings.translateXProperty(), 4)),
                new KeyFrame(Duration.millis(180), new KeyValue(btnDiscardSettings.translateXProperty(), -4)),
                new KeyFrame(Duration.millis(230), new KeyValue(btnDiscardSettings.translateXProperty(), 0))
        );
        saveShake.playFromStart();
        discardShake.playFromStart();
    }

    private void applySettingsSection(SettingsSection section, boolean enforceDirtyBlock) {
        if (section == null) {
            return;
        }
        if (enforceDirtyBlock && currentSettingsSection != section && hasPendingSettingsChanges()) {
            shakePendingActionButtons();
            showSettingsStatus("Debes guardar o descartar antes de cambiar de seccion.");
            return;
        }
        currentSettingsSection = section;
        setSectionVisible(paneSettingsGeneral, section == SettingsSection.GENERAL);
        setSectionVisible(paneSettingsModpacks, section == SettingsSection.MODPACKS);
        setSectionVisible(paneSettingsSystem, section == SettingsSection.SYSTEM);
        applySettingsTabState(btnSettingsTabGeneral, section == SettingsSection.GENERAL);
        applySettingsTabState(btnSettingsTabModpacks, section == SettingsSection.MODPACKS);
        applySettingsTabState(btnSettingsTabSystem, section == SettingsSection.SYSTEM);
        if (settingsScroll != null) {
            settingsScroll.setVvalue(0.0);
        }
    }

    private static void setSectionVisible(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static void applySettingsTabState(Button button, boolean active) {
        if (button == null) {
            return;
        }
        if (active) {
            if (!button.getStyleClass().contains("settings-tab-btn-active")) {
                button.getStyleClass().add("settings-tab-btn-active");
            }
        } else {
            button.getStyleClass().remove("settings-tab-btn-active");
        }
    }

    private void refreshJavaRuntimeStatusAsync() {
        if (lblJavaRuntimeStatus != null) {
            lblJavaRuntimeStatus.setText("Java: verificando...");
        }
        if (lblSystemJavaDetails != null) {
            lblSystemJavaDetails.setText("Java: verificando version requerida...");
        }
        updateStartupProgress(0.40, "Verificando Java...");
        CompletableFuture
                .supplyAsync(FabricMinecraftService::detectJavaRuntime)
                .thenAccept(info -> Platform.runLater(() -> {
                    updateJavaStatusLabels(info);
                    startupJavaReady = true;
                    updateStartupProgress(0.70, "Java verificado.");
                    tryFinishStartupOverlay();
                }));
    }

    private void updateJavaStatusLabels(FabricMinecraftService.JavaRuntimeInfo info) {
        if (info == null || !info.available()) {
            if (lblJavaRuntimeStatus != null) {
                lblJavaRuntimeStatus.setText("Java: no detectado");
                lblJavaRuntimeStatus.getStyleClass().remove("java-status-ok");
                if (!lblJavaRuntimeStatus.getStyleClass().contains("java-status-bad")) {
                    lblJavaRuntimeStatus.getStyleClass().add("java-status-bad");
                }
            }
            if (lblSystemJavaDetails != null) {
                lblSystemJavaDetails.setText("No se detecto Java. Instala Java " + FabricMinecraftService.REQUIRED_JAVA_MAJOR + " o superior.");
            }
            return;
        }
        int major = info.majorVersion();
        boolean supported = major >= FabricMinecraftService.REQUIRED_JAVA_MAJOR;
        if (lblJavaRuntimeStatus != null) {
            lblJavaRuntimeStatus.setText("Java: " + info.versionString() + (supported ? " (OK)" : " (incompatible)"));
            lblJavaRuntimeStatus.getStyleClass().remove("java-status-ok");
            lblJavaRuntimeStatus.getStyleClass().remove("java-status-bad");
            lblJavaRuntimeStatus.getStyleClass().add(supported ? "java-status-ok" : "java-status-bad");
        }
        if (lblSystemJavaDetails != null) {
            if (supported) {
                lblSystemJavaDetails.setText("Java detectado: " + info.versionString() + ". Ruta: " + info.javaBinaryPath());
            } else {
                lblSystemJavaDetails.setText(
                        "Java detectado: " + info.versionString() +
                                ". Debes usar Java " + FabricMinecraftService.REQUIRED_JAVA_MAJOR + " o superior."
                );
            }
        }
    }

    private void animateEntrance() {
        Node sideNode = sidebarGlass != null ? sidebarGlass : sidebarScroll;
        sideNode.setTranslateX(-230);
        sideNode.setOpacity(0);

        centerContent.setOpacity(0);
        centerContent.setTranslateY(18);

        ParallelTransition sideAnim = parallel(slide(sideNode, 0, 520), fade(sideNode, 1.0, 440));
        ParallelTransition centerAnim = parallel(fade(centerContent, 1.0, 520), slideY(centerContent, 0, 520));

        sideAnim.setOnFinished(event -> {
            centerAnim.play();
            startTitleFloat();
        });
        sideAnim.play();
    }

    private void startTitleFloat() {
        TranslateTransition transition = new TranslateTransition(Duration.seconds(3.6), imgTitle);
        transition.setByY(-10);
        transition.setAutoReverse(true);
        transition.setCycleCount(Animation.INDEFINITE);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        transition.play();
    }

    private void scheduleSemmie() {
        delay(2600, this::semmieLoop);
    }

    private void semmieLoop() {
        walkSemmie();
        delay(60_000, this::semmieLoop);
    }

    private void walkSemmie() {
        double width = semmiePane.getWidth() > 0 ? semmiePane.getWidth() : 240;
        imgSemmie.setLayoutX(-90);
        imgSemmie.setTranslateX(0);

        TranslateTransition transition = new TranslateTransition(Duration.seconds(6.4), imgSemmie);
        transition.setFromX(0);
        transition.setToX(width + 110);
        transition.setInterpolator(Interpolator.LINEAR);
        transition.setOnFinished(event -> imgSemmie.setLayoutX(-90));
        transition.play();
    }

    @FXML
    private void onMinimize() {
        Stage targetStage = resolveStage();
        if (targetStage != null) {
            targetStage.setIconified(true);
        }
    }

    @FXML
    private void onMaxRestore() {
        toggleMaxRestore();
    }

    @FXML
    private void onClose() {
        Stage targetStage = resolveStage();
        shutdownBackgroundServices();
        if (targetStage != null) {
            targetStage.close();
        }
    }

    @FXML
    private void onHome() {
        if (!allowSettingsViewExit()) {
            return;
        }
        showView(ViewMode.HOME, true);
    }

    @FXML
    private void onSkin() {
        if (!allowSettingsViewExit()) {
            return;
        }
        showInfo("Skin Manager estara disponible en una proxima version.");
        showView(ViewMode.HOME, true);
    }

    @FXML
    private void onSettings() {
        showView(ViewMode.SETTINGS, true);
    }

    @FXML
    private void onSaveSettings() {
        saveAllSettings();
        showSettingsStatus("Guardado!");
    }

    @FXML
    private void onDiscardSettings() {
        restorePersistedSettings();
        showSettingsStatus("Cambios descartados.");
    }

    @FXML
    private void onSettingsTabGeneral() {
        applySettingsSection(SettingsSection.GENERAL, true);
    }

    @FXML
    private void onSettingsTabModpacks() {
        applySettingsSection(SettingsSection.MODPACKS, true);
    }

    @FXML
    private void onSettingsTabSystem() {
        applySettingsSection(SettingsSection.SYSTEM, true);
    }

    @FXML
    private void onModsPrevPage() {
        if (modsPageIndex > 0) {
            modsPageIndex--;
            refreshModEntriesView();
        }
    }

    @FXML
    private void onModsNextPage() {
        int totalPages = Math.max(1, (int) Math.ceil(filteredModEntries.size() / (double) MODS_PAGE_SIZE));
        if (modsPageIndex < totalPages - 1) {
            modsPageIndex++;
            refreshModEntriesView();
        }
    }

    @FXML
    private void onDiscord() {
        try {
            java.awt.Desktop.getDesktop().browse(new URI("https://discord.gg/tu-servidor"));
        } catch (Exception ignored) {
        }
    }

    @FXML
    private void onPlay() {
        if (launchInProgress || minecraftRunning) {
            if (minecraftRunning) {
                showInfo("Minecraft ya esta en ejecucion.");
            }
            return;
        }
        String user = lblUsername.getText().trim();
        if (user.isEmpty() || user.equals("--") || user.equals("-")) {
            showInfo("Configura tu nombre de usuario primero.");
            return;
        }

        FabricMinecraftService.JavaRuntimeInfo javaInfo = FabricMinecraftService.detectJavaRuntime();
        if (!javaInfo.available() || javaInfo.majorVersion() < FabricMinecraftService.REQUIRED_JAVA_MAJOR) {
            showView(ViewMode.SETTINGS, true);
            applySettingsSection(SettingsSection.SYSTEM, false);
            shakePendingActionButtons();
            showInfo(
                    "No se puede iniciar porque falta Java compatible.\n\n" +
                            "Requerido: Java " + FabricMinecraftService.REQUIRED_JAVA_MAJOR + "+.\n" +
                            "Detectado: " + (javaInfo.versionString().isBlank() ? "no detectado" : javaInfo.versionString())
            );
            refreshJavaRuntimeStatusAsync();
            return;
        }

        saveAllSettings();
        final LauncherStorage.LauncherConfig launchConfig = persistedConfig;

        lblStatus.setText("Preparando lanzamiento...");
        updateInstallPresence("Preparando lanzamiento...");
        setPlayBusy(true);
        showLoadingScreen(user);

        CompletableFuture.runAsync(() -> {
            try {
                FabricMinecraftService service = new FabricMinecraftService();
                FabricMinecraftService.LaunchResult result = service.installAndLaunch(
                        user,
                        launchConfig,
                        (status, total, currentDownload) -> Platform.runLater(() ->
                                updateLoadingProgressUi(status, total, currentDownload)
                        )
                );
                Platform.runLater(() -> handleLaunchSuccess(launchConfig, result));
            } catch (Exception ex) {
                Platform.runLater(() -> handleLaunchFailure(ex));
            }
        });
    }

    private void showView(ViewMode mode, boolean animate) {
        if (mode != ViewMode.SETTINGS && !allowSettingsViewExit()) {
            return;
        }
        viewMode = mode;
        boolean showHome = mode == ViewMode.HOME;

        if (centerContent != null) {
            centerContent.setManaged(showHome);
            centerContent.setVisible(showHome);
        }
        Node settingsNode = settingsView != null ? settingsView : settingsScroll;
        if (settingsNode != null) {
            settingsNode.setManaged(!showHome);
            settingsNode.setVisible(!showHome);
            if (!showHome) {
                if (settingsScroll != null) {
                    settingsScroll.setVvalue(0.0);
                }
            }
        }

        applyNavState(btnNavHome, showHome);
        applyNavState(btnNavSettings, !showHome);
        applyNavState(btnNavSkin, false);

        if (!loggedIn && login != null) {
            if (showHome) {
                setLoginOverlayVisible(true);
            } else {
                setLoginOverlayVisible(false);
            }
        }

        if (animate) {
            Node target = showHome ? centerContent : settingsNode;
            if (target != null) {
                target.setOpacity(0.0);
                target.setTranslateY(10.0);
                ParallelTransition transition = parallel(
                        fade(target, 1.0, 230),
                        slideY(target, 0, 230)
                );
                transition.play();
            }
        } else if (centerContent != null && settingsNode != null) {
            centerContent.setOpacity(showHome ? 1.0 : 0.0);
            settingsNode.setOpacity(showHome ? 0.0 : 1.0);
            centerContent.setTranslateY(0.0);
            settingsNode.setTranslateY(0.0);
        }
        refreshLauncherRuntimeStateUi();
    }

    private boolean allowSettingsViewExit() {
        if (viewMode != ViewMode.SETTINGS) {
            return true;
        }
        if (!hasPendingSettingsChanges()) {
            return true;
        }
        shakePendingActionButtons();
        showSettingsStatus("Guarda o descarta cambios antes de salir.");
        return false;
    }

    private static void applyNavState(Button button, boolean active) {
        if (button == null) {
            return;
        }
        if (active) {
            if (!button.getStyleClass().contains("nav-btn-active")) {
                button.getStyleClass().add("nav-btn-active");
            }
        } else {
            button.getStyleClass().remove("nav-btn-active");
        }
    }

    private void showSettingsStatus(String text) {
        if (lblSettingsStatus == null) {
            return;
        }

        lblSettingsStatus.setText(text);
        lblSettingsStatus.setOpacity(1.0);
        if (settingsStatusFade != null) {
            settingsStatusFade.stop();
        }
        settingsStatusFade = new Timeline(
                new KeyFrame(Duration.millis(2500), new KeyValue(lblSettingsStatus.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(3800), new KeyValue(lblSettingsStatus.opacityProperty(), 0.0))
        );
        settingsStatusFade.play();
    }

    private void setLoginOverlayVisible(boolean visible) {
        if (login == null) {
            return;
        }
        login.setManaged(visible);
        login.setVisible(visible);
        login.setMouseTransparent(!visible);
        login.setOpacity(visible ? 1.0 : 0.0);
        applyLoginBackdropFx(visible);
    }

    private void applyLoginBackdropFx(boolean active) {
        if (sidebarGlass != null) {
            sidebarGlass.setOpacity(active ? 0.56 : 1.0);
            sidebarGlass.setEffect(active ? loginBlurLight : null);
        }
        if (centerContent != null) {
            centerContent.setOpacity(active ? 0.42 : 1.0);
            centerContent.setEffect(active ? loginBlurHeavy : null);
        }
        Node settingsNode = settingsView != null ? settingsView : settingsScroll;
        if (settingsNode != null) {
            settingsNode.setEffect(active ? loginBlurLight : null);
        }
        if (titleBar != null) {
            titleBar.setOpacity(active ? 0.76 : 1.0);
        }
    }

    private void showLoadingScreen(String username) {
        stopProgressTimeline();

        totalProgress = 0.0;
        downloadProgress = 0.0;
        lblLoadStatus.setText(LOAD_STEPS[0]);
        lblTimer.setText("00:00");
        updateProgressBars();

        loadingOverlay.setVisible(true);
        loadingOverlay.setOpacity(0.0);
        fade(loadingOverlay, 1.0, 300).play();
        Platform.runLater(this::updateProgressBars);

        startIndicatorAnimations();
    }

    private void updateLoadingProgressUi(String status, double total, double currentDownload) {
        if (status != null && !status.isBlank()) {
            lblLoadStatus.setText(status);
            lblStatus.setText(status);
            updateInstallPresence(status);
        }
        totalProgress = clamp(total, 0.0, 1.0);
        downloadProgress = clamp(currentDownload, 0.0, 1.0);
        updateProgressBars();
    }

    private void handleLaunchSuccess(LauncherStorage.LauncherConfig config, FabricMinecraftService.LaunchResult result) {
        setPlayBusy(false);
        updateLoadingProgressUi("Minecraft iniciado", 1.0, 1.0);
        if (result != null) {
            trackMinecraftProcess(result.process(), result.loaderVersion());
        } else {
            refreshMinecraftRunningState();
        }

        if (config.closeLauncherOnStart()) {
            Stage targetStage = resolveStage();
            if (targetStage != null) {
                targetStage.close();
            }
            return;
        }
        dismissLoadingOverlay();
        refreshLauncherRuntimeStateUi();
    }

    private void handleLaunchFailure(Exception ex) {
        setPlayBusy(false);
        dismissLoadingOverlay();
        lblStatus.setText("Error al iniciar");
        String msg = ex == null ? "Error desconocido" : ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex == null ? "Error desconocido" : ex.toString();
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("connection reset")
                || lower.contains("timed out")
                || lower.contains("forcibly closed")
                || lower.contains("connection aborted")) {
            msg += "\n\nParece un corte temporal de red. Puedes reintentar: el launcher reutiliza lo ya descargado.";
        }
        pushDiscordPresence(DiscordRpcService.LauncherRpcState.IDLE, "Error al iniciar");
        showInfo("No se pudo iniciar Minecraft.\n\n" + msg);
        refreshMinecraftRunningState();
    }

    private void dismissLoadingOverlay() {
        stopIndicatorAnimations();
        FadeTransition out = fade(loadingOverlay, 0.0, 260);
        out.setOnFinished(event -> loadingOverlay.setVisible(false));
        out.play();
    }

    private void setPlayBusy(boolean busy) {
        launchInProgress = busy;
        applyPlayAvailability();
    }

    private void startIndicatorAnimations() {
        stopIndicatorAnimations();
        totalIndicatorLoop = createIndicatorLoop(progressTrackTotal, progressIndTotal, 1200);
        downloadIndicatorLoop = createIndicatorLoop(progressTrackDownload, progressIndDownload, 980);
        totalIndicatorLoop.play();
        downloadIndicatorLoop.play();
    }

    private Timeline createIndicatorLoop(StackPane track, Region indicator, int ms) {
        final double indicatorWidth = indicator.getPrefWidth() > 0 ? indicator.getPrefWidth() : 74;
        final double startOffset = -indicatorWidth - 22;
        final double[] pos = {startOffset};
        final double frameMs = 16.0;

        Timeline loop = new Timeline(new KeyFrame(Duration.millis(frameMs), event -> {
            double trackWidth = resolveTrackContentWidth(track);
            double speed = (trackWidth + indicatorWidth + 44) / (ms / frameMs);
            pos[0] += speed;
            if (pos[0] > trackWidth + 22) {
                pos[0] = startOffset;
            }
            indicator.setTranslateX(pos[0]);
        }));
        loop.setCycleCount(Animation.INDEFINITE);
        indicator.toFront();
        return loop;
    }

    private void stopIndicatorAnimations() {
        if (totalIndicatorLoop != null) {
            totalIndicatorLoop.stop();
            totalIndicatorLoop = null;
        }
        if (downloadIndicatorLoop != null) {
            downloadIndicatorLoop.stop();
            downloadIndicatorLoop = null;
        }
        progressIndTotal.setTranslateX(-120);
        progressIndDownload.setTranslateX(-120);
    }

    private void runProgressTimeline(String username) {
        final int totalTicks = 220;
        final int[] tick = {0};
        final int[] secs = {0};
        final int[] stepIdx = {-1};

        progressTimeline = new Timeline(new KeyFrame(Duration.millis(70), event -> {
            tick[0]++;

            double totalTarget = Math.min(1.0, tick[0] / (double) totalTicks);
            totalProgress += (totalTarget - totalProgress) * 0.14;
            totalProgress = clamp(totalProgress, 0.0, 1.0);

            int step = Math.min(LOAD_STEPS.length - 1, (int) Math.floor(totalProgress * LOAD_STEPS.length));
            if (step != stepIdx[0]) {
                stepIdx[0] = step;
                lblLoadStatus.setText(LOAD_STEPS[step]);
                downloadProgress = 0.0;
            }

            double stepSpan = 1.0 / LOAD_STEPS.length;
            double stepStart = step * stepSpan;
            double withinStep = clamp((totalProgress - stepStart) / stepSpan, 0.0, 1.0);
            double downloadTarget = step == LOAD_STEPS.length - 1 ? 1.0 : withinStep;
            downloadProgress += (downloadTarget - downloadProgress) * 0.20;
            downloadProgress = clamp(downloadProgress, 0.0, 1.0);

            if (tick[0] % 14 == 0) {
                secs[0]++;
                lblTimer.setText(String.format(Locale.US, "%02d:%02d", secs[0] / 60, secs[0] % 60));
            }

            updateProgressBars();
        }));

        progressTimeline.setCycleCount(totalTicks + 10);
        progressTimeline.setOnFinished(event -> finishLoading(username));
        progressTimeline.play();
    }

    private void finishLoading(String username) {
        stopProgressTimeline();

        totalProgress = 1.0;
        downloadProgress = 1.0;
        updateProgressBars();
        lblLoadStatus.setText("Listo. Iniciando SemenCraft...");

        delay(1000, () -> {
            FadeTransition fadeOut = fade(loadingOverlay, 0.0, 360);
            fadeOut.setOnFinished(event -> {
                stopIndicatorAnimations();
                loadingOverlay.setVisible(false);
                lblStatus.setText("Listo");
                Platform.runLater(() -> showInfo("Bienvenido, " + username + ".\n\nSemenCraft se inicio correctamente."));
            });
            fadeOut.play();
        });
    }

    private void stopProgressTimeline() {
        if (progressTimeline != null) {
            progressTimeline.stop();
            progressTimeline = null;
        }
    }

    private void updateProgressBars() {
        double totalTrackW = resolveTrackContentWidth(progressTrackTotal);
        double downloadTrackW = resolveTrackContentWidth(progressTrackDownload);
        double totalFillW = Math.rint(totalTrackW * totalProgress);
        double downloadFillW = Math.rint(downloadTrackW * downloadProgress);

        setRegionWidth(progressFillTotal, totalFillW);
        setRegionWidth(progressFillDownload, downloadFillW);

        lblTotalPercent.setText(String.format(Locale.US, "%.0f%%", totalProgress * 100.0));
        lblDownloadPercent.setText(String.format(Locale.US, "%.0f%%", downloadProgress * 100.0));
    }

    private static void setRegionWidth(Region region, double width) {
        double safeWidth = Math.max(0.0, width);
        region.setMinWidth(safeWidth);
        region.setPrefWidth(safeWidth);
        region.setMaxWidth(safeWidth);
    }

    private static double resolveTrackWidth(Region track) {
        double width = track.getWidth();
        if (width > 0) {
            return width;
        }
        width = track.getPrefWidth();
        if (width > 0) {
            return width;
        }
        return PROGRESS_FALLBACK_W;
    }

    private static double resolveTrackContentWidth(Region track) {
        double width = resolveTrackWidth(track);
        Insets insets = track.getInsets();
        if (insets != null) {
            width -= insets.getLeft() + insets.getRight();
        }
        return Math.max(0.0, width);
    }

    static FadeTransition fade(Node n, double to, int ms) {
        FadeTransition transition = new FadeTransition(Duration.millis(ms), n);
        transition.setToValue(to);
        return transition;
    }

    private static TranslateTransition slide(Node n, double toX, int ms) {
        TranslateTransition transition = new TranslateTransition(Duration.millis(ms), n);
        transition.setToX(toX);
        transition.setInterpolator(Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0));
        return transition;
    }

    private static TranslateTransition slideY(Node n, double toY, int ms) {
        TranslateTransition transition = new TranslateTransition(Duration.millis(ms), n);
        transition.setToY(toY);
        transition.setInterpolator(Interpolator.EASE_OUT);
        return transition;
    }

    private static ParallelTransition parallel(Animation... animations) {
        return new ParallelTransition(animations);
    }

    static void delay(int ms, Runnable action) {
        PauseTransition transition = new PauseTransition(Duration.millis(ms));
        transition.setOnFinished(event -> action.run());
        transition.play();
    }

    private static DoubleBinding clamp(ObservableDoubleValue value, double min, double max) {
        return Bindings.createDoubleBinding(
                () -> Math.max(min, Math.min(max, value.get())),
                value
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("SemenCraft Launcher");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
