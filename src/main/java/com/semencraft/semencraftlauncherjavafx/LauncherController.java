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
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
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
    @FXML private ScrollPane settingsScroll;
    @FXML private VBox settingsRoot;
    @FXML private ComboBox<String> cmbModpack;
    @FXML private Slider sliderRam;
    @FXML private Label lblRamValue;
    @FXML private ComboBox<String> cmbLaunchMode;
    @FXML private TextField txtResolution;
    @FXML private CheckBox chkBorderlessWindow;
    @FXML private CheckBox chkCloseLauncherOnStart;
    @FXML private CheckBox chkDiscordRpc;
    @FXML private Button btnSaveSettings;
    @FXML private Label lblSettingsStatus;

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
    private LauncherStorage.LauncherConfig persistedConfig = LauncherStorage.LauncherConfig.defaults();
    private Image playImageDefault;
    private Image playImageHover;
    private Image playImagePressed;
    private Timeline minecraftStatePoller;
    private Process minecraftProcess;
    private String activeLoaderVersion = "";
    private String cachedMinecraftDirLower;

    private double totalProgress;
    private double downloadProgress;

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

    @FXML
    public void initialize() {
        loadImages();
        PixelNinePatchSupport.apply(sidebar);
        setupSidebarGlass();
        setupStageResolver();
        setupResponsiveBindings();
        setupNavButtons();
        setupDiscordButton();
        setupLoadingVisuals();
        setupSettingsView();
        bootstrapSavedSession();

        if (loginController != null) {
            loginController.setOnSuccess(this::onLoginSuccess);
        }

        showView(ViewMode.HOME, false);
        startDiscordRpc();
        startMinecraftStatePolling();
        animateEntrance();
        scheduleSemmie();
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
        if (settingsScroll != null) {
            settingsScroll.setPannable(true);
            settingsScroll.setFitToWidth(true);
        }

        if (cmbModpack != null) {
            cmbModpack.getItems().setAll(
                    "Normal",
                    "Optimizado"
            );
        }
        if (cmbLaunchMode != null) {
            cmbLaunchMode.getItems().setAll(
                    "Ventana",
                    "Pantalla completa",
                    "Ventana maximizada"
            );
        }

        persistedConfig = LauncherStorage.loadConfig();
        applyConfigToControls(persistedConfig);
        registerSettingsDirtyListeners();
        installSettingsTooltips();
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
        installSideTooltip(cmbModpack, "Normal: experiencia completa. Optimizado: menos carga para mejor FPS.");
        installSideTooltip(cmbLaunchMode, "Selecciona como se abrira el juego al iniciar.");
        installSideTooltip(txtResolution, "Formato recomendado: ancho x alto, por ejemplo 1280x720.");
        installSideTooltip(sliderRam, "Asigna memoria al juego. No uses mas de lo que tu PC soporta.");
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
            cmbModpack.valueProperty().addListener((obs, oldV, newV) -> onSettingsEdited());
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
            selectComboValue(cmbModpack, config.modpack(), "Normal");
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
        String modpack = cmbModpack != null && cmbModpack.getValue() != null ? cmbModpack.getValue() : "Normal";
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
        boolean dirty = !captureCurrentConfig().equals(persistedConfig);
        btnSaveSettings.setDisable(!dirty);
    }

    private void updateRamLabel() {
        if (sliderRam == null || lblRamValue == null) {
            return;
        }
        int ramGb = (int) Math.round(sliderRam.getValue());
        lblRamValue.setText(ramGb + " GB");
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
        showView(ViewMode.HOME, true);
    }

    @FXML
    private void onSkin() {
        showInfo("Skin Manager estara disponible en una proxima version.");
        showView(ViewMode.HOME, true);
    }

    @FXML
    private void onSettings() {
        showView(ViewMode.SETTINGS, true);
    }

    @FXML
    private void onSaveSettings() {
        LauncherStorage.LauncherConfig current = captureCurrentConfig();
        LauncherStorage.saveConfig(current);
        persistedConfig = current;
        refreshSaveState();
        showSettingsStatus("Guardado!");
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

        LauncherStorage.LauncherConfig launchConfig = captureCurrentConfig();
        LauncherStorage.saveConfig(launchConfig);
        persistedConfig = launchConfig;
        refreshSaveState();

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
        viewMode = mode;
        boolean showHome = mode == ViewMode.HOME;

        if (centerContent != null) {
            centerContent.setManaged(showHome);
            centerContent.setVisible(showHome);
        }
        if (settingsScroll != null) {
            settingsScroll.setManaged(!showHome);
            settingsScroll.setVisible(!showHome);
            if (!showHome) {
                settingsScroll.setVvalue(0.0);
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
            Node target = showHome ? centerContent : settingsScroll;
            if (target != null) {
                target.setOpacity(0.0);
                target.setTranslateY(10.0);
                ParallelTransition transition = parallel(
                        fade(target, 1.0, 230),
                        slideY(target, 0, 230)
                );
                transition.play();
            }
        } else if (centerContent != null && settingsScroll != null) {
            centerContent.setOpacity(showHome ? 1.0 : 0.0);
            settingsScroll.setOpacity(showHome ? 0.0 : 1.0);
            centerContent.setTranslateY(0.0);
            settingsScroll.setTranslateY(0.0);
        }
        refreshLauncherRuntimeStateUi();
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
        if (settingsScroll != null) {
            settingsScroll.setEffect(active ? loginBlurLight : null);
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
