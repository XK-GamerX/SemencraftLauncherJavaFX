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
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LauncherController {

    @FXML private HBox titleBar;
    @FXML private HBox titleBarDragArea;
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
    @FXML private Button btnNavTeam;
    @FXML private Button btnNavSettings;
    @FXML private StackPane currentUserAvatar;
    @FXML private Label lblUsername;
    @FXML private Button btnDiscord;

    @FXML private StackPane centerLayer;
    @FXML private StackPane centerRoot;
    @FXML private VBox centerContent;
    @FXML private ImageView imgTitle;
    @FXML private Button btnPlay;
    @FXML private Label lblStatus;
    @FXML private Region titleGap;
    @FXML private VBox skinView;
    @FXML private ScrollPane skinScroll;
    @FXML private VBox skinRoot;
    @FXML private VBox settingsView;
    @FXML private ScrollPane settingsScroll;
    @FXML private VBox settingsRoot;
    @FXML private Button btnSettingsTabGeneral;
    @FXML private Button btnSettingsTabModpacks;
    @FXML private Button btnSettingsTabResourcepacks;
    @FXML private Button btnSettingsTabSystem;
    @FXML private StackPane notificationsAnchor;
    @FXML private VBox teamView;
    @FXML private ScrollPane teamScroll;
    @FXML private VBox teamRoot;
    @FXML private Button btnNotifications;
    @FXML private StackPane notificationBadge;
    @FXML private Label lblNotificationCount;
    @FXML private StackPane notificationsDropdown;
    @FXML private ImageView imgNotificationsBook;
    @FXML private ScrollPane notificationsScroll;
    @FXML private VBox notificationsList;
    @FXML private Button btnCloseNotifications;
    @FXML private Label lblJavaRuntimeStatus;
    @FXML private VBox paneSettingsGeneral;
    @FXML private VBox paneSettingsModpacks;
    @FXML private VBox paneSettingsResourcepacks;
    @FXML private VBox paneSettingsSystem;
    @FXML private ComboBox<String> cmbModpack;
    @FXML private TextField txtModSearch;
    @FXML private FlowPane modsSectionFilterChips;
    @FXML private Label lblModsCount;
    @FXML private Button btnModsPrevPage;
    @FXML private Label lblModsPage;
    @FXML private Button btnModsNextPage;
    @FXML private VBox modsListContainer;
    @FXML private VBox resourcepacksAvailableContainer;
    @FXML private VBox resourcepacksSelectedContainer;
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

    @FXML private Label lblTitleVersion;
    @FXML private Label lblVersion;
    @FXML private Label lblUserState;
    @FXML private Region userStateDot;
    @FXML private CheckBox chkNotificationSound;
    @FXML private CheckBox chkPerformanceMode;
    @FXML private Label lblLauncherVersion;
    @FXML private Label lblUpdateStatus;
    @FXML private Button btnCheckUpdates;
    @FXML private Label lblNotificationsHeaderCount;
    @FXML private VBox toastLayer;
    @FXML private StackPane modalLayer;
    @FXML private Button btnConfigure;
    @FXML private Button btnModsList;
    @FXML private HBox userBlock;

    private Stage stage;
    private Timeline progressTimeline;
    private Timeline totalIndicatorLoop;
    private Timeline downloadIndicatorLoop;
    private Timeline settingsStatusFade;
    private double windowDragOffsetX;
    private double windowDragOffsetY;
    private boolean windowControlsBound;
    private boolean customMaximized;
    private double restoreX;
    private double restoreY;
    private double restoreWidth;
    private double restoreHeight;
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
    private final DiscordRpcService discordRpc = new DiscordRpcService();
    private final ModpackManager modpackManager = new ModpackManager();
    private final SupabaseTeamsService teamsService = new SupabaseTeamsService();
    private final SupabaseSkinService skinService = new SupabaseSkinService();
    private LauncherStorage.LauncherConfig persistedConfig = LauncherStorage.LauncherConfig.defaults();
    private ModpackManager.Catalog modpackCatalog;
    private ModpackManager.Selection persistedModSelection;
    private ModpackManager.Selection workingModSelection;
    private final List<ModpackManager.OptionalEntry> filteredModEntries = new ArrayList<>();
    private final List<ModpackManager.OptionalEntry> resourcepackEntries = new ArrayList<>();
    private final LinkedHashSet<String> activeModSections = new LinkedHashSet<>();
    private final Map<String, Image> entryIconCache = new HashMap<>();
    private final Map<String, Image> minecraftSkinImageCache = new ConcurrentHashMap<>();
    private final Set<String> minecraftSkinLoadsInFlight = ConcurrentHashMap.newKeySet();
    private Image modEntryIcon;
    private Image resourcepackEntryIcon;
    private Image teamNavIcon;
    private Image bellIcon;
    private Image cameraIcon;
    private Image crownIcon;
    private Image editIcon;
    private Image steveSkinAsset;
    private Timeline minecraftStatePoller;
    private Timeline notificationBadgePoller;
    private Timeline presenceHeartbeatTimeline;
    private Timeline presencePollTimeline;
    private final Map<String, Circle> presenceDotsByUsername = new HashMap<>();
    private final Map<String, String> presenceStatusByUsername = new ConcurrentHashMap<>();
    private volatile long lastUserActivityMs = System.currentTimeMillis();
    private boolean presenceActivityTrackingBound;
    private static final int PRESENCE_HEARTBEAT_SECONDS = 20;
    private static final int PRESENCE_POLL_SECONDS = 20;
    private static final long PRESENCE_IDLE_THRESHOLD_MS = 3 * 60 * 1000L;
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
    private StartupProgressListener startupProgressListener;
    private SettingsSection currentSettingsSection = SettingsSection.GENERAL;
    private TeamPanelMode teamPanelMode = TeamPanelMode.EMPTY;
    private SupabaseTeamsService.TeamContext currentTeamContext;
    private SupabaseSkinService.SkinContext currentSkinContext;
    private String pendingTeamName = "";
    private String pendingTeamColorHex = "#9EC0FF";
    private String pendingTeamIconBase64 = "";
    private Image pendingTeamIconPreview;
    private String pendingTeamDraftError = "";
    private String pendingSkinBase64 = "";
    private Image pendingSkinPreview;
    private String pendingSkinModel = "wide";
    private String pendingSkinDraftError = "";
    private String skinContextErrorMessage = "";
    private List<String> persistedResourcepackSelection = List.of();
    private List<String> workingResourcepackSelection = List.of();
    private int notificationBadgeCount;
    private boolean notificationBadgeRefreshInFlight;
    private boolean notificationsOpen;
    private boolean skinContextRefreshInFlight;
    private final LauncherUpdater launcherUpdater = new LauncherUpdater();
    private final List<LocalNotification> localNotifications = new ArrayList<>();
    private int unreadLocalNotifications;
    private int lastAnnouncedUnreadCount = -1;
    private boolean updateCheckInFlight;
    private boolean updateInstallInProgress;
    private long lastProcessScanMs;
    private boolean lastProcessScanResult;
    /** Alto de la tarjeta de skin sin contar el modelo 3D (cabecera, botones, margenes). */
    private static final double SKIN_CARD_CHROME_HEIGHT = 262;
    /** Radio interior de las cabezas de jugador; debe ir con .minecraft-head-inner en style.css. */
    private static final double HEAD_INNER_RADIUS = 5;
    private SkinModelView currentSkinModelView;
    private TranslateTransition titleFloatTransition;
    private boolean performanceMode;
    /** "Optimizar launcher": sin animaciones ni efectos caros. */
    private static volatile boolean reducedMotion;

    private double totalProgress;
    private double downloadProgress;
    private double startupProgressValue;

    private static final int MODS_PAGE_SIZE = 8;
    private static final double NOTIFICATION_BADGE_POLL_SECONDS = 8.0;
    private static final long PROCESS_SCAN_INTERVAL_MS = 8_000L;
    private static final int MAX_LOCAL_NOTIFICATIONS = 12;
    private static final DateTimeFormatter NOTIFICATION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES")).withZone(ZoneId.systemDefault());

    // Iconos vectoriales (caja de 10x10 para ventana, 16x16 para el resto).
    private static final String ICON_MINIMIZE = "M0 4.5 H10 V5.5 H0 Z";
    private static final String ICON_MAXIMIZE = "M0 0 H10 V10 H0 Z M1 1 V9 H9 V1 Z";
    private static final String ICON_RESTORE = "M2 0 H10 V8 H8 V7 H9 V1 H3 V2 H2 Z M0 2 H8 V10 H0 Z M1 3 V9 H7 V3 Z";
    private static final String ICON_CLOSE = "M0.7 0 L5 4.3 L9.3 0 L10 0.7 L5.7 5 L10 9.3 L9.3 10 L5 5.7 L0.7 10 L0 9.3 L4.3 5 L0 0.7 Z";
    private static final String ICON_BELL = "M8 1 C5.3 1 3.5 3.1 3.5 5.8 V9 L2 11.2 V12.2 H14 V11.2 L12.5 9 V5.8 C12.5 3.1 10.7 1 8 1 Z M6.1 13.2 C6.3 14.3 7.1 15 8 15 C8.9 15 9.7 14.3 9.9 13.2 Z";
    // Iconos de 24x24 (estilo Material).
    private static final String ICON_NAV_HOME = "M12 3.2 L2.5 11.6 H5.4 V20.5 H10 V14.6 H14 V20.5 H18.6 V11.6 H21.5 Z";
    private static final String ICON_NAV_SKIN = "M4 4 H20 V20 H4 Z M7 9 V12 H10 V9 Z M14 9 V12 H17 V9 Z M9 14 V17 H15 V14 Z";
    private static final String ICON_NAV_TEAM = "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z";
    private static final String ICON_NAV_SETTINGS = "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";
    private static final String ICON_PLAY = "M7 4.5 V19.5 L19.5 12 Z";
    private static final String ICON_TUNE = "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z";
    private static final String ICON_LIST = "M4 10.5c-.83 0-1.5.67-1.5 1.5s.67 1.5 1.5 1.5 1.5-.67 1.5-1.5-.67-1.5-1.5-1.5zm0-6c-.83 0-1.5.67-1.5 1.5S3.17 7.5 4 7.5 5.5 6.83 5.5 6 4.83 4.5 4 4.5zm0 12c-.83 0-1.5.68-1.5 1.5s.68 1.5 1.5 1.5 1.5-.68 1.5-1.5-.67-1.5-1.5-1.5zM7 19h14v-2H7v2zm0-6h14v-2H7v2zm0-8v2h14V5H7z";
    private static final List<String> TEAM_COLOR_OPTIONS = List.of(
            "#FF6B6B", "#FF7A63", "#FF8B5E", "#FF9E5E", "#FFB45F", "#FFC862", "#F0D85F", "#D7E25E",
            "#BAE86A", "#94E67B", "#72E08E", "#5AD7A2", "#56D5BA", "#59D2CF", "#60CBE3", "#6BC0F5",
            "#78B2FF", "#87A3FF", "#9795FF", "#A889FF", "#B97FFF", "#CA7CFF", "#DC7EF8", "#EF84EC",
            "#FF8ADB", "#FF8FC7", "#FF95B1", "#D4DBE6", "#BEC7D5", "#AAB4C5", "#96A2B5", "#7F8CA1"
    );
    private static final String FILTER_SECTION_ALL_LABEL = "Todas";
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

    /** Notificaciones propias del launcher (actualizaciones, cambios del modpack). */
    private record LocalNotification(
            String kind,
            String title,
            String body,
            Instant createdAt,
            String actionLabel,
            Runnable action
    ) {
    }

    private enum ViewMode {
        HOME,
        SKIN,
        TEAM,
        SETTINGS
    }

    private enum SettingsSection {
        GENERAL,
        MODPACKS,
        RESOURCEPACKS,
        SYSTEM
    }

    private enum TeamPanelMode {
        EMPTY,
        CREATE,
        EDIT,
        JOIN
    }

    /**
     * Permite que quien cree el controlador (MainApp) reciba el progreso de
     * arranque (verificacion de Java, carga del catalogo de modpacks, etc.)
     * para poder mostrarlo en una ventana splash externa en vez del overlay
     * interno de la escena principal.
     */
    interface StartupProgressListener {
        void onProgress(double progress, String status);
        void onReady();
    }

    void setStartupProgressListener(StartupProgressListener listener) {
        this.startupProgressListener = listener;
        if (listener != null) {
            listener.onProgress(startupProgressValue,
                    lblStartupStatus != null ? lblStartupStatus.getText() : "Cargando Launcher");
        }
    }

    @FXML
    public void initialize() {
        beginStartupOverlay();
        loadImages();
        updateStartupProgress(0.18, "Cargando interfaz...");
        setupCenterClip();
        setupSidebarGlass();
        setupStageResolver();
        setupResponsiveBindings();
        setupNavButtons();
        setupWindowButtonIcons();
        setupDiscordButton();
        setupNotificationUi();
        setupLoadingVisuals();
        setupSettingsView();
        setupSkinView();
        setupTeamView();
        updateStartupProgress(0.34, "Aplicando configuracion...");
        bootstrapSavedSession();
        refreshCurrentUserAvatar();

        if (loginController != null) {
            loginController.setOnSuccess(this::onLoginSuccess);
        }

        showView(ViewMode.HOME, false);
        startDiscordRpc();
        startMinecraftStatePolling();
        setupPresenceActivityTracking();
        startPresenceHeartbeat();
        startPresencePolling();
        animateEntrance();
        updateStartupProgress(0.48, "Preparando recursos...");
        applyVersionLabels();
        refreshUserPresenceUi();
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

    /**
     * FIX RAIZ del bug de los botones de la barra de titulo: el HBox de
     * contenido (sidebar + centerLayer) podia pedir mas altura minima de la
     * que quedaba disponible debajo de titleBar (confirmado con logs:
     * boundsInScene minY=-17.6 cuando titleBar ocupa 0..38.4). Al no poder
     * encogerse por debajo de ese minimo, JavaFX lo desbordaba hacia arriba
     * (StackPane centra por defecto) y ese desborde tapaba literalmente los
     * botones de minimizar/maximizar/cerrar para el picking del mouse.
     *
     * Ya bajamos los minWidth/minHeight a 0 en el FXML para que pueda
     * encogerse, pero esto ademas agrega un clip duro sobre el area de
     * <center>: pase lo que pase con el contenido en el futuro (imagenes
     * nuevas, texto largo, etc.), nada de lo que este dentro de centerRoot
     * puede volver a pintarse ni recibir clics fuera de su propia area.
     */
    private void setupCenterClip() {
        if (centerRoot == null) {
            return;
        }
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(centerRoot.widthProperty());
        clip.heightProperty().bind(centerRoot.heightProperty());
        centerRoot.setClip(clip);
    }

    private void setupSidebarGlass() {
        sidebarScroll.setPannable(false);
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

        updateMaxRestoreButton(false);

        configureWindowButtonUi(btnMinimize, this::onMinimize);
        configureWindowButtonUi(btnMaxRestore, this::onMaxRestore);
        configureWindowButtonUi(btnClose, this::onClose);

        // El arrastre/doble-clic para maximizar se cablea SOLO sobre titleBarDragArea
        // (icono + label + espaciador), que es un nodo HERMANO de los botones, no un
        // ancestro. Asi los botones nunca pueden quedar "tapados" por la logica de
        // arrastre de la barra, sin depender de detectar el target en tiempo de
        // ejecucion (que es justamente lo que estaba fallando antes).
        Node dragNode = titleBarDragArea != null ? titleBarDragArea : titleBar;
        dragNode.setPickOnBounds(true);
        dragNode.setOnMousePressed(this::onTitleBarPressed);
        dragNode.setOnMouseDragged(this::onTitleBarDragged);
        dragNode.setOnMouseClicked(this::onTitleBarClicked);
    }

    private void configureWindowButtonUi(Button button, Runnable action) {
        if (button == null) {
            return;
        }
        button.setMouseTransparent(false);
        button.setPickOnBounds(true);
        button.setFocusTraversable(false);
        // Re-cableamos la accion directamente en Java (ademas del onAction del FXML)
        // para que el boton funcione aunque la vinculacion declarada en el FXML falle
        // por cualquier motivo (por ejemplo, si el jar se empaqueta/ejecuta de forma
        // que la reflexion de FXMLLoader no encuentre el metodo).
        button.setOnAction(event -> {
            event.consume();
            if (action != null) {
                action.run();
            }
        });
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
        if (targetStage == null) {
            return;
        }
        if (customMaximized) {
            // Si arrastran la barra estando maximizado, restauramos primero
            // para que la ventana "se despegue" del borde, como en apps nativas.
            double ratioX = (event.getScreenX() - targetStage.getX()) / Math.max(1.0, targetStage.getWidth());
            restoreWindowState(targetStage);
            targetStage.setX(event.getScreenX() - restoreWidth * ratioX);
            targetStage.setY(event.getScreenY() - 12);
            windowDragOffsetX = restoreWidth * ratioX;
            windowDragOffsetY = 12;
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
            btnMaxRestore.setText("");
            btnMaxRestore.setGraphic(svgIcon(maximized ? ICON_RESTORE : ICON_MAXIMIZE));
        }
    }

    private void setupWindowButtonIcons() {
        if (btnMinimize != null) {
            btnMinimize.setText("");
            btnMinimize.setGraphic(svgIcon(ICON_MINIMIZE));
        }
        updateMaxRestoreButton(customMaximized);
        if (btnClose != null) {
            btnClose.setText("");
            btnClose.setGraphic(svgIcon(ICON_CLOSE));
        }
        if (btnNotifications != null) {
            btnNotifications.setText("");
            btnNotifications.setGraphic(svgIcon(ICON_BELL));
        }
    }

    private static SVGPath svgIcon(String content) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        path.setFillRule(FillRule.EVEN_ODD);
        path.getStyleClass().add("win-icon");
        return path;
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

    /**
     * Maximiza/restaura la ventana manualmente, sin depender de
     * Stage.setMaximized(...). Con StageStyle.UNDECORATED, el maximizado
     * nativo de JavaFX en Windows puede cubrir la barra de tareas y
     * comportarse como pantalla completa; en cambio, aca ajustamos la
     * ventana a Screen.getVisualBounds(), que sí respeta la barra de tareas.
     */
    private void toggleMaxRestore() {
        Stage targetStage = resolveStage();
        if (targetStage == null) {
            return;
        }
        if (customMaximized) {
            restoreWindowState(targetStage);
        } else {
            maximizeWindowState(targetStage);
        }
    }

    private void maximizeWindowState(Stage targetStage) {
        restoreX = targetStage.getX();
        restoreY = targetStage.getY();
        restoreWidth = targetStage.getWidth();
        restoreHeight = targetStage.getHeight();

        Rectangle2D bounds = resolveVisualBoundsFor(targetStage);
        targetStage.setX(bounds.getMinX());
        targetStage.setY(bounds.getMinY());
        targetStage.setWidth(bounds.getWidth());
        targetStage.setHeight(bounds.getHeight());

        customMaximized = true;
        updateMaxRestoreButton(true);
    }

    private void restoreWindowState(Stage targetStage) {
        targetStage.setX(restoreX);
        targetStage.setY(restoreY);
        targetStage.setWidth(restoreWidth);
        targetStage.setHeight(restoreHeight);

        customMaximized = false;
        updateMaxRestoreButton(false);
    }

    private Rectangle2D resolveVisualBoundsFor(Stage targetStage) {
        List<Screen> screens = Screen.getScreensForRectangle(
                targetStage.getX(), targetStage.getY(), targetStage.getWidth(), targetStage.getHeight()
        );
        Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
        return screen.getVisualBounds();
    }

    private void setupResponsiveBindings() {
        Platform.runLater(() -> {
            if (sidebar.getScene() == null) {
                return;
            }

            imgTitle.fitHeightProperty().bind(clamp(centerLayer.heightProperty().multiply(0.29), 140, 280));
            titleGap.prefHeightProperty().bind(clamp(centerLayer.heightProperty().multiply(0.028), 10, 24));


            DoubleBinding loadTrackWidth = clamp(loadingOverlay.widthProperty().multiply(0.37), 240, 420);
            progressTrackTotal.prefWidthProperty().bind(loadTrackWidth);
            progressTrackDownload.prefWidthProperty().bind(loadTrackWidth);
            timerRow.prefWidthProperty().bind(loadTrackWidth);

            loadingCard.maxWidthProperty().bind(clamp(loadingOverlay.widthProperty().multiply(0.50), 360, 520));
            loadingCard.setMaxHeight(Region.USE_PREF_SIZE);
            imgLoadTitle.fitHeightProperty().bind(clamp(loadingOverlay.heightProperty().multiply(0.11), 64, 96));
            if (startupProgressTrack != null && startupOverlay != null) {
                startupProgressTrack.prefWidthProperty().bind(clamp(startupOverlay.widthProperty().multiply(0.26), 190, 310));
                startupProgressTrack.widthProperty().addListener((obs, oldV, newV) -> updateStartupProgress(startupProgressValue, null));
            }

            if (settingsRoot != null) {
                settingsRoot.maxWidthProperty().bind(clamp(centerLayer.widthProperty().subtract(24), 760, 1180));
            }
            if (teamRoot != null) {
                teamRoot.maxWidthProperty().bind(clamp(centerLayer.widthProperty().subtract(24), 760, 1360));
            }
        });
    }

    private void setupLoadingVisuals() {
        clipToBounds(progressTrackTotal);
        clipToBounds(progressTrackDownload);

        progressFillTotal.setManaged(true);
        progressFillDownload.setManaged(true);
        progressFillTotal.setVisible(true);
        progressFillDownload.setVisible(true);
        progressFillTotal.setMinHeight(6);
        progressFillTotal.setPrefHeight(6);
        progressFillTotal.setMaxHeight(6);
        progressFillDownload.setMinHeight(6);
        progressFillDownload.setPrefHeight(6);
        progressFillDownload.setMaxHeight(6);
        setRegionWidth(progressFillTotal, 0.0);
        setRegionWidth(progressFillDownload, 0.0);

        progressIndTotal.setMouseTransparent(true);
        progressIndDownload.setMouseTransparent(true);
        progressIndTotal.setManaged(true);
        progressIndDownload.setManaged(true);
        progressIndTotal.setVisible(true);
        progressIndDownload.setVisible(true);
        progressIndTotal.setMinSize(74, 6);
        progressIndTotal.setPrefSize(74, 6);
        progressIndTotal.setMaxSize(74, 6);
        progressIndDownload.setMinSize(74, 6);
        progressIndDownload.setPrefSize(74, 6);
        progressIndDownload.setMaxSize(74, 6);
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
        if (startupProgressListener != null) {
            startupProgressListener.onProgress(clamped,
                    lblStartupStatus != null ? lblStartupStatus.getText() : status);
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
        int waitMs = (int) Math.max(0, 900 - elapsed) + 400;
        delay(waitMs, () -> {
            startupOverlay.setVisible(false);
            startupOverlay.setManaged(false);
            startupOverlay.setMouseTransparent(true);
            if (startupProgressListener != null) {
                startupProgressListener.onReady();
            }
            delay(700, () -> announceCatalogChanges(false));
            delay(1600, this::checkForLauncherUpdateOnStartup);
        });
    }

    private void onLoginSuccess(String username) {
        loggedIn = true;
        lblUsername.setText(username);
        refreshCurrentUserAvatar();
        refreshUserPresenceUi();
        if (loginController == null || loginController.isRememberMe()) {
            LauncherStorage.saveCredentials(username);
        } else {
            LauncherStorage.forgetCredentials();
        }
        refreshLauncherRuntimeStateUi();
        refreshTeamContextAsync();
        refreshSkinContextAsync();
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
        refreshCurrentUserAvatar();
        if (login != null) {
            login.setVisible(false);
            login.setManaged(false);
            login.setMouseTransparent(true);
            login.setOpacity(0.0);
        }
        applyLoginBackdropFx(false);
        refreshLauncherRuntimeStateUi();
        refreshTeamContextAsync();
        refreshSkinContextAsync();
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

    private void startNotificationBadgePolling() {
        if (notificationBadgePoller != null) {
            return;
        }
        notificationBadgePoller = new Timeline(
                new KeyFrame(Duration.seconds(NOTIFICATION_BADGE_POLL_SECONDS), event -> refreshNotificationBadgeAsync())
        );
        notificationBadgePoller.setCycleCount(Animation.INDEFINITE);
        notificationBadgePoller.play();
    }

    private void stopNotificationBadgePolling() {
        if (notificationBadgePoller != null) {
            notificationBadgePoller.stop();
            notificationBadgePoller = null;
        }
        notificationBadgeRefreshInFlight = false;
    }

    private void setupPresenceActivityTracking() {
        if (presenceActivityTrackingBound || titleBar == null) {
            return;
        }
        titleBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                bindPresenceActivityListeners(newScene);
            }
        });
        if (titleBar.getScene() != null) {
            bindPresenceActivityListeners(titleBar.getScene());
        }
        presenceActivityTrackingBound = true;
    }

    private void bindPresenceActivityListeners(Scene scene) {
        scene.addEventFilter(MouseEvent.ANY, event -> lastUserActivityMs = System.currentTimeMillis());
        scene.addEventFilter(KeyEvent.ANY, event -> lastUserActivityMs = System.currentTimeMillis());
    }

    private void startPresenceHeartbeat() {
        if (presenceHeartbeatTimeline != null) {
            return;
        }
        presenceHeartbeatTimeline = new Timeline(
                new KeyFrame(Duration.seconds(PRESENCE_HEARTBEAT_SECONDS), event -> sendPresenceHeartbeatAsync())
        );
        presenceHeartbeatTimeline.setCycleCount(Animation.INDEFINITE);
        presenceHeartbeatTimeline.play();
        sendPresenceHeartbeatAsync();
    }

    private void stopPresenceHeartbeat() {
        if (presenceHeartbeatTimeline != null) {
            presenceHeartbeatTimeline.stop();
            presenceHeartbeatTimeline = null;
        }
    }

    private void sendPresenceHeartbeatAsync() {
        if (!loggedIn || activeMinecraftUsername().isBlank()) {
            return;
        }
        String username = activeMinecraftUsername();
        String status;
        if (minecraftRunning) {
            // Si esta jugando, no puede estar "ausente": jugando tiene prioridad.
            status = "playing";
        } else if ((System.currentTimeMillis() - lastUserActivityMs) > PRESENCE_IDLE_THRESHOLD_MS) {
            status = "idle";
        } else {
            status = "online";
        }
        CompletableFuture.runAsync(() -> teamsService.sendHeartbeat(username, status));
    }

    private void startPresencePolling() {
        if (presencePollTimeline != null) {
            return;
        }
        presencePollTimeline = new Timeline(
                new KeyFrame(Duration.seconds(PRESENCE_POLL_SECONDS), event -> refreshPresenceAsync())
        );
        presencePollTimeline.setCycleCount(Animation.INDEFINITE);
        presencePollTimeline.play();
    }

    private void stopPresencePolling() {
        if (presencePollTimeline != null) {
            presencePollTimeline.stop();
            presencePollTimeline = null;
        }
    }

    private void refreshPresenceAsync() {
        refreshPresenceFor(presenceDotsByUsername);
    }

    /**
     * Consulta el estado de presencia de los usuarios registrados en
     * dotRegistry y actualiza sus puntitos. Distintas vistas (panel de
     * equipo, dialogo de invitar) usan su propio registro de Circles para no
     * pisarse entre si, pero comparten el cache de estados presenceStatusByUsername.
     */
    private void refreshPresenceFor(Map<String, Circle> dotRegistry) {
        if (!loggedIn || dotRegistry.isEmpty()) {
            return;
        }
        List<String> usernames = new ArrayList<>(dotRegistry.keySet());
        CompletableFuture
                .supplyAsync(() -> teamsService.fetchPresence(usernames))
                .thenAccept(result -> {
                    if (!result.success()) {
                        return;
                    }
                    Platform.runLater(() -> {
                        presenceStatusByUsername.putAll(result.statusByNormalizedUsername());
                        for (Map.Entry<String, String> entry : result.statusByNormalizedUsername().entrySet()) {
                            Circle dot = dotRegistry.get(entry.getKey());
                            applyPresenceStatus(dot, entry.getValue());
                        }
                    });
                });
    }

    private String presenceKey(String minecraftUsername) {
        return minecraftUsername == null ? "" : minecraftUsername.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Envuelve un avatar de cabeza de Minecraft con un indicador de presencia
     * (online/AFK/offline) en la esquina inferior derecha, al estilo Discord.
     */
    private StackPane buildAvatarWithPresence(String minecraftUsername, String teamColorHex, double size) {
        return buildAvatarWithPresence(minecraftUsername, teamColorHex, size, presenceDotsByUsername);
    }

    private StackPane buildAvatarWithPresence(
            String minecraftUsername, String teamColorHex, double size, Map<String, Circle> dotRegistry
    ) {
        StackPane head = buildMinecraftHeadNode(minecraftUsername, teamColorHex, size);
        StackPane wrapper = new StackPane(head);
        wrapper.setPrefSize(size, size);
        wrapper.setMinSize(size, size);
        wrapper.setMaxSize(size, size);

        Circle dot = new Circle(Math.max(5.0, size * 0.16));
        dot.getStyleClass().add("presence-dot");
        StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);
        dot.setTranslateX(-1);
        dot.setTranslateY(-1);

        String key = presenceKey(minecraftUsername);
        if (!key.isBlank()) {
            dotRegistry.put(key, dot);
            applyPresenceStatus(dot, presenceStatusByUsername.getOrDefault(key, "offline"));
        } else {
            applyPresenceStatus(dot, "offline");
        }

        wrapper.getChildren().add(dot);
        return wrapper;
    }

    private void applyPresenceStatus(Circle dot, String status) {
        if (dot == null) {
            return;
        }
        dot.getStyleClass().removeAll(
                "presence-dot-online", "presence-dot-idle", "presence-dot-offline", "presence-dot-playing"
        );
        String resolved = status == null ? "offline" : status;
        dot.getStyleClass().add(switch (resolved) {
            case "online" -> "presence-dot-online";
            case "idle" -> "presence-dot-idle";
            case "playing" -> "presence-dot-playing";
            default -> "presence-dot-offline";
        });
        Tooltip.install(dot, new Tooltip(switch (resolved) {
            case "online" -> "En linea";
            case "idle" -> "Ausente (AFK)";
            case "playing" -> "Jugando";
            default -> "Desconectado";
        }));
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
            lastProcessScanMs = 0L;
        }

        // Recorrer todos los procesos de Windows es caro: como mucho una vez cada 8 s.
        long now = System.currentTimeMillis();
        if (now - lastProcessScanMs < PROCESS_SCAN_INTERVAL_MS) {
            return lastProcessScanResult;
        }
        lastProcessScanMs = now;
        String gameDirLower = normalizedMinecraftDirLower();
        try {
            lastProcessScanResult = ProcessHandle.allProcesses()
                    .filter(ProcessHandle::isAlive)
                    .anyMatch(process -> isLikelyMinecraftProcess(process, gameDirLower));
        } catch (Exception ignored) {
            lastProcessScanResult = false;
        }
        return lastProcessScanResult;
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
        applyLauncherRuntimeState();
        refreshUserPresenceUi();
    }

    private void applyLauncherRuntimeState() {
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
        if (btnPlay != null) {
            btnPlay.setDisable(blocked);
            btnPlay.setCursor(blocked ? Cursor.DEFAULT : Cursor.HAND);
            btnPlay.setText(minecraftRunning ? "EN JUEGO" : (launchInProgress ? "INICIANDO..." : "JUGAR"));
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
        stopNotificationBadgePolling();
        stopPresenceHeartbeat();
        stopPresencePolling();
        discordRpc.shutdown();
    }

    private void loadImages() {
        // El logo grande solo va en Inicio; en los lugares chicos va el logo textual.
        Image logo = loadAsset("title.png");
        if (logo != null) {
            imgTitle.setImage(logo);
        }
        Image wordmark = loadAsset("logo-semencraft.png");
        if (wordmark != null) {
            imgLoadTitle.setImage(wordmark);
        }
        Image railLogo = loadAsset("icon.png");
        if (railLogo != null) {
            imgLogo.setImage(railLogo);
        }

        Image launcherIcon = loadAssetAny(
                "circular-black.bmp",
                "circular-black.png",
                "icon.png"
        );
        if (launcherIcon != null && imgTitleBarIcon != null) {
            imgTitleBarIcon.setImage(launcherIcon);
        }

        Image bookImage = loadAsset("book.png");
        if (bookImage != null && imgNotificationsBook != null) {
            imgNotificationsBook.setImage(bookImage);
        }

        teamNavIcon = loadAsset("team.png");
        bellIcon = loadAsset("bell.png");
        cameraIcon = loadAsset("camera.png");
        crownIcon = loadAsset("crown.png");
        editIcon = loadAsset("edit.png");
        steveSkinAsset = loadAsset("steve_skin.png");
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
            if (reducedMotion) {
                return;
            }
            exit.stop();
            enter.playFromStart();
        });
        node.setOnMouseExited(event -> {
            if (reducedMotion) {
                return;
            }
            enter.stop();
            exit.playFromStart();
        });
    }

    private void setupNavButtons() {
        applyNavIcon(btnNavHome, ICON_NAV_HOME, "Inicio");
        applyNavIcon(btnNavSkin, ICON_NAV_SKIN, "Skin");
        applyNavIcon(btnNavTeam, ICON_NAV_TEAM, "Tu equipo");
        applyNavIcon(btnNavSettings, ICON_NAV_SETTINGS, "Configuración");
        applyButtonIcon(btnPlay, ICON_PLAY, "btn-icon");
        applyButtonIcon(btnConfigure, ICON_TUNE, "btn-icon");
        applyButtonIcon(btnModsList, ICON_LIST, "btn-icon");
        if (userBlock != null) {
            userBlock.setCursor(Cursor.HAND);
            userBlock.setOnMouseClicked(event -> onSkin());
            Tooltip.install(userBlock, sideTooltip("Ver mi skin"));
        }

        for (Button button : new Button[]{btnNavHome, btnNavSkin, btnNavTeam, btnNavSettings}) {
            attachSmoothHover(button, 1.06, 0);
        }
    }

    private void applyNavIcon(Button btn, String svgContent, String label) {
        if (btn == null) {
            return;
        }
        SVGPath icon = svgIcon(svgContent);
        icon.getStyleClass().setAll("nav-icon");
        btn.setGraphic(icon);
        btn.setText(label);
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btn.setTooltip(sideTooltip(label));
    }

    private static void applyButtonIcon(Button btn, String svgContent, String styleClass) {
        if (btn == null) {
            return;
        }
        SVGPath icon = svgIcon(svgContent);
        icon.getStyleClass().setAll(styleClass);
        btn.setGraphic(icon);
        btn.setGraphicTextGap(8);
    }

    private static Tooltip sideTooltip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(Duration.millis(250));
        tip.setHideDelay(Duration.millis(60));
        return tip;
    }

    private void setupDiscordButton() {
        Image img = loadAsset("discord.png");
        if (img != null) {
            btnDiscord.setGraphic(makeIcon(img, 14));
            btnDiscord.setGraphicTextGap(8);
        }
        btnDiscord.setText("DISCORD");
    }

    private void setupNotificationUi() {
        if (btnNotifications != null) {
            attachSmoothHover(btnNotifications, 1.04, -1);
        }
        if (notificationBadge != null) {
            notificationBadge.setVisible(false);
            notificationBadge.setManaged(false);
        }
        if (notificationsDropdown != null) {
            notificationsDropdown.setVisible(false);
            notificationsDropdown.setManaged(false);
            notificationsDropdown.setOpacity(0.0);
        }
        if (notificationsScroll != null) {
            notificationsScroll.setFitToWidth(true);
            notificationsScroll.setPannable(false);
        }
        startNotificationBadgePolling();
        refreshNotificationBadgeAsync();
        Platform.runLater(this::bringNotificationUiToFront);
    }

    private void setupSkinView() {
        if (skinScroll != null) {
            skinScroll.setFitToWidth(true);
            skinScroll.setPannable(false);
            skinScroll.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> fitSkinModelToViewport());
        }
        if (skinView != null) {
            skinView.setVisible(false);
            skinView.setManaged(false);
        }
        renderSkinView();
    }

    private void setupTeamView() {
        if (teamScroll != null) {
            teamScroll.setFitToWidth(true);
            teamScroll.setPannable(false);
        }
        if (teamView != null) {
            teamView.setVisible(false);
            teamView.setManaged(false);
        }
        renderTeamView();
        renderNotifications();
    }

    private void setupSettingsView() {
        LauncherStorage.ensureEnvironment();
        modEntryIcon = loadAsset("icon.png");
        resourcepackEntryIcon = loadAsset("circular-blue.png");

        if (settingsScroll != null) {
            settingsScroll.setPannable(false);
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
        if (modsSectionFilterChips != null) {
            modsSectionFilterChips.setHgap(8);
            modsSectionFilterChips.setVgap(8);
        }
        installComboPopupAnimation(cmbModpack);
        installComboPopupAnimation(cmbLaunchMode);

        persistedConfig = LauncherStorage.loadConfig();
        applyConfigToControls(persistedConfig);
        registerSettingsDirtyListeners();
        setupRamSliderFill();
        loadModpackCatalogAsync();
        refreshJavaRuntimeStatusAsync();
        applySettingsSection(SettingsSection.GENERAL, false);
        refreshSaveState();
        if (lblSettingsStatus != null) {
            lblSettingsStatus.setOpacity(0.0);
            lblSettingsStatus.setText("");
        }
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
            if (reducedMotion) {
                return;
            }
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
            if (reducedMotion) {
                return;
            }
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
        if (chkNotificationSound != null) {
            chkNotificationSound.selectedProperty().addListener((obs, oldV, newV) -> onSettingsEdited());
        }
        if (chkPerformanceMode != null) {
            chkPerformanceMode.selectedProperty().addListener((obs, oldV, newV) -> onSettingsEdited());
        }
        if (txtModSearch != null) {
            txtModSearch.textProperty().addListener((obs, oldV, newV) -> {
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
        if (chkNotificationSound != null) {
            chkNotificationSound.setSelected(config.notificationSound());
        }
        if (chkPerformanceMode != null) {
            chkPerformanceMode.setSelected(config.performanceMode());
        }
        applyPerformanceMode(config.performanceMode());
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
        boolean notificationSound = chkNotificationSound == null || chkNotificationSound.isSelected();
        boolean optimized = chkPerformanceMode != null && chkPerformanceMode.isSelected();
        return new LauncherStorage.LauncherConfig(modpack, ram, mode, resolution, borderless, closeLauncher,
                discordRpc, notificationSound, optimized);
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
        if (!normalizeResourcepackSelection(workingResourcepackSelection)
                .equals(normalizeResourcepackSelection(persistedResourcepackSelection))) {
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
        modpackManager.setSyncProgressListener((status, fraction) -> Platform.runLater(() -> {
            if (!startupCatalogReady) {
                updateStartupProgress(0.56 + 0.20 * fraction, status);
            }
        }));
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
                    initializeResourcepackSelectionState();
                    refreshModEntriesView();
                    refreshResourcepackEntriesView();
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
        if (modsSectionFilterChips == null) {
            return;
        }
        List<String> options = new ArrayList<>();
        if (modpackCatalog != null) {
            for (String section : modpackCatalog.sections()) {
                if (!ModpackManager.SECTION_RESOURCEPACKS.equalsIgnoreCase(section)) {
                    options.add(section);
                }
            }
        }
        activeModSections.retainAll(options);

        modsSectionFilterChips.getChildren().clear();
        modsSectionFilterChips.getChildren().add(buildModSectionFilterChip(
                FILTER_SECTION_ALL_LABEL,
                activeModSections.isEmpty(),
                () -> {
                    activeModSections.clear();
                    modsPageIndex = 0;
                    rebuildSectionFilterOptions();
                    refreshModEntriesView();
                }
        ));

        for (String section : options) {
            boolean selected = activeModSections.contains(section);
            modsSectionFilterChips.getChildren().add(buildModSectionFilterChip(
                    section,
                    selected,
                    () -> {
                        if (selected) {
                            activeModSections.remove(section);
                        } else {
                            activeModSections.add(section);
                        }
                        modsPageIndex = 0;
                        rebuildSectionFilterOptions();
                        refreshModEntriesView();
                    }
            ));
        }
    }

    private ToggleButton buildModSectionFilterChip(String label, boolean selected, Runnable onToggle) {
        ToggleButton button = new ToggleButton(label);
        button.getStyleClass().add("mod-filter-chip");
        button.setSelected(selected);
        button.setFocusTraversable(false);
        button.setOnAction(event -> onToggle.run());
        return button;
    }

    private void initializeResourcepackSelectionState() {
        resourcepackEntries.clear();
        if (modpackCatalog == null) {
            persistedResourcepackSelection = List.of();
            workingResourcepackSelection = List.of();
            return;
        }
        resourcepackEntries.addAll(modpackManager.resourcepackEntries(modpackCatalog));
        List<String> initial = normalizeResourcepackSelection(
                modpackManager.loadAppliedResourcepackSelection(modpackCatalog, LauncherStorage.minecraftDirectory())
        );
        persistedResourcepackSelection = List.copyOf(initial);
        workingResourcepackSelection = new ArrayList<>(initial);
    }

    private List<String> normalizeResourcepackSelection(List<String> ids) {
        if (modpackCatalog == null || ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            ModpackManager.OptionalEntry entry = modpackCatalog.findOptionalById(id);
            if (entry != null && entry.type() == ModpackManager.EntryType.RESOURCEPACK) {
                normalized.add(id);
            }
        }
        return List.copyOf(normalized);
    }

    private void refreshResourcepackEntriesView() {
        if (resourcepacksAvailableContainer == null || resourcepacksSelectedContainer == null) {
            return;
        }
        resourcepacksAvailableContainer.getChildren().clear();
        resourcepacksSelectedContainer.getChildren().clear();

        if (modpackCatalog == null) {
            setResourcepackListMessage(resourcepacksAvailableContainer, "Cargando resourcepacks...");
            setResourcepackListMessage(resourcepacksSelectedContainer, "Aun no hay packs seleccionados.");
            return;
        }

        List<String> selectedIds = normalizeResourcepackSelection(workingResourcepackSelection);
        workingResourcepackSelection = new ArrayList<>(selectedIds);
        LinkedHashSet<String> selectedSet = new LinkedHashSet<>(selectedIds);

        List<ModpackManager.OptionalEntry> selectedEntries = new ArrayList<>();
        for (String id : selectedIds) {
            ModpackManager.OptionalEntry entry = modpackCatalog.findOptionalById(id);
            if (entry != null && entry.type() == ModpackManager.EntryType.RESOURCEPACK) {
                selectedEntries.add(entry);
            }
        }

        List<ModpackManager.OptionalEntry> availableEntries = new ArrayList<>();
        for (ModpackManager.OptionalEntry entry : resourcepackEntries) {
            if (!selectedSet.contains(entry.id())) {
                availableEntries.add(entry);
            }
        }

        if (availableEntries.isEmpty()) {
            setResourcepackListMessage(resourcepacksAvailableContainer, "No hay packs disponibles.");
        } else {
            for (ModpackManager.OptionalEntry entry : availableEntries) {
                resourcepacksAvailableContainer.getChildren().add(buildResourcepackEntryRow(entry, false, -1, 0));
            }
        }

        if (selectedEntries.isEmpty()) {
            setResourcepackListMessage(resourcepacksSelectedContainer, "Los packs elegidos apareceran aqui.");
        } else {
            for (int i = 0; i < selectedEntries.size(); i++) {
                resourcepacksSelectedContainer.getChildren().add(
                        buildResourcepackEntryRow(selectedEntries.get(i), true, i, selectedEntries.size())
                );
            }
        }
    }

    private Node buildResourcepackEntryRow(ModpackManager.OptionalEntry entry, boolean selected, int index, int total) {
        HBox row = new HBox(12);
        row.getStyleClass().add("resourcepack-item-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Image icon = resolveEntryIcon(entry);
        ImageView iconView = makeIcon(icon, 46);
        iconView.setFitWidth(46);
        iconView.setFitHeight(46);

        Label name = new Label(entry.displayName());
        name.getStyleClass().add("resourcepack-item-name");
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setWrapText(false);

        VBox textCol = new VBox(4);
        textCol.getStyleClass().add("resourcepack-item-copy");
        textCol.getChildren().add(name);
        if (!selected) {
            Label description = new Label(
                    entry.description() == null || entry.description().isBlank()
                            ? "Sin descripcion."
                            : entry.description()
            );
            description.getStyleClass().add("resourcepack-item-description");
            description.setWrapText(true);
            textCol.getChildren().add(description);
        }
        textCol.setMinWidth(0);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        VBox actions = new VBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setFillWidth(true);
        actions.setMinWidth(selected ? 104 : 92);
        actions.setPrefWidth(selected ? 104 : 92);

        if (selected) {
            Button moveUp = new Button("Subir");
            moveUp.getStyleClass().add("resourcepack-order-btn");
            moveUp.setDisable(index <= 0);
            moveUp.setOnAction(event -> moveResourcepackSelection(index, -1));
            moveUp.setMaxWidth(Double.MAX_VALUE);
            moveUp.setMinWidth(96);

            Button moveDown = new Button("Bajar");
            moveDown.getStyleClass().add("resourcepack-order-btn");
            moveDown.setDisable(index >= total - 1);
            moveDown.setOnAction(event -> moveResourcepackSelection(index, 1));
            moveDown.setMaxWidth(Double.MAX_VALUE);
            moveDown.setMinWidth(96);

            Button remove = new Button("Quitar");
            remove.getStyleClass().addAll("resourcepack-toggle-btn", "resourcepack-remove-btn");
            remove.setOnAction(event -> removeResourcepackFromSelection(entry.id()));
            remove.setMaxWidth(Double.MAX_VALUE);
            remove.setMinWidth(96);

            actions.getChildren().addAll(moveUp, moveDown, remove);
        } else {
            Button add = new Button("Usar");
            add.getStyleClass().addAll("resourcepack-toggle-btn", "resourcepack-toggle-btn-selected");
            add.setOnAction(event -> addResourcepackToSelection(entry.id()));
            add.setMaxWidth(Double.MAX_VALUE);
            add.setMinWidth(88);
            actions.getChildren().add(add);
        }

        row.getChildren().addAll(iconView, textCol, actions);
        attachSmoothHover(row, 1.01, -1);
        return row;
    }

    private void addResourcepackToSelection(String entryId) {
        LinkedHashSet<String> next = new LinkedHashSet<>(normalizeResourcepackSelection(workingResourcepackSelection));
        next.add(entryId);
        workingResourcepackSelection = new ArrayList<>(next);
        refreshResourcepackEntriesView();
        onSettingsEdited();
    }

    private void removeResourcepackFromSelection(String entryId) {
        List<String> next = new ArrayList<>(normalizeResourcepackSelection(workingResourcepackSelection));
        next.remove(entryId);
        workingResourcepackSelection = next;
        refreshResourcepackEntriesView();
        onSettingsEdited();
    }

    private void moveResourcepackSelection(int index, int delta) {
        List<String> next = new ArrayList<>(normalizeResourcepackSelection(workingResourcepackSelection));
        int targetIndex = index + delta;
        if (index < 0 || index >= next.size() || targetIndex < 0 || targetIndex >= next.size()) {
            return;
        }
        String moved = next.remove(index);
        next.add(targetIndex, moved);
        workingResourcepackSelection = next;
        refreshResourcepackEntriesView();
        onSettingsEdited();
    }

    private static void setResourcepackListMessage(VBox container, String message) {
        if (container == null) {
            return;
        }
        container.getChildren().clear();
        Label label = new Label(message);
        label.getStyleClass().add("settings-hint");
        label.setWrapText(true);
        container.getChildren().add(label);
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
        for (ModpackManager.OptionalEntry entry : modpackCatalog.optionalEntries()) {
            if (entry.type() != ModpackManager.EntryType.MOD) {
                continue;
            }
            if (!activeModSections.isEmpty() && !activeModSections.contains(entry.section())) {
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

    private Node buildModEntryRow(ModpackManager.OptionalEntry entry) {
        HBox row = new HBox(10);
        row.getStyleClass().add("mod-item-row");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Image icon = resolveEntryIcon(entry);
        ImageView iconView = makeIcon(icon, 36);
        iconView.setFitHeight(36);
        iconView.setFitWidth(36);

        Label lblName = new Label(entry.displayName());
        lblName.getStyleClass().add("mod-item-name");
        Label lblFile = new Label(entry.fileName());
        lblFile.getStyleClass().add("mod-item-file");
        Label lblSection = new Label(entry.section());
        lblSection.getStyleClass().add("mod-item-section");

        VBox textCol = new VBox(2, lblName, lblFile, lblSection);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        CheckBox toggle = new CheckBox();
        toggle.getStyleClass().add("switch");
        toggle.setFocusTraversable(false);
        toggle.setSelected(workingModSelection != null && workingModSelection.isEnabled(entry.id()));
        toggle.selectedProperty().addListener((obs, oldV, newV) -> applyModEntryToggle(entry, newV));

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
            lblModsPage.setText("Página " + (modsPageIndex + 1) + "/" + totalPages);
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
        workingResourcepackSelection = new ArrayList<>(normalizeResourcepackSelection(persistedResourcepackSelection));
        modsPageIndex = 0;
        refreshModEntriesView();
        refreshResourcepackEntriesView();
        refreshSaveState();
    }

    private boolean saveAllSettings() {
        try {
            LauncherStorage.LauncherConfig currentConfig = captureCurrentConfig();
            ModpackManager.Selection currentSelection = captureCurrentModSelection();
            List<String> currentResourcepackSelection = normalizeResourcepackSelection(workingResourcepackSelection);
            LauncherStorage.saveConfig(currentConfig);
            persistedConfig = currentConfig;
            applyPerformanceMode(currentConfig.performanceMode());

            if (currentSelection != null) {
                persistedModSelection = currentSelection;
                workingModSelection = currentSelection;
                if (ModpackManager.TEMPLATE_CUSTOM.equals(currentSelection.selectedTemplate())) {
                    modpackManager.saveCustomSelection(modpackCatalog, currentSelection);
                }
            }
            persistedResourcepackSelection = List.copyOf(currentResourcepackSelection);
            workingResourcepackSelection = new ArrayList<>(currentResourcepackSelection);
            modpackManager.saveResourcepackSelection(currentResourcepackSelection);
            if (modpackCatalog != null) {
                modpackManager.applyResourcepackSelectionToGame(
                        modpackCatalog,
                        LauncherStorage.minecraftDirectory(),
                        currentResourcepackSelection
                );
            }
            refreshResourcepackEntriesView();
            refreshSaveState();
            return true;
        } catch (Exception ex) {
            return false;
        }
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
        setSectionVisible(paneSettingsResourcepacks, section == SettingsSection.RESOURCEPACKS);
        setSectionVisible(paneSettingsSystem, section == SettingsSection.SYSTEM);
        applySettingsTabState(btnSettingsTabGeneral, section == SettingsSection.GENERAL);
        applySettingsTabState(btnSettingsTabModpacks, section == SettingsSection.MODPACKS);
        applySettingsTabState(btnSettingsTabResourcepacks, section == SettingsSection.RESOURCEPACKS);
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
                lblSystemJavaDetails.setText(
                        "No se detecto Java " + FabricMinecraftService.REQUIRED_JAVA_MAJOR
                                + " en el sistema. Se descargara automaticamente al presionar Jugar."
                );
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
        if (reducedMotion) {
            return;
        }
        Node sideNode = sidebarGlass != null ? sidebarGlass : sidebarScroll;
        sideNode.setTranslateX(-72);
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
        if (reducedMotion || imgTitle == null) {
            return;
        }
        if (titleFloatTransition != null) {
            titleFloatTransition.stop();
        }
        imgTitle.setTranslateY(0);
        titleFloatTransition = new TranslateTransition(Duration.seconds(3.6), imgTitle);
        titleFloatTransition.setByY(-10);
        titleFloatTransition.setAutoReverse(true);
        titleFloatTransition.setCycleCount(Animation.INDEFINITE);
        titleFloatTransition.setInterpolator(Interpolator.EASE_BOTH);
        titleFloatTransition.play();
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
        if (!ensureLoggedInForAccountUi()) {
            return;
        }
        showView(ViewMode.SKIN, true);
        refreshSkinContextAsync();
    }

    @FXML
    private void onTeam() {
        if (!ensureLoggedInForAccountUi()) {
            return;
        }
        showView(ViewMode.TEAM, true);
    }

    @FXML
    private void onSettings() {
        showView(ViewMode.SETTINGS, true);
    }

    @FXML
    private void onToggleNotifications() {
        if (localNotifications.isEmpty() && !ensureLoggedInForAccountUi()) {
            return;
        }
        notificationsOpen = !notificationsOpen;
        updateNotificationsDropdownVisibility(notificationsOpen, true);
        if (notificationsOpen) {
            unreadLocalNotifications = 0;
            renderNotifications();
            updateNotificationBadge();
            String username = activeMinecraftUsername();
            if (loggedIn && !username.isBlank()) {
                CompletableFuture.runAsync(() -> teamsService.markNotificationsViewed(username))
                        .thenRun(() -> Platform.runLater(this::refreshTeamContextAsync));
            }
        }
    }

    @FXML
    private void onCloseNotifications() {
        notificationsOpen = false;
        updateNotificationsDropdownVisibility(false, false);
    }

    @FXML
    private void onSaveSettings() {
        if (saveAllSettings()) {
            showSettingsStatus("Guardado!");
        } else {
            showSettingsStatus("No se pudieron aplicar los resourcepacks.");
        }
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
    private void onSettingsTabResourcepacks() {
        applySettingsSection(SettingsSection.RESOURCEPACKS, true);
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
        openExternalUrl("https://discord.gg/tVcSPEQd8U");
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

        // Ya no bloqueamos el Play si falta Java: installAndLaunch() se encarga de
        // descargar un runtime portable (JRE 21 de Eclipse Temurin) automaticamente
        // cuando hace falta, mostrando el progreso en la pantalla de carga de abajo.

        if (!saveAllSettings()) {
            showView(ViewMode.SETTINGS, true);
            applySettingsSection(SettingsSection.RESOURCEPACKS, false);
            showSettingsStatus("No se pudieron aplicar los resourcepacks antes de iniciar.");
            return;
        }
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
        boolean showSkin = mode == ViewMode.SKIN;
        boolean showTeam = mode == ViewMode.TEAM;

        if (centerContent != null) {
            centerContent.setManaged(showHome);
            centerContent.setVisible(showHome);
        }
        Node skinNode = skinView != null ? skinView : skinScroll;
        if (skinNode != null) {
            skinNode.setManaged(showSkin);
            skinNode.setVisible(showSkin);
            if (showSkin && skinScroll != null) {
                skinScroll.setVvalue(0.0);
            }
        }
        Node settingsNode = settingsView != null ? settingsView : settingsScroll;
        if (settingsNode != null) {
            settingsNode.setManaged(mode == ViewMode.SETTINGS);
            settingsNode.setVisible(mode == ViewMode.SETTINGS);
            if (mode == ViewMode.SETTINGS) {
                if (settingsScroll != null) {
                    settingsScroll.setVvalue(0.0);
                }
            }
        }
        Node teamNode = teamView != null ? teamView : teamScroll;
        if (teamNode != null) {
            teamNode.setManaged(showTeam);
            teamNode.setVisible(showTeam);
            if (showTeam && teamScroll != null) {
                teamScroll.setVvalue(0.0);
            }
        }

        applyNavState(btnNavHome, showHome);
        applyNavState(btnNavSkin, showSkin);
        applyNavState(btnNavTeam, showTeam);
        applyNavState(btnNavSettings, mode == ViewMode.SETTINGS);

        boolean showNotificationsBell = true;
        if (notificationsAnchor != null) {
            notificationsAnchor.setVisible(showNotificationsBell);
            notificationsAnchor.setManaged(showNotificationsBell);
        }
        if (!showNotificationsBell) {
            updateNotificationsDropdownVisibility(false, false);
        }

        if (!loggedIn && login != null) {
            if (showHome || showSkin || showTeam) {
                setLoginOverlayVisible(true);
            } else {
                setLoginOverlayVisible(false);
            }
        }

        if (currentSkinModelView != null) {
            currentSkinModelView.setAnimated(showSkin && !reducedMotion);
        }

        if (animate && !reducedMotion) {
            Node target = showHome ? centerContent : (showSkin ? skinNode : (showTeam ? teamNode : settingsNode));
            if (target != null) {
                target.setOpacity(0.0);
                target.setTranslateY(10.0);
                ParallelTransition transition = parallel(
                        fade(target, 1.0, 230),
                        slideY(target, 0, 230)
                );
                transition.play();
            }
        } else if (centerContent != null && skinNode != null && settingsNode != null && teamNode != null) {
            centerContent.setOpacity(showHome ? 1.0 : 0.0);
            skinNode.setOpacity(showSkin ? 1.0 : 0.0);
            settingsNode.setOpacity(mode == ViewMode.SETTINGS ? 1.0 : 0.0);
            teamNode.setOpacity(showTeam ? 1.0 : 0.0);
            centerContent.setTranslateY(0.0);
            skinNode.setTranslateY(0.0);
            settingsNode.setTranslateY(0.0);
            teamNode.setTranslateY(0.0);
        }
        refreshLauncherRuntimeStateUi();
        bringNotificationUiToFront();
    }

    private String activeMinecraftUsername() {
        if (lblUsername == null || lblUsername.getText() == null) {
            return "";
        }
        String value = lblUsername.getText().trim();
        if (value.isBlank() || "--".equals(value) || "-".equals(value)) {
            return "";
        }
        return value;
    }

    private boolean ensureLoggedInForAccountUi() {
        if (loggedIn && !activeMinecraftUsername().isBlank()) {
            return true;
        }
        showView(ViewMode.HOME, true);
        setLoginOverlayVisible(true);
        showInfo("Inicia sesion primero para usar esta seccion.");
        return false;
    }

    private void refreshTeamContextAsync() {
        if (!loggedIn || activeMinecraftUsername().isBlank()) {
            currentTeamContext = null;
            notificationBadgeCount = 0;
            renderTeamView();
            renderNotifications();
            refreshCurrentUserAvatar();
            updateNotificationBadge();
            return;
        }

        String username = activeMinecraftUsername();
        CompletableFuture
                .supplyAsync(() -> teamsService.loadContext(username))
                .thenAccept(result -> Platform.runLater(() -> {
                    if (servicesShutdown || !username.equalsIgnoreCase(activeMinecraftUsername())) {
                        return;
                    }
                    if (result.success()) {
                        currentTeamContext = result.context();
                        notificationBadgeCount = countPendingNotifications(result.context());
                    } else {
                        currentTeamContext = null;
                        notificationBadgeCount = 0;
                    }
                    renderTeamView();
                    renderNotifications();
                    refreshCurrentUserAvatar();
                    updateNotificationBadge();
                }));
    }

    private void refreshSkinContextAsync() {
        if (servicesShutdown || skinContextRefreshInFlight) {
            return;
        }
        if (!loggedIn || activeMinecraftUsername().isBlank()) {
            currentSkinContext = null;
            pendingSkinBase64 = "";
            pendingSkinPreview = null;
            pendingSkinModel = "wide";
            pendingSkinDraftError = "";
            skinContextErrorMessage = "";
            refreshCurrentUserAvatar();
            renderSkinView();
            return;
        }

        String username = activeMinecraftUsername();
        skinContextRefreshInFlight = true;
        CompletableFuture
                .supplyAsync(() -> skinService.loadSkinContext(username))
                .thenAccept(result -> Platform.runLater(() -> {
                    skinContextRefreshInFlight = false;
                    if (servicesShutdown || !username.equalsIgnoreCase(activeMinecraftUsername())) {
                        return;
                    }
                    if (result.success() && result.context() != null) {
                        SupabaseSkinService.SkinContext resolvedContext = shouldKeepLocalSkinContext(result.context())
                                ? currentSkinContext
                                : result.context();
                        currentSkinContext = resolvedContext;
                        primeCurrentUserSkinCache();
                        pendingSkinBase64 = resolvedContext.skinBase64();
                        pendingSkinPreview = decodeImage(resolvedContext.skinBase64());
                        pendingSkinModel = SupabaseSkinService.normalizeModel(resolvedContext.modelId());
                        pendingSkinDraftError = "";
                        skinContextErrorMessage = "";
                    } else {
                        currentSkinContext = null;
                        pendingSkinBase64 = "";
                        pendingSkinPreview = null;
                        pendingSkinModel = "wide";
                        pendingSkinDraftError = "";
                        skinContextErrorMessage = result.message();
                    }
                    refreshCurrentUserAvatar();
                    renderSkinView();
                }));
    }

    private boolean shouldKeepLocalSkinContext(SupabaseSkinService.SkinContext fetchedContext) {
        if (currentSkinContext == null || fetchedContext == null) {
            return false;
        }
        if (!defaultString(currentSkinContext.normalizedMinecraftUsername())
                .equalsIgnoreCase(defaultString(fetchedContext.normalizedMinecraftUsername()))) {
            return false;
        }

        Instant localUpdatedAt = parseInstantSafely(currentSkinContext.updatedAt());
        Instant fetchedUpdatedAt = parseInstantSafely(fetchedContext.updatedAt());
        if (localUpdatedAt != null && fetchedUpdatedAt != null && localUpdatedAt.isAfter(fetchedUpdatedAt)) {
            return true;
        }

        return currentSkinContext.hasCustomSkin()
                && !fetchedContext.hasCustomSkin()
                && localUpdatedAt != null;
    }

    private Instant parseInstantSafely(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void refreshNotificationBadgeAsync() {
        if (servicesShutdown || notificationBadgeRefreshInFlight) {
            return;
        }
        if (!loggedIn || activeMinecraftUsername().isBlank()) {
            notificationBadgeCount = 0;
            updateNotificationBadge();
            return;
        }

        String username = activeMinecraftUsername();
        notificationBadgeRefreshInFlight = true;
        CompletableFuture
                .supplyAsync(() -> teamsService.loadNotificationBadge(username))
                .thenAccept(result -> Platform.runLater(() -> {
                    notificationBadgeRefreshInFlight = false;
                    if (servicesShutdown || !username.equalsIgnoreCase(activeMinecraftUsername())) {
                        return;
                    }
                    if (result.success()) {
                        notificationBadgeCount = Math.max(0, result.unreadCount());
                        announceUnreadCount(notificationBadgeCount);
                    } else if (currentTeamContext == null) {
                        notificationBadgeCount = 0;
                    }
                    updateNotificationBadge();
                }));
    }

    private int countPendingNotifications(SupabaseTeamsService.TeamContext context) {
        if (context == null) {
            return 0;
        }
        return (int) context.notifications().stream()
                .filter(item -> "pending".equalsIgnoreCase(item.status()) || !item.read())
                .count();
    }

    private void updateNotificationBadge() {
        if (notificationBadge == null || lblNotificationCount == null) {
            return;
        }
        int count = Math.max(0, notificationBadgeCount) + Math.max(0, unreadLocalNotifications);
        boolean show = count > 0;
        notificationBadge.setManaged(show);
        notificationBadge.setVisible(show);
        String text = count > 9 ? "9+" : String.valueOf(count);
        lblNotificationCount.setText(text);
        if (lblNotificationsHeaderCount != null) {
            lblNotificationsHeaderCount.setVisible(show);
            lblNotificationsHeaderCount.setManaged(show);
            lblNotificationsHeaderCount.setText(text);
        }
    }

    private void updateNotificationsDropdownVisibility(boolean visible, boolean animate) {
        if (notificationsDropdown == null) {
            return;
        }
        if (btnNotifications != null) {
            btnNotifications.getStyleClass().remove("notification-bell-btn-open");
            if (visible) {
                btnNotifications.getStyleClass().add("notification-bell-btn-open");
            }
        }
        if (visible) {
            bringNotificationUiToFront();
        }
        notificationsDropdown.setManaged(visible);
        notificationsDropdown.setVisible(visible);
        if (!visible) {
            notificationsOpen = false;
            notificationsDropdown.setOpacity(0.0);
            return;
        }
        if (animate) {
            notificationsDropdown.setOpacity(0.0);
            notificationsDropdown.setTranslateY(-8.0);
            ParallelTransition transition = parallel(
                    fade(notificationsDropdown, 1.0, 180),
                    slideY(notificationsDropdown, 0.0, 180)
            );
            transition.play();
        } else {
            notificationsDropdown.setOpacity(1.0);
            notificationsDropdown.setTranslateY(0.0);
        }
    }

    private void bringNotificationUiToFront() {
        if (notificationsAnchor != null) {
            notificationsAnchor.toFront();
        }
        if (notificationsDropdown != null) {
            notificationsDropdown.toFront();
        }
    }

    private void renderSkinView() {
        if (skinRoot == null) {
            return;
        }
        skinRoot.getChildren().clear();

        if (!loggedIn || activeMinecraftUsername().isBlank()) {
            skinRoot.getChildren().add(createInfoCard(
                    "Skin Manager",
                    "Inicia sesion para subir tu skin personalizada y verla dentro de Minecraft."
            ));
            return;
        }

        if (currentSkinContext == null) {
            VBox info = createInfoCard(
                    skinContextErrorMessage.isBlank() ? "Cargando skin" : "No se pudo cargar tu skin",
                    skinContextErrorMessage.isBlank()
                            ? "Estamos consultando tu skin personalizada en Supabase."
                            : skinContextErrorMessage
            );
            if (!skinContextErrorMessage.isBlank()) {
                Button retryButton = new Button("Reintentar");
                retryButton.getStyleClass().add("team-action-primary");
                retryButton.setOnAction(event -> refreshSkinContextAsync());
                info.getChildren().add(retryButton);
            }
            skinRoot.getChildren().add(info);
            return;
        }

        Image skinPreview = pendingSkinPreview != null ? pendingSkinPreview : decodeImage(currentSkinContext.skinBase64());
        boolean dirty = hasPendingSkinChanges();

        Label title = createHeadingLabel("Tu skin");
        Label state = new Label(dirty ? "Cambios sin guardar"
                : (currentSkinContext.hasCustomSkin() ? "Guardada" : "Sin skin propia"));
        state.getStyleClass().addAll("skin-state-badge", dirty ? "skin-state-dirty" : "skin-state-saved");
        HBox header = new HBox(12, title, state);
        header.setAlignment(Pos.CENTER_LEFT);

        // Columna izquierda: modelo y tipo de brazos
        ToggleGroup modelGroup = new ToggleGroup();
        ToggleButton wideButton = buildSkinModelButton("Clásica", "wide", modelGroup);
        ToggleButton slimButton = buildSkinModelButton("Delgada", "slim", modelGroup);
        wideButton.getStyleClass().add("segmented-left");
        slimButton.getStyleClass().add("segmented-right");
        HBox modelRow = new HBox(0, wideButton, slimButton);
        modelRow.setAlignment(Pos.CENTER);
        modelRow.getStyleClass().add("skin-segmented");

        VBox viewerPanel = new VBox(14, buildSkinViewer(skinPreview), modelRow);
        viewerPanel.setAlignment(Pos.TOP_CENTER);
        viewerPanel.getStyleClass().add("skin-viewer-panel");
        if (currentSkinModelView != null) {
            Label dragHint = new Label("Arrastra para girar");
            dragHint.getStyleClass().add("skin-body-caption");
            viewerPanel.getChildren().add(dragHint);
        }

        // Columna derecha: archivo y guardar
        Label fileTitle = new Label("Archivo de skin");
        fileTitle.getStyleClass().add("skin-section-title");
        Node sheet = buildSkinSheetThumbnail(skinPreview);
        Button changeButton = new Button(skinPreview == null ? "Subir skin" : "Cambiar skin");
        changeButton.getStyleClass().add("secondary-btn");
        changeButton.setOnAction(event -> chooseSkinFile());
        Label fileHint = new Label("PNG de 64x64 o 64x32.");
        fileHint.getStyleClass().add("settings-hint");
        HBox fileRow = new HBox(16, sheet, new VBox(8, changeButton, fileHint));
        fileRow.setAlignment(Pos.CENTER_LEFT);

        Label validationLabel = new Label(pendingSkinDraftError == null ? "" : pendingSkinDraftError);
        validationLabel.getStyleClass().add("team-validation-error");
        validationLabel.setWrapText(true);

        Button saveButton = new Button("Guardar skin");
        saveButton.getStyleClass().add("team-action-primary");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setDisable(!dirty);
        saveButton.setOnAction(event -> submitSkinDraftAsync(saveButton, validationLabel));

        Region sideSpacer = new Region();
        VBox.setVgrow(sideSpacer, Priority.ALWAYS);
        VBox sidePanel = new VBox(14, fileTitle, fileRow, sideSpacer, validationLabel, saveButton);
        sidePanel.getStyleClass().add("skin-side-panel");
        HBox.setHgrow(sidePanel, Priority.ALWAYS);

        HBox content = new HBox(18, viewerPanel, sidePanel);
        content.setAlignment(Pos.TOP_LEFT);

        VBox card = createSectionCard();
        card.getStyleClass().add("skin-card");
        card.setMaxWidth(820);
        card.getChildren().addAll(header, content);
        skinRoot.getChildren().add(card);
    }

    private ToggleButton buildSkinModelButton(String label, String modelId, ToggleGroup toggleGroup) {
        ToggleButton button = new ToggleButton(label);
        button.getStyleClass().add("mod-toggle-btn");
        button.setToggleGroup(toggleGroup);
        String normalizedModel = SupabaseSkinService.normalizeModel(modelId);
        button.setSelected(normalizedModel.equals(SupabaseSkinService.normalizeModel(pendingSkinModel)));
        button.setOnAction(event -> {
            pendingSkinModel = normalizedModel;
            renderSkinView();
        });
        return button;
    }

    private void chooseSkinFile() {
        Stage owner = stage != null ? stage : (titleBar != null && titleBar.getScene() != null && titleBar.getScene().getWindow() instanceof Stage s ? s : null);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecciona tu skin");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Skins PNG", "*.png"));
        File selected = chooser.showOpenDialog(owner);
        if (selected == null) {
            return;
        }

        try {
            BufferedImage image = SupabaseSkinService.readSkinFile(selected.toPath());
            pendingSkinPreview = bufferedImageToFxImage(image);
            pendingSkinBase64 = encodeBufferedImageToBase64(image);
            if (SupabaseSkinService.isClassicLegacySkin(image)) {
                pendingSkinModel = "wide";
            }
            pendingSkinDraftError = "";
            renderSkinView();
        } catch (Exception ex) {
            pendingSkinDraftError = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "No se pudo usar esa skin."
                    : ex.getMessage();
            renderSkinView();
            showInfo(pendingSkinDraftError);
        }
    }

    private void submitSkinDraftAsync(Button saveButton, Label validationLabel) {
        String validation = validateSkinDraft();
        if (!validation.isBlank()) {
            pendingSkinDraftError = validation;
            if (validationLabel != null) {
                validationLabel.setText(validation);
            }
            shakeNode(saveButton);
            return;
        }

        pendingSkinDraftError = "";
        if (validationLabel != null) {
            validationLabel.setText("");
        }
        saveButton.setDisable(true);

        String username = activeMinecraftUsername();
        String skinBase64 = pendingSkinBase64;
        String modelId = pendingSkinModel;
        CompletableFuture
                .supplyAsync(() -> skinService.saveSkin(username, modelId, skinBase64))
                .thenAccept(result -> Platform.runLater(() -> {
                    saveButton.setDisable(false);
                    if (servicesShutdown || !username.equalsIgnoreCase(activeMinecraftUsername())) {
                        return;
                    }
                    if (!result.success() || result.context() == null) {
                        pendingSkinDraftError = result.message();
                        if (validationLabel != null) {
                            validationLabel.setText(pendingSkinDraftError);
                        }
                        shakeNode(saveButton);
                        return;
                    }

                    currentSkinContext = result.context();
                    primeCurrentUserSkinCache();
                    pendingSkinBase64 = result.context().skinBase64();
                    pendingSkinPreview = decodeImage(result.context().skinBase64());
                    pendingSkinModel = SupabaseSkinService.normalizeModel(result.context().modelId());
                    pendingSkinDraftError = "";
                    skinContextErrorMessage = "";
                    refreshCurrentUserAvatar();
                    renderTeamView();
                    renderNotifications();
                    renderSkinView();
                    showInfo(result.message());
                }));
    }

    private String validateSkinDraft() {
        if (!loggedIn || activeMinecraftUsername().isBlank()) {
            return "Inicia sesion para guardar tu skin.";
        }
        if (pendingSkinBase64 == null || pendingSkinBase64.isBlank()) {
            return "Primero debes subir una skin PNG de 64x64 o 64x32.";
        }
        try {
            SupabaseSkinService.decodeSkinBase64(pendingSkinBase64);
        } catch (Exception ex) {
            return ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "La skin seleccionada no es valida."
                    : ex.getMessage();
        }
        return "";
    }

    private boolean hasPendingSkinChanges() {
        if (currentSkinContext == null) {
            return pendingSkinPreview != null && pendingSkinBase64 != null && !pendingSkinBase64.isBlank();
        }
        return !defaultString(pendingSkinBase64).equals(defaultString(currentSkinContext.skinBase64()))
                || !SupabaseSkinService.normalizeModel(pendingSkinModel)
                .equals(SupabaseSkinService.normalizeModel(currentSkinContext.modelId()));
    }

    /** Modelo 3D si la PC lo soporta; si no, las vistas 2D de frente y espalda. */
    private Node buildSkinViewer(Image skinPreview) {
        if (currentSkinModelView != null) {
            currentSkinModelView.setAnimated(false);
            currentSkinModelView = null;
        }
        if (skinPreview != null && SkinModelView.isSupported()) {
            boolean slim = "slim".equalsIgnoreCase(SupabaseSkinService.normalizeModel(pendingSkinModel));
            currentSkinModelView = new SkinModelView(skinPreview, slim, 300, 360, reducedMotion);
            currentSkinModelView.setAnimated(viewMode == ViewMode.SKIN && !reducedMotion);
            fitSkinModelToViewport();
            return currentSkinModelView;
        }
        HBox figures = new HBox(14);
        figures.getStyleClass().add("skin-body-row");
        figures.setAlignment(Pos.CENTER);
        figures.getChildren().addAll(
                buildSkinBodyFigure(skinPreview, true, "Frente"),
                buildSkinBodyFigure(skinPreview, false, "Espalda")
        );
        return figures;
    }

    /** El modelo se achica en ventanas bajas para que la tarjeta entre sin scroll. */
    private void fitSkinModelToViewport() {
        if (currentSkinModelView == null || skinScroll == null) {
            return;
        }
        double viewportHeight = skinScroll.getViewportBounds().getHeight();
        if (viewportHeight <= 0) {
            return;
        }
        double height = Math.max(240, Math.min(360, viewportHeight - SKIN_CARD_CHROME_HEIGHT));
        currentSkinModelView.setViewerHeight(Math.floor(height));
    }

    private Node buildSkinBodyFigure(Image skinPreview, boolean frontView, String captionText) {
        VBox figure = new VBox(6);
        figure.getStyleClass().add("skin-body-figure");
        figure.setAlignment(Pos.TOP_CENTER);

        Region previewRegion;
        if (skinPreview != null) {
            Image bodyPreview = createSkinBodyPreview(skinPreview, frontView, 210);
            ImageView bodyView = new ImageView(bodyPreview);
            bodyView.setPreserveRatio(true);
            bodyView.setSmooth(false);
            bodyView.setCache(false);
            bodyView.getStyleClass().add("skin-body-preview");

            StackPane frame = new StackPane(bodyView);
            frame.getStyleClass().add("skin-body-frame");
            frame.setPrefSize(150, 250);
            previewRegion = frame;
        } else {
            Label empty = new Label("Sin skin");
            empty.getStyleClass().add("skin-empty-preview");
            StackPane placeholder = new StackPane(empty);
            placeholder.getStyleClass().add("skin-body-frame");
            placeholder.setPrefSize(150, 250);
            previewRegion = placeholder;
        }

        Label caption = new Label(captionText);
        caption.getStyleClass().add("skin-body-caption");
        figure.getChildren().addAll(previewRegion, caption);
        return figure;
    }

    /**
     * La textura plana, nitida: cada pixel de la skin ocupa un numero entero de pixeles
     * reales de pantalla (tambien con escalado de Windows al 125 % o 150 %).
     */
    private Node buildSkinSheetThumbnail(Image skinPreview) {
        StackPane frame = new StackPane();
        frame.getStyleClass().add("skin-sheet-frame");
        frame.setPrefSize(156, 156);
        frame.setMinSize(156, 156);
        frame.setMaxSize(156, 156);
        if (skinPreview == null || skinPreview.getWidth() <= 0) {
            Label empty = new Label("Sin skin");
            empty.getStyleClass().add("skin-empty-preview");
            frame.getChildren().add(empty);
            return frame;
        }

        double outputScale = skinRoot != null && skinRoot.getScene() != null && skinRoot.getScene().getWindow() != null
                ? skinRoot.getScene().getWindow().getOutputScaleX()
                : javafx.stage.Screen.getPrimary().getOutputScaleX();
        int screenPixelsPerSkinPixel = Math.max(1, (int) Math.round(2.0 * outputScale));
        ImageView sheetView = new ImageView(SkinModelView.pixelScaled(skinPreview, screenPixelsPerSkinPixel));
        sheetView.setFitWidth(skinPreview.getWidth() * screenPixelsPerSkinPixel / outputScale);
        sheetView.setPreserveRatio(true);
        sheetView.setSmooth(false);
        frame.getChildren().add(sheetView);
        return frame;
    }

    private void renderTeamView() {
        if (teamRoot == null) {
            return;
        }
        teamRoot.getChildren().clear();

        if (!loggedIn || activeMinecraftUsername().isBlank()) {
            teamRoot.getChildren().add(createInfoCard(
                    "Tu equipo",
                    "Inicia sesion para ver tus equipos, invitaciones y miembros."
            ));
            return;
        }

        if (currentTeamContext == null) {
            teamRoot.getChildren().add(createInfoCard(
                    "Cargando equipo",
                    "Estamos consultando tu informacion en la base de datos."
            ));
            return;
        }

        if (!currentTeamContext.hasTeam()) {
            switch (teamPanelMode) {
                case CREATE -> renderCreateTeamState();
                case JOIN -> renderJoinTeamState();
                default -> renderNoTeamState();
            }
            return;
        }

        if (teamPanelMode == TeamPanelMode.EDIT && currentTeamContext.founder()) {
            renderCreateTeamState();
            return;
        }

        teamPanelMode = TeamPanelMode.EMPTY;
        renderCurrentTeamState();
    }

    private void renderNoTeamState() {
        VBox card = createSectionCard();
        card.setAlignment(Pos.CENTER);
        Label title = createHeadingLabel("Aun no tienes equipo");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        Label subtitle = createBodyLabel("Puedes crear uno propio o esperar una invitacion para unirte a otro.");
        subtitle.setWrapText(true);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setMaxWidth(460);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER);
        Button joinButton = new Button("Unirte a un equipo");
        joinButton.getStyleClass().add("team-action-secondary");
        joinButton.setOnAction(event -> {
            teamPanelMode = TeamPanelMode.JOIN;
            renderTeamView();
        });

        Button createButton = new Button("Crear un equipo");
        createButton.getStyleClass().add("team-action-primary");
        createButton.setOnAction(event -> {
            teamPanelMode = TeamPanelMode.CREATE;
            renderTeamView();
        });
        actions.getChildren().addAll(joinButton, createButton);

        card.getChildren().addAll(title, subtitle, actions);
        teamRoot.getChildren().add(card);
    }

    private void renderJoinTeamState() {
        VBox card = createSectionCard();
        card.setAlignment(Pos.CENTER);
        Label title = createHeadingLabel("Unirte a un equipo");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        Label subtitle = createBodyLabel("Las invitaciones llegan a tu campanita de notificaciones. Cuando alguien te invite, podras aceptar o rechazar desde ahi.");
        subtitle.setWrapText(true);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setMaxWidth(480);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER);
        Button openNotifications = new Button("Ver invitaciones");
        openNotifications.getStyleClass().add("team-action-primary");
        openNotifications.setOnAction(event -> {
            notificationsOpen = true;
            updateNotificationsDropdownVisibility(true, true);
        });

        Button backButton = new Button("Volver");
        backButton.getStyleClass().add("team-action-secondary");
        backButton.setOnAction(event -> {
            teamPanelMode = TeamPanelMode.EMPTY;
            renderTeamView();
        });
        actions.getChildren().addAll(openNotifications, backButton);

        card.getChildren().addAll(title, subtitle, actions);
        teamRoot.getChildren().add(card);
    }

    private void renderCreateTeamState() {
        boolean editing = teamPanelMode == TeamPanelMode.EDIT && currentTeamContext != null && currentTeamContext.hasTeam();
        VBox card = createSectionCard();
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(620);
        Label title = createHeadingLabel(editing ? "Edita tu equipo" : "Crea tu equipo");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        Label subtitle = createBodyLabel(editing
                ? "Actualiza el icono, el nombre o el color representante de tu equipo."
                : "Sube un icono, elige un nombre y define el color representante.");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(420);
        subtitle.setAlignment(Pos.CENTER);

        VBox header = new VBox(14);
        header.setAlignment(Pos.CENTER);
        StackPane preview = buildTeamIconNode(pendingTeamIconPreview, pendingTeamColorHex, 118);
        header.getChildren().add(buildTeamDraftIconControl(preview, 118));

        Label nameLabel = createFieldLabel("Nombre de equipo");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setAlignment(Pos.CENTER);
        TextField nameField = new TextField(pendingTeamName);
        nameField.getStyleClass().add("settings-field");
        nameField.setPromptText("Nombre de tu equipo");
        nameField.setMaxWidth(340);
        nameField.textProperty().addListener((obs, oldV, newV) -> pendingTeamName = newV == null ? "" : newV);

        Label colorLabel = createFieldLabel("Color representante");
        colorLabel.setMaxWidth(Double.MAX_VALUE);
        colorLabel.setAlignment(Pos.CENTER);
        HBox colorRow = new HBox(12);
        colorRow.setAlignment(Pos.CENTER);
        Region swatch = new Region();
        swatch.getStyleClass().add("team-color-swatch");
        Label validationLabel = new Label(pendingTeamDraftError == null ? "" : pendingTeamDraftError);
        validationLabel.getStyleClass().add("team-validation-error");
        validationLabel.setWrapText(true);
        validationLabel.setMaxWidth(420);
        validationLabel.setAlignment(Pos.CENTER);

        pendingTeamColorHex = resolveDraftTeamColor();
        applyTeamColorSelection(pendingTeamColorHex, swatch, preview, validationLabel);

        TilePane palette = new TilePane();
        palette.setAlignment(Pos.CENTER);
        palette.setPrefColumns(8);
        palette.setPrefTileWidth(22);
        palette.setPrefTileHeight(22);
        palette.setHgap(6);
        palette.setVgap(6);
        palette.setMaxWidth(230);
        palette.getStyleClass().add("team-color-palette");
        for (String option : TEAM_COLOR_OPTIONS) {
            palette.getChildren().add(buildColorSwatchButton(option, swatch, preview, validationLabel));
        }
        colorRow.getChildren().add(swatch);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER);
        Button createButton = new Button(editing ? "Guardar cambios" : "Crear equipo");
        createButton.getStyleClass().add("team-action-primary");
        createButton.setOnAction(event -> submitTeamDraftAsync(createButton, validationLabel));
        nameField.textProperty().addListener((obs, oldV, newV) -> updateTeamDraftValidation(validationLabel));

        Button cancelButton = new Button("Cancelar");
        cancelButton.getStyleClass().add("team-action-secondary");
        cancelButton.setOnAction(event -> {
            teamPanelMode = TeamPanelMode.EMPTY;
            pendingTeamDraftError = "";
            renderTeamView();
        });
        actions.getChildren().addAll(createButton, cancelButton);

        updateTeamDraftValidation(validationLabel);
        card.getChildren().addAll(title, subtitle, header, nameLabel, nameField, colorLabel, colorRow, palette, validationLabel, actions);
        teamRoot.getChildren().add(card);
    }

    private void renderCurrentTeamState() {
        presenceDotsByUsername.clear();
        SupabaseTeamsService.TeamRecord team = currentTeamContext.team();
        VBox card = createSectionCard();
        HBox header = new HBox(18);
        header.setAlignment(Pos.CENTER_LEFT);

        Image teamImage = decodeImage(team.teamIconBase64());
        StackPane iconNode = buildTeamIconNode(teamImage, team.representativeColor(), 116);

        VBox titleBox = new VBox(6);
        Label title = createHeadingLabel(team.teamName());
        Label subtitle = createBodyLabel(currentTeamContext.founder()
                ? "Eres el fundador de este equipo."
                : "Formas parte de este equipo.");
        subtitle.setWrapText(true);
        titleBox.getChildren().addAll(title, subtitle);
        header.getChildren().addAll(iconNode, titleBox);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        if (currentTeamContext.founder()) {
            Button editButton = new Button();
            editButton.getStyleClass().addAll("team-action-secondary", "team-edit-btn");
            editButton.setText("");
            if (editIcon != null) {
                editButton.setGraphic(makeIcon(editIcon, 20));
            } else {
                editButton.setText("Editar");
            }
            editButton.setOnAction(event -> openEditTeamState());
            header.getChildren().add(editButton);
        }

        HBox actions = new HBox(12);
        if (currentTeamContext.founder()) {
            Button inviteButton = new Button("Invitar jugadores");
            inviteButton.getStyleClass().add("team-action-primary");
            inviteButton.setOnAction(event -> openInviteDialog());
            actions.getChildren().add(inviteButton);
        }
        Button leaveButton = new Button("Abandonar equipo");
        leaveButton.getStyleClass().add("team-action-secondary");
        leaveButton.setOnAction(event -> leaveTeamAsync());
        actions.getChildren().add(leaveButton);

        VBox membersBox = new VBox(10);
        Label membersTitle = createFieldLabel("Miembros");
        membersBox.getChildren().add(membersTitle);
        for (SupabaseTeamsService.TeamMember member : team.members()) {
            membersBox.getChildren().add(buildMemberRow(member));
        }

        card.getChildren().addAll(header, actions, membersBox);
        teamRoot.getChildren().add(card);
        refreshPresenceAsync();
    }

    private void openEditTeamState() {
        if (currentTeamContext == null || currentTeamContext.team() == null || !currentTeamContext.founder()) {
            return;
        }
        SupabaseTeamsService.TeamRecord team = currentTeamContext.team();
        pendingTeamName = team.teamName();
        pendingTeamColorHex = normalizeTeamHex(team.representativeColor());
        pendingTeamIconBase64 = team.teamIconBase64();
        pendingTeamIconPreview = decodeImage(team.teamIconBase64());
        pendingTeamDraftError = "";
        teamPanelMode = TeamPanelMode.EDIT;
        renderTeamView();
    }

    private VBox createInfoCard(String titleText, String subtitleText) {
        VBox card = createSectionCard();
        Label title = createHeadingLabel(titleText);
        Label subtitle = createBodyLabel(subtitleText);
        subtitle.setWrapText(true);
        card.getChildren().addAll(title, subtitle);
        return card;
    }

    private VBox createSectionCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("team-card");
        card.setFillWidth(true);
        card.setMaxWidth(780);
        return card;
    }

    private Label createHeadingLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("team-heading");
        return label;
    }

    private Label createBodyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("team-body");
        return label;
    }

    private Label createFieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("team-field-label");
        return label;
    }

    private void updateTeamDraftValidation(Label validationLabel) {
        pendingTeamDraftError = validateTeamDraft();
        if (validationLabel != null) {
            validationLabel.setText(pendingTeamDraftError);
        }
    }

    private String validateTeamDraft() {
        String name = pendingTeamName == null ? "" : pendingTeamName.trim();
        if (name.isBlank()) {
            return "Escribe un nombre para tu equipo.";
        }
        if (!name.matches("[A-Za-z0-9 _-]{3,24}")) {
            return "El nombre del equipo debe tener entre 3 y 24 caracteres y solo usar letras, numeros, espacios, - o _.";
        }
        if (isTeamColorReservedByAnotherTeam(pendingTeamColorHex)) {
            return "Ese color ya esta siendo usado por otro equipo.";
        }
        if (pendingTeamIconBase64 == null || pendingTeamIconBase64.isBlank()) {
            return "Sube un icono para tu equipo.";
        }
        return "";
    }

    private void applyTeamColorSelection(String value, Region swatch, StackPane preview, Label validationLabel) {
        String normalized = normalizeTeamHex(value);
        pendingTeamColorHex = normalized;
        if (swatch != null) {
            swatch.setStyle("-fx-background-color: " + normalized + ";");
        }
        if (preview != null) {
            preview.setStyle("-fx-border-color: " + normalized + ";");
        }
        updateTeamDraftValidation(validationLabel);
    }

    private Button buildColorSwatchButton(String hex, Region swatch, StackPane preview, Label validationLabel) {
        Button button = new Button();
        button.getStyleClass().add("team-palette-btn");
        button.setStyle("-fx-background-color: " + hex + ";");
        boolean unavailable = isTeamColorReservedByAnotherTeam(hex);
        button.setDisable(unavailable);
        if (unavailable) {
            button.setTooltip(new Tooltip("Ese color ya pertenece a otro equipo."));
        } else {
            button.setOnAction(event -> applyTeamColorSelection(hex, swatch, preview, validationLabel));
        }
        return button;
    }

    private String resolveDraftTeamColor() {
        String normalized = normalizeTeamHex(pendingTeamColorHex);
        if (!isTeamColorReservedByAnotherTeam(normalized)) {
            return normalized;
        }
        for (String option : TEAM_COLOR_OPTIONS) {
            if (!isTeamColorReservedByAnotherTeam(option)) {
                return option;
            }
        }
        return normalized;
    }

    private boolean isTeamColorReservedByAnotherTeam(String colorHex) {
        String normalized = normalizeTeamHex(colorHex);
        String editableColor = "";
        if (teamPanelMode == TeamPanelMode.EDIT && currentTeamContext != null && currentTeamContext.team() != null) {
            editableColor = normalizeTeamHex(currentTeamContext.team().representativeColor());
        }
        if (normalized.equalsIgnoreCase(editableColor)) {
            return false;
        }
        if (currentTeamContext == null || currentTeamContext.reservedTeamColors() == null) {
            return false;
        }
        LinkedHashSet<String> reserved = new LinkedHashSet<>();
        for (String reservedColor : currentTeamContext.reservedTeamColors()) {
            reserved.add(normalizeTeamHex(reservedColor));
        }
        return reserved.contains(normalized);
    }

    private HBox buildMemberRow(SupabaseTeamsService.TeamMember member) {
        HBox row = new HBox(12);
        row.getStyleClass().add("team-member-row");
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = buildAvatarWithPresence(
                member.minecraftUsername(),
                currentTeamContext == null || currentTeamContext.team() == null ? "" : currentTeamContext.team().representativeColor(),
                54
        );
        VBox info = new VBox(4);
        HBox nameRow = new HBox(6);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(member.minecraftUsername());
        name.getStyleClass().add("team-member-name");
        nameRow.getChildren().add(name);
        if (member.founder() && crownIcon != null) {
            nameRow.getChildren().add(makeIcon(crownIcon, 16));
        }
        Label role = new Label(member.founder() ? "Fundador" : "Miembro");
        role.getStyleClass().add("team-member-role");
        info.getChildren().addAll(nameRow, role);

        row.getChildren().addAll(avatar, info);
        HBox.setHgrow(info, Priority.ALWAYS);

        if (currentTeamContext != null && currentTeamContext.founder() && !member.founder()) {
            Button kickButton = new Button("Expulsar");
            kickButton.getStyleClass().add("team-kick-btn");
            kickButton.setOnAction(event -> removeMemberAsync(member.minecraftUsername()));
            row.getChildren().add(kickButton);
        }

        return row;
    }

    private void renderNotifications() {
        if (notificationsList == null) {
            return;
        }
        notificationsList.getChildren().clear();

        boolean hasTeamNotifications = loggedIn && currentTeamContext != null && !currentTeamContext.notifications().isEmpty();
        if (!localNotifications.isEmpty()) {
            notificationsList.getChildren().add(buildNotificationsSectionLabel("LAUNCHER"));
            for (LocalNotification notification : localNotifications) {
                notificationsList.getChildren().add(buildLocalNotificationCard(notification));
            }
        }
        if (hasTeamNotifications) {
            if (!localNotifications.isEmpty()) {
                notificationsList.getChildren().add(buildNotificationsSectionLabel("EQUIPO"));
            }
            for (SupabaseTeamsService.NotificationItem item : currentTeamContext.notifications()) {
                notificationsList.getChildren().add(buildTeamNotificationCard(item));
            }
        }
        if (localNotifications.isEmpty() && !hasTeamNotifications) {
            notificationsList.getChildren().add(buildEmptyNotificationsState());
        }
    }

    private Node buildNotificationsSectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("notification-section");
        return label;
    }

    private Node buildLocalNotificationCard(LocalNotification notification) {
        VBox card = new VBox(6);
        card.getStyleClass().add("notification-card");
        if ("update".equals(notification.kind())) {
            card.getStyleClass().add("notification-card-highlight");
        }

        Label title = new Label(notification.title());
        title.getStyleClass().add("notification-title");
        title.setWrapText(true);
        Label time = new Label(formatRelativeTime(notification.createdAt()));
        time.getStyleClass().add("notification-meta");
        card.getChildren().addAll(title, time);

        if (notification.body() != null && !notification.body().isBlank()) {
            Label body = new Label(notification.body());
            body.getStyleClass().add("notification-body");
            body.setWrapText(true);
            card.getChildren().add(body);
        }
        if (notification.action() != null && notification.actionLabel() != null) {
            Button action = new Button(notification.actionLabel());
            action.getStyleClass().add("notification-accept-btn");
            action.setOnAction(event -> {
                updateNotificationsDropdownVisibility(false, false);
                notification.action().run();
            });
            card.getChildren().add(action);
        }
        return card;
    }

    private Node buildTeamNotificationCard(SupabaseTeamsService.NotificationItem item) {
        boolean pendingInvite = "team_invite".equals(item.type()) && "pending".equals(item.status());
        VBox card = new VBox(8);
        card.getStyleClass().add("notification-card");
        if (!item.read() || pendingInvite) {
            card.getStyleClass().add("notification-card-highlight");
        }

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        if ("team_invite".equals(item.type()) || (item.teamIconBase64() != null && !item.teamIconBase64().isBlank())) {
            header.getChildren().add(buildTeamIconNode(decodeImage(item.teamIconBase64()), item.teamColorHex(), 40));
        } else if (item.inviterMinecraftUsername() != null && !item.inviterMinecraftUsername().isBlank()) {
            header.getChildren().add(buildMinecraftHeadNode(item.inviterMinecraftUsername(), item.teamColorHex(), 40));
        }

        Label title = new Label(item.teamName() == null || item.teamName().isBlank() ? "Notificación" : item.teamName());
        title.getStyleClass().add("notification-title");
        title.setWrapText(true);
        VBox text = new VBox(2, title);
        StringBuilder meta = new StringBuilder();
        if (item.inviterMinecraftUsername() != null && !item.inviterMinecraftUsername().isBlank()) {
            meta.append("Invitado por ").append(item.inviterMinecraftUsername());
        }
        String when = formatRelativeTime(parseInstantSafely(item.createdAt()));
        if (!when.isBlank()) {
            meta.append(meta.isEmpty() ? "" : ", ").append(when);
        }
        if (!meta.isEmpty()) {
            Label metaLabel = new Label(meta.toString());
            metaLabel.getStyleClass().add("notification-meta");
            metaLabel.setWrapText(true);
            text.getChildren().add(metaLabel);
        }
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        header.getChildren().add(text);
        card.getChildren().add(header);

        if (item.message() != null && !item.message().isBlank()) {
            Label body = new Label(item.message());
            body.getStyleClass().add("notification-body");
            body.setWrapText(true);
            card.getChildren().add(body);
        }

        if (pendingInvite) {
            Button acceptButton = new Button("Aceptar");
            acceptButton.getStyleClass().add("notification-accept-btn");
            acceptButton.setOnAction(event -> respondToInviteAsync(item, true));
            Button rejectButton = new Button("Rechazar");
            rejectButton.getStyleClass().add("notification-reject-btn");
            rejectButton.setOnAction(event -> respondToInviteAsync(item, false));
            card.getChildren().add(new HBox(8, acceptButton, rejectButton));
        } else {
            Label state = new Label(formatNotificationState(item.status()));
            state.getStyleClass().add("notification-state");
            card.getChildren().add(state);
        }
        return card;
    }

    private Node buildEmptyNotificationsState() {
        Label title = new Label("Sin notificaciones");
        title.getStyleClass().add("notification-title");
        Label body = new Label("Cuando recibas invitaciones o novedades del equipo, aparecerán aquí.");
        body.getStyleClass().add("notification-body");
        body.setWrapText(true);
        VBox empty = new VBox(8, title, body);
        empty.getStyleClass().add("notification-empty-state");
        return empty;
    }

    private static String formatRelativeTime(Instant when) {
        if (when == null) {
            return "";
        }
        long seconds = Math.max(0L, java.time.Duration.between(when, Instant.now()).getSeconds());
        if (seconds < 60) {
            return "ahora";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return "hace " + minutes + " min";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return "hace " + hours + " h";
        }
        long days = hours / 24;
        if (days == 1) {
            return "ayer";
        }
        if (days < 7) {
            return "hace " + days + " días";
        }
        return NOTIFICATION_DATE_FORMAT.format(when);
    }

    private String formatNotificationState(String status) {
        return switch (status == null ? "" : status.toLowerCase(Locale.ROOT)) {
            case "accepted" -> "Invitacion aceptada";
            case "rejected" -> "Invitacion rechazada";
            case "expired" -> "Invitacion expirada";
            case "info" -> "Actualizacion";
            default -> "Pendiente";
        };
    }

    private void submitTeamDraftAsync(Button createButton, Label validationLabel) {
        String validationMessage = validateTeamDraft();
        if (!validationMessage.isBlank()) {
            pendingTeamDraftError = validationMessage;
            if (validationLabel != null) {
                validationLabel.setText(validationMessage);
            }
            shakeNode(createButton);
            return;
        }

        pendingTeamDraftError = "";
        if (validationLabel != null) {
            validationLabel.setText("");
        }
        String username = activeMinecraftUsername();
        boolean editing = teamPanelMode == TeamPanelMode.EDIT && currentTeamContext != null && currentTeamContext.team() != null;
        CompletableFuture
                .supplyAsync(() -> editing
                        ? teamsService.updateTeam(new SupabaseTeamsService.UpdateTeamRequest(
                        username,
                        currentTeamContext.team().teamId(),
                        pendingTeamName,
                        pendingTeamColorHex,
                        pendingTeamIconBase64
                ))
                        : teamsService.createTeam(new SupabaseTeamsService.CreateTeamRequest(
                        username,
                        pendingTeamName,
                        pendingTeamColorHex,
                        pendingTeamIconBase64
                )))
                .thenAccept(result -> Platform.runLater(() -> {
                    if (!result.success()) {
                        pendingTeamDraftError = result.message();
                        if (validationLabel != null) {
                            validationLabel.setText(result.message());
                        }
                        shakeNode(createButton);
                        return;
                    }
                    pendingTeamName = "";
                    pendingTeamColorHex = "#9EC0FF";
                    pendingTeamIconBase64 = "";
                    pendingTeamIconPreview = null;
                    pendingTeamDraftError = "";
                    teamPanelMode = TeamPanelMode.EMPTY;
                    refreshTeamContextAsync();
                    showInfo(result.message());
                }));
    }

    private void removeMemberAsync(String targetUsername) {
        if (!confirmAction("Expulsar miembro", "Seguro que quieres expulsar a " + targetUsername + " de tu equipo?")) {
            return;
        }
        CompletableFuture
                .supplyAsync(() -> teamsService.removeMember(activeMinecraftUsername(), targetUsername))
                .thenAccept(result -> Platform.runLater(() -> {
                    if (!result.success()) {
                        showInfo(result.message());
                        return;
                    }
                    refreshTeamContextAsync();
                }));
    }

    private void leaveTeamAsync() {
        if (!confirmAction("Abandonar equipo", "Seguro que quieres abandonar tu equipo actual?")) {
            return;
        }
        CompletableFuture
                .supplyAsync(() -> teamsService.leaveTeam(activeMinecraftUsername()))
                .thenAccept(result -> Platform.runLater(() -> {
                    if (!result.success()) {
                        showInfo(result.message());
                        return;
                    }
                    teamPanelMode = TeamPanelMode.EMPTY;
                    refreshTeamContextAsync();
                    showInfo(result.message());
                }));
    }

    private void respondToInviteAsync(SupabaseTeamsService.NotificationItem item, boolean accept) {
        String action = accept ? "aceptar" : "rechazar";
        if (!confirmAction("Confirmar decision", "Seguro que quieres " + action + " la invitacion al equipo '" + item.teamName() + "'?")) {
            return;
        }

        CompletableFuture
                .supplyAsync(() -> teamsService.respondToInvitation(activeMinecraftUsername(), item.id(), accept))
                .thenAccept(result -> Platform.runLater(() -> {
                    if (!result.success()) {
                        showInfo(result.message());
                        return;
                    }
                    refreshTeamContextAsync();
                }));
    }

    private void chooseTeamIcon() {
        Stage owner = stage != null ? stage : (titleBar != null && titleBar.getScene() != null && titleBar.getScene().getWindow() instanceof Stage s ? s : null);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecciona el icono del equipo");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imagenes", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );
        File file = owner == null ? chooser.showOpenDialog(null) : chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }

        try {
            Image sourceImage = new Image(file.toURI().toString(), 0, 0, true, true);
            if (sourceImage.isError()) {
                throw new IOException("No se pudo leer la imagen.");
            }
            Image cropped = openTeamIconCropDialog(sourceImage, owner);
            if (cropped == null) {
                return;
            }
            pendingTeamIconBase64 = encodeTeamIconToBase64(cropped);
            pendingTeamIconPreview = cropped;
            pendingTeamDraftError = validateTeamDraft();
            renderTeamView();
        } catch (Exception ex) {
            showInfo("No se pudo usar esa imagen como icono del equipo.");
        }
    }

    private Image openTeamIconCropDialog(Image sourceImage, Stage owner) {
        final double cropSize = 320;
        final double baseScale = Math.max(cropSize / sourceImage.getWidth(), cropSize / sourceImage.getHeight());

        Stage dialog = new Stage();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Recorta el icono del equipo");

        ImageView imageView = new ImageView(sourceImage);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        StackPane viewport = new StackPane(imageView);
        viewport.setPrefSize(cropSize, cropSize);
        viewport.setMinSize(cropSize, cropSize);
        viewport.setMaxSize(cropSize, cropSize);
        viewport.getStyleClass().add("team-crop-viewport");
        Rectangle clip = new Rectangle(cropSize, cropSize);
        viewport.setClip(clip);

        Rectangle outerFrame = new Rectangle(cropSize, cropSize);
        outerFrame.getStyleClass().add("team-crop-frame");
        outerFrame.setMouseTransparent(true);

        Circle guideCircle = new Circle(cropSize * 0.42);
        guideCircle.getStyleClass().add("team-crop-circle");
        guideCircle.setMouseTransparent(true);

        Pane guideGrid = buildCropGuideGrid(cropSize);

        StackPane cropArea = new StackPane(viewport, guideCircle, guideGrid, outerFrame);
        cropArea.setPrefSize(cropSize, cropSize);
        cropArea.setMaxSize(cropSize, cropSize);

        double[] zoom = {1.0};
        double[] dragAnchor = {0.0, 0.0};
        double[] startTranslate = {0.0, 0.0};

        Runnable clampImage = () -> {
            double displayWidth = sourceImage.getWidth() * baseScale * zoom[0];
            double displayHeight = sourceImage.getHeight() * baseScale * zoom[0];
            imageView.setFitWidth(displayWidth);
            imageView.setFitHeight(displayHeight);

            double maxTranslateX = Math.max(0.0, (displayWidth - cropSize) / 2.0);
            double maxTranslateY = Math.max(0.0, (displayHeight - cropSize) / 2.0);
            imageView.setTranslateX(clamp(imageView.getTranslateX(), -maxTranslateX, maxTranslateX));
            imageView.setTranslateY(clamp(imageView.getTranslateY(), -maxTranslateY, maxTranslateY));
        };
        clampImage.run();

        cropArea.setOnMousePressed(event -> {
            dragAnchor[0] = event.getSceneX();
            dragAnchor[1] = event.getSceneY();
            startTranslate[0] = imageView.getTranslateX();
            startTranslate[1] = imageView.getTranslateY();
        });
        cropArea.setOnMouseDragged(event -> {
            imageView.setTranslateX(startTranslate[0] + (event.getSceneX() - dragAnchor[0]));
            imageView.setTranslateY(startTranslate[1] + (event.getSceneY() - dragAnchor[1]));
            clampImage.run();
        });

        Slider zoomSlider = new Slider(1.0, 2.8, 1.0);
        zoomSlider.getStyleClass().add("team-crop-slider");
        zoomSlider.valueProperty().addListener((obs, oldV, newV) -> {
            zoom[0] = newV.doubleValue();
            clampImage.run();
        });

        Label hint = createBodyLabel("Arrastra la imagen para encuadrarla y usa el zoom si lo necesitas.");
        hint.setWrapText(true);
        hint.setMaxWidth(cropSize);
        hint.setAlignment(Pos.CENTER);

        Button saveButton = new Button("Usar icono");
        saveButton.getStyleClass().add("team-action-primary");
        final Image[] croppedResult = {null};
        saveButton.setOnAction(event -> {
            WritableImage snapshot = viewport.snapshot(new SnapshotParameters(), new WritableImage((int) cropSize, (int) cropSize));
            croppedResult[0] = snapshot;
            dialog.close();
        });

        Button cancelButton = new Button("Cancelar");
        cancelButton.getStyleClass().add("team-action-secondary");
        cancelButton.setOnAction(event -> dialog.close());

        HBox actions = new HBox(12, saveButton, cancelButton);
        actions.setAlignment(Pos.CENTER);

        VBox root = new VBox(16, cropArea, hint, zoomSlider, actions);
        root.getStyleClass().add("team-crop-root");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root);
        if (titleBar != null && titleBar.getScene() != null) {
            scene.getStylesheets().addAll(titleBar.getScene().getStylesheets());
        }
        dialog.setScene(scene);
        dialog.showAndWait();
        return croppedResult[0];
    }

    private Pane buildCropGuideGrid(double size) {
        Pane grid = new Pane();
        grid.setMouseTransparent(true);
        grid.setPrefSize(size, size);

        for (int i = 1; i <= 2; i++) {
            double offset = (size / 3.0) * i;

            Rectangle vertical = new Rectangle(1.0, size);
            vertical.setLayoutX(offset);
            vertical.getStyleClass().add("team-crop-grid-line");

            Rectangle horizontal = new Rectangle(size, 1.0);
            horizontal.setLayoutY(offset);
            horizontal.getStyleClass().add("team-crop-grid-line");

            grid.getChildren().addAll(vertical, horizontal);
        }
        return grid;
    }

    private void openInviteDialog() {
        CompletableFuture
                .supplyAsync(() -> teamsService.listInviteCandidates(activeMinecraftUsername()))
                .thenAccept(result -> Platform.runLater(() -> {
                    if (!result.success()) {
                        showInfo(result.message());
                        return;
                    }

                    Stage dialog = new Stage();
                    if (stage != null) {
                        dialog.initOwner(stage);
                    }
                    dialog.initModality(Modality.WINDOW_MODAL);
                    dialog.setTitle("Invitar jugadores");

                    VBox root = new VBox(14);
                    root.getStyleClass().add("team-modal-root");
                    root.setPadding(new Insets(18));

                    Label title = new Label("Invitar a tu equipo");
                    title.getStyleClass().add("team-heading");

                    TextField searchField = new TextField();
                    searchField.getStyleClass().addAll("settings-field", "minecraft-search-field");
                    searchField.setPromptText("Buscar usuario de Minecraft...");

                    VBox resultsBox = new VBox(10);
                    ScrollPane scrollPane = new ScrollPane(resultsBox);
                    scrollPane.setFitToWidth(true);
                    scrollPane.getStyleClass().add("notifications-scroll");
                    VBox.setVgrow(scrollPane, Priority.ALWAYS);

                    Map<String, Circle> inviteDialogDots = new HashMap<>();
                    Runnable renderResults = () -> {
                        resultsBox.getChildren().clear();
                        inviteDialogDots.clear();
                        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
                        List<SupabaseTeamsService.RegisteredPlayer> filtered = result.players().stream()
                                .filter(player -> player.minecraftUsername().toLowerCase(Locale.ROOT).contains(query))
                                .toList();
                        if (filtered.isEmpty()) {
                            resultsBox.getChildren().add(createInfoCard("Sin resultados", "No hay jugadores disponibles con ese filtro."));
                            return;
                        }

                        for (SupabaseTeamsService.RegisteredPlayer player : filtered) {
                            HBox row = new HBox(12);
                            row.getStyleClass().add("team-member-row");
                            row.setAlignment(Pos.CENTER_LEFT);
                            row.setMaxWidth(Double.MAX_VALUE);
                            StackPane avatar = buildAvatarWithPresence(player.minecraftUsername(), "", 44, inviteDialogDots);
                            Label name = new Label(player.minecraftUsername());
                            name.getStyleClass().add("team-member-name");
                            Region spacer = new Region();
                            HBox.setHgrow(spacer, Priority.ALWAYS);
                            Button inviteButton = new Button("Invitar");
                            inviteButton.getStyleClass().add("team-action-primary");
                            inviteButton.setOnAction(event -> {
                                inviteButton.setDisable(true);
                                CompletableFuture
                                        .supplyAsync(() -> teamsService.invitePlayer(activeMinecraftUsername(), player.minecraftUsername()))
                                        .thenAccept(inviteResult -> Platform.runLater(() -> {
                                            inviteButton.setDisable(false);
                                            if (!inviteResult.success()) {
                                                showInfo(inviteResult.message());
                                                return;
                                            }
                                            dialog.close();
                                            refreshTeamContextAsync();
                                        }));
                            });
                            row.getChildren().addAll(avatar, name, spacer, inviteButton);
                            resultsBox.getChildren().add(row);
                        }
                    };

                    searchField.textProperty().addListener((obs, oldV, newV) -> renderResults.run());
                    renderResults.run();
                    refreshPresenceFor(inviteDialogDots);

                    Timeline inviteDialogPresencePoll = new Timeline(
                            new KeyFrame(Duration.seconds(PRESENCE_POLL_SECONDS), event -> refreshPresenceFor(inviteDialogDots))
                    );
                    inviteDialogPresencePoll.setCycleCount(Animation.INDEFINITE);
                    inviteDialogPresencePoll.play();
                    dialog.setOnHidden(event -> inviteDialogPresencePoll.stop());

                    root.getChildren().addAll(title, searchField, scrollPane);
                    Scene dialogScene = new Scene(root, 500, 420);
                    if (titleBar != null && titleBar.getScene() != null) {
                        dialogScene.getStylesheets().addAll(titleBar.getScene().getStylesheets());
                    }
                    dialog.setScene(dialogScene);
                    dialog.show();
                }));
    }

    private Node buildTeamDraftIconControl(StackPane preview, double size) {
        if (pendingTeamIconBase64 == null || pendingTeamIconBase64.isBlank()) {
            return buildTeamIconUploadButton();
        }

        StackPane wrapper = new StackPane();
        wrapper.getStyleClass().add("team-icon-edit-wrapper");
        wrapper.setPrefSize(size, size);
        wrapper.setMinSize(size, size);
        wrapper.setMaxSize(size, size);
        wrapper.setCursor(Cursor.HAND);

        Region overlay = new Region();
        overlay.getStyleClass().add("team-icon-edit-overlay");
        overlay.setMouseTransparent(true);
        overlay.setPrefSize(size, size);
        overlay.setMinSize(size, size);
        overlay.setMaxSize(size, size);
        overlay.setOpacity(0.0);

        StackPane editBadge = new StackPane();
        editBadge.getStyleClass().add("team-icon-edit-indicator");
        editBadge.setMouseTransparent(true);
        editBadge.setOpacity(0.0);
        if (editIcon != null) {
            editBadge.getChildren().add(makeIcon(editIcon, 24));
        } else {
            Label fallback = new Label("EDIT");
            fallback.getStyleClass().add("team-icon-edit-indicator-label");
            editBadge.getChildren().add(fallback);
        }

        ParallelTransition hoverIn = parallel(fade(overlay, 1.0, 180), fade(editBadge, 1.0, 180));
        ParallelTransition hoverOut = parallel(fade(overlay, 0.0, 180), fade(editBadge, 0.0, 180));
        wrapper.setOnMouseEntered(event -> {
            hoverOut.stop();
            hoverIn.playFromStart();
        });
        wrapper.setOnMouseExited(event -> {
            hoverIn.stop();
            hoverOut.playFromStart();
        });
        wrapper.setOnMouseClicked(event -> chooseTeamIcon());
        wrapper.getChildren().addAll(preview, overlay, editBadge);
        return wrapper;
    }

    private Button buildTeamIconUploadButton() {
        Button uploadButton = new Button("SUBIR");
        uploadButton.getStyleClass().add("team-upload-btn");
        if (cameraIcon != null) {
            VBox graphic = new VBox(6, makeIcon(cameraIcon, 28), new Label("SUBIR"));
            graphic.setAlignment(Pos.CENTER);
            uploadButton.setGraphic(graphic);
            uploadButton.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        }
        uploadButton.setOnAction(event -> chooseTeamIcon());
        return uploadButton;
    }

    private StackPane buildTeamIconNode(Image image, String colorHex, double size) {
        Image resolvedImage = image != null ? image : (teamNavIcon != null ? teamNavIcon : loadAsset("icon.png"));
        StackPane outer = new StackPane();
        outer.getStyleClass().add("team-icon-ring");
        outer.setPrefSize(size, size);
        outer.setMinSize(size, size);
        outer.setMaxSize(size, size);
        outer.setStyle("-fx-border-color: " + normalizeTeamHex(colorHex) + ";");

        StackPane inner = new StackPane();
        inner.getStyleClass().add("team-icon-inner");
        double innerSize = size - 14;
        inner.setPrefSize(innerSize, innerSize);
        inner.setMinSize(innerSize, innerSize);
        inner.setMaxSize(innerSize, innerSize);

        if (resolvedImage != null) {
            ImageView imageView = new ImageView(resolvedImage);
            imageView.setFitWidth(innerSize - 4);
            imageView.setFitHeight(innerSize - 4);
            imageView.setPreserveRatio(false);
            Circle clip = new Circle((innerSize - 4) / 2);
            clip.setCenterX((innerSize - 4) / 2);
            clip.setCenterY((innerSize - 4) / 2);
            imageView.setClip(clip);
            inner.getChildren().add(imageView);
        }

        outer.getChildren().add(inner);
        return outer;
    }

    private void refreshCurrentUserAvatar() {
        if (currentUserAvatar == null) {
            return;
        }
        currentUserAvatar.getChildren().setAll(buildMinecraftHeadNode(activeMinecraftUsername(), currentUserTeamColor(), 54));
    }

    private void primeCurrentUserSkinCache() {
        String normalized = normalizeMinecraftHeadKey(activeMinecraftUsername());
        if (normalized.isBlank() || currentSkinContext == null || !currentSkinContext.hasCustomSkin()) {
            return;
        }
        Image image = decodeImage(currentSkinContext.skinBase64());
        if (image != null) {
            minecraftSkinImageCache.put(normalized, image);
        }
    }

    private StackPane buildMinecraftHeadNode(String minecraftUsername, String teamColorHex, double size) {
        double resolvedSize = Math.max(28.0, size);
        double innerSize = resolvedSize - 8.0;

        StackPane outer = new StackPane();
        outer.getStyleClass().add("minecraft-head-shell");
        outer.setPrefSize(resolvedSize, resolvedSize);
        outer.setMinSize(resolvedSize, resolvedSize);
        outer.setMaxSize(resolvedSize, resolvedSize);
        String borderHex = optionalTeamBorderHex(teamColorHex);
        outer.setStyle(borderHex.isBlank()
                ? "-fx-border-color: transparent;"
                : "-fx-border-color: " + borderHex + ";");

        StackPane inner = new StackPane();
        inner.getStyleClass().add("minecraft-head-inner");
        inner.setPrefSize(innerSize, innerSize);
        inner.setMinSize(innerSize, innerSize);
        inner.setMaxSize(innerSize, innerSize);

        ImageView imageView = new ImageView(resolveMinecraftHeadImage(minecraftUsername, (int) Math.round(innerSize)));
        imageView.setFitWidth(innerSize);
        imageView.setFitHeight(innerSize);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(false);
        // Misma forma que el marco: cuadrado con esquinas apenas redondeadas, como una cabeza de Minecraft.
        Rectangle clip = new Rectangle(innerSize, innerSize);
        clip.setArcWidth(HEAD_INNER_RADIUS * 2);
        clip.setArcHeight(HEAD_INNER_RADIUS * 2);
        imageView.setClip(clip);
        inner.getChildren().add(imageView);
        outer.getChildren().add(inner);

        requestMinecraftSkinAsync(minecraftUsername);
        return outer;
    }

    private Image resolveMinecraftHeadImage(String minecraftUsername, int targetSize) {
        Image skinImage = resolveMinecraftSkinImage(minecraftUsername);
        Image head = createSkinHeadPreview(skinImage, Math.max(24, targetSize));
        if (head != null) {
            return head;
        }
        Image placeholder = resolvePlaceholderSkinAsset();
        Image placeholderHead = createSkinHeadPreview(placeholder, Math.max(24, targetSize));
        return placeholderHead != null ? placeholderHead : placeholder;
    }

    private Image resolveMinecraftSkinImage(String minecraftUsername) {
        String normalized = normalizeMinecraftHeadKey(minecraftUsername);
        if (normalized.isBlank()) {
            return resolvePlaceholderSkinAsset();
        }

        if (currentSkinContext != null
                && currentSkinContext.hasCustomSkin()
                && normalized.equalsIgnoreCase(normalizeMinecraftHeadKey(currentSkinContext.normalizedMinecraftUsername()))) {
            Image currentUserSkin = decodeImage(currentSkinContext.skinBase64());
            if (currentUserSkin != null) {
                minecraftSkinImageCache.put(normalized, currentUserSkin);
                return currentUserSkin;
            }
        }

        return minecraftSkinImageCache.getOrDefault(normalized, resolvePlaceholderSkinAsset());
    }

    private void requestMinecraftSkinAsync(String minecraftUsername) {
        String normalized = normalizeMinecraftHeadKey(minecraftUsername);
        if (normalized.isBlank()) {
            return;
        }

        if (currentSkinContext != null
                && currentSkinContext.hasCustomSkin()
                && normalized.equalsIgnoreCase(normalizeMinecraftHeadKey(currentSkinContext.normalizedMinecraftUsername()))) {
            primeCurrentUserSkinCache();
            return;
        }
        if (minecraftSkinImageCache.containsKey(normalized) || !minecraftSkinLoadsInFlight.add(normalized)) {
            return;
        }

        CompletableFuture
                .supplyAsync(() -> {
                    SupabaseSkinService.LoadResult result = skinService.loadSkinContext(minecraftUsername);
                    if (result.success() && result.context() != null && result.context().hasCustomSkin()) {
                        Image image = decodeImage(result.context().skinBase64());
                        if (image != null) {
                            return image;
                        }
                    }
                    return resolvePlaceholderSkinAsset();
                })
                .thenAccept(image -> Platform.runLater(() -> {
                    minecraftSkinLoadsInFlight.remove(normalized);
                    if (servicesShutdown) {
                        return;
                    }
                    minecraftSkinImageCache.put(normalized, image != null ? image : resolvePlaceholderSkinAsset());
                    refreshCurrentUserAvatar();
                    if (currentTeamContext != null) {
                        renderTeamView();
                        renderNotifications();
                    }
                }));
    }

    private Image resolvePlaceholderSkinAsset() {
        if (steveSkinAsset == null) {
            steveSkinAsset = loadAsset("steve_skin.png");
        }
        if (steveSkinAsset != null) {
            return steveSkinAsset;
        }
        return teamNavIcon != null ? teamNavIcon : loadAsset("icon.png");
    }

    private String currentUserTeamColor() {
        if (currentTeamContext == null || !currentTeamContext.hasTeam() || currentTeamContext.team() == null) {
            return "";
        }
        return defaultString(currentTeamContext.team().representativeColor());
    }

    private static String normalizeMinecraftHeadKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String optionalTeamBorderHex(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        return normalized.matches("^#[0-9A-F]{6}$") ? normalized : "";
    }

    private static String normalizeTeamHex(String raw) {
        if (raw == null || raw.isBlank()) {
            return "#9EC0FF";
        }
        String text = raw.trim().toUpperCase(Locale.ROOT);
        if (!text.startsWith("#")) {
            text = "#" + text;
        }
        return text.matches("^#[0-9A-F]{6}$") ? text : "#9EC0FF";
    }

    private static Image decodeImage(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return new Image(new ByteArrayInputStream(bytes));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Image bufferedImageToFxImage(BufferedImage image) throws Exception {
        if (image == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new Image(new ByteArrayInputStream(out.toByteArray()));
    }

    private static String encodeBufferedImageToBase64(BufferedImage image) throws Exception {
        if (image == null) {
            throw new IOException("Imagen invalida.");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static Image createSkinHeadPreview(Image skinImage, int targetSize) {
        BufferedImage skin = fxImageToBufferedImage(skinImage);
        if (skin == null || skin.getWidth() < 48 || skin.getHeight() < 16) {
            return null;
        }

        BufferedImage head = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D headGraphics = head.createGraphics();
        headGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        headGraphics.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
        headGraphics.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, null);
        headGraphics.dispose();

        BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        graphics.drawImage(head, 0, 0, targetSize, targetSize, null);
        graphics.dispose();
        try {
            return bufferedImageToFxImage(scaled);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Image createSkinBodyPreview(Image skinImage, boolean frontView, int targetHeight) {
        BufferedImage skin = fxImageToBufferedImage(skinImage);
        if (skin == null || skin.getWidth() < 48 || skin.getHeight() < 32) {
            return null;
        }

        boolean modernSkin = skin.getHeight() >= 64;
        boolean slim = "slim".equalsIgnoreCase(SupabaseSkinService.normalizeModel(pendingSkinModel));
        int armWidth = slim ? 3 : 4;
        int leftArmX = slim ? 13 : 12;

        BufferedImage body = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = body.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        if (frontView) {
            drawSkinPart(graphics, skin, 8, 8, 8, 8, 4, 0, 8, 8);
            drawSkinPart(graphics, skin, 20, 20, 8, 12, 4, 8, 8, 12);
            drawSkinPart(graphics, skin, 44, 20, 4, 12, 0, 8, armWidth, 12);
            drawSkinPart(graphics, skin, modernSkin ? 36 : 44, modernSkin ? 52 : 20, 4, 12, leftArmX, 8, armWidth, 12);
            drawSkinPart(graphics, skin, 4, 20, 4, 12, 4, 20, 4, 12);
            drawSkinPart(graphics, skin, modernSkin ? 20 : 4, modernSkin ? 52 : 20, 4, 12, 8, 20, 4, 12);

            drawSkinPart(graphics, skin, 40, 8, 8, 8, 4, 0, 8, 8);
            if (modernSkin) {
                drawSkinPart(graphics, skin, 20, 36, 8, 12, 4, 8, 8, 12);
                drawSkinPart(graphics, skin, 44, 36, 4, 12, 0, 8, armWidth, 12);
                drawSkinPart(graphics, skin, 52, 52, 4, 12, leftArmX, 8, armWidth, 12);
                drawSkinPart(graphics, skin, 4, 36, 4, 12, 4, 20, 4, 12);
                drawSkinPart(graphics, skin, 4, 52, 4, 12, 8, 20, 4, 12);
            }
        } else {
            drawSkinPart(graphics, skin, 24, 8, 8, 8, 4, 0, 8, 8);
            drawSkinPart(graphics, skin, 32, 20, 8, 12, 4, 8, 8, 12);
            drawSkinPart(graphics, skin, 52, 20, 4, 12, 0, 8, armWidth, 12);
            drawSkinPart(graphics, skin, modernSkin ? 44 : 52, modernSkin ? 52 : 20, 4, 12, leftArmX, 8, armWidth, 12);
            drawSkinPart(graphics, skin, 12, 20, 4, 12, 4, 20, 4, 12);
            drawSkinPart(graphics, skin, modernSkin ? 28 : 12, modernSkin ? 52 : 20, 4, 12, 8, 20, 4, 12);

            drawSkinPart(graphics, skin, 56, 8, 8, 8, 4, 0, 8, 8);
            if (modernSkin) {
                drawSkinPart(graphics, skin, 32, 36, 8, 12, 4, 8, 8, 12);
                drawSkinPart(graphics, skin, 52, 36, 4, 12, 0, 8, armWidth, 12);
                drawSkinPart(graphics, skin, 60, 52, 4, 12, leftArmX, 8, armWidth, 12);
                drawSkinPart(graphics, skin, 12, 36, 4, 12, 4, 20, 4, 12);
                drawSkinPart(graphics, skin, 12, 52, 4, 12, 8, 20, 4, 12);
            }
        }

        graphics.dispose();

        int targetWidth = Math.max(1, (int) Math.round(targetHeight * 0.5));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D scaledGraphics = scaled.createGraphics();
        scaledGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        scaledGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        scaledGraphics.drawImage(body, 0, 0, targetWidth, targetHeight, null);
        scaledGraphics.dispose();

        try {
            return bufferedImageToFxImage(scaled);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void drawSkinPart(Graphics2D graphics,
                                     BufferedImage skin,
                                     int srcX,
                                     int srcY,
                                     int srcWidth,
                                     int srcHeight,
                                     int dstX,
                                     int dstY,
                                     int dstWidth,
                                     int dstHeight) {
        if (graphics == null || skin == null || srcX < 0 || srcY < 0) {
            return;
        }
        if (srcX + srcWidth > skin.getWidth() || srcY + srcHeight > skin.getHeight()) {
            return;
        }
        graphics.drawImage(
                skin,
                dstX,
                dstY,
                dstX + dstWidth,
                dstY + dstHeight,
                srcX,
                srcY,
                srcX + srcWidth,
                srcY + srcHeight,
                null
        );
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private boolean isTeamDraftReady() {
        return !pendingTeamName.isBlank()
                && normalizeTeamHex(pendingTeamColorHex) != null
                && pendingTeamIconBase64 != null
                && !pendingTeamIconBase64.isBlank();
    }

    private void shakeNode(Node node) {
        if (node == null) {
            return;
        }
        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), 0.0)),
                new KeyFrame(Duration.millis(50), new KeyValue(node.translateXProperty(), -10.0)),
                new KeyFrame(Duration.millis(100), new KeyValue(node.translateXProperty(), 9.0)),
                new KeyFrame(Duration.millis(150), new KeyValue(node.translateXProperty(), -7.0)),
                new KeyFrame(Duration.millis(200), new KeyValue(node.translateXProperty(), 6.0)),
                new KeyFrame(Duration.millis(250), new KeyValue(node.translateXProperty(), 0.0))
        );
        shake.playFromStart();
    }

    private static String encodeTeamIconToBase64(File file) throws Exception {
        BufferedImage source = ImageIO.read(file);
        if (source == null) {
            throw new IOException("Imagen invalida.");
        }

        return encodeBufferedTeamIconToBase64(source);
    }

    private static String encodeTeamIconToBase64(Image image) throws Exception {
        if (image == null) {
            throw new IOException("Imagen invalida.");
        }
        BufferedImage source = fxImageToBufferedImage(image);
        if (source == null) {
            throw new IOException("Imagen invalida.");
        }
        return encodeBufferedTeamIconToBase64(source);
    }

    private static BufferedImage fxImageToBufferedImage(Image image) {
        if (image == null) {
            return null;
        }
        int width = Math.max(1, (int) Math.round(image.getWidth()));
        int height = Math.max(1, (int) Math.round(image.getHeight()));
        PixelReader reader = image.getPixelReader();
        if (reader == null) {
            return null;
        }

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return out;
    }

    private static String encodeBufferedTeamIconToBase64(BufferedImage source) throws Exception {
        int targetSize = 256;
        BufferedImage output = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setBackground(new java.awt.Color(0, 0, 0, 0));
        graphics.clearRect(0, 0, targetSize, targetSize);

        double scale = Math.min(targetSize / (double) source.getWidth(), targetSize / (double) source.getHeight());
        int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int x = (targetSize - drawWidth) / 2;
        int y = (targetSize - drawHeight) / 2;
        graphics.drawImage(source, x, y, drawWidth, drawHeight, null);
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(output, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private boolean confirmAction(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle("SemenCraft Launcher");
        alert.setHeaderText(title);
        styleDialog(alert);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
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
        // El desenfoque es de lo mas caro para la GPU: en modo optimizado solo se oscurece.
        boolean blur = active && !reducedMotion;
        if (sidebarGlass != null) {
            sidebarGlass.setOpacity(active ? 0.56 : 1.0);
            sidebarGlass.setEffect(blur ? loginBlurLight : null);
        }
        if (centerContent != null) {
            centerContent.setOpacity(active ? 0.42 : 1.0);
            centerContent.setEffect(blur ? loginBlurHeavy : null);
        }
        Node settingsNode = settingsView != null ? settingsView : settingsScroll;
        if (settingsNode != null) {
            settingsNode.setEffect(blur ? loginBlurLight : null);
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
        announceCatalogChanges(!config.closeLauncherOnStart());
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
        if (lower.contains("runtime de java") || lower.contains("runtime portable")) {
            // El intento automatico de descargar/usar un JRE portable fallo por completo
            // (sin internet, arquitectura no soportada, etc.): ofrecemos el enlace manual como respaldo.
            promptJavaDownload(null);
        }
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
        if (reducedMotion) {
            return;
        }
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

    private static Duration motion(int ms) {
        return Duration.millis(reducedMotion ? 1 : ms);
    }

    static FadeTransition fade(Node n, double to, int ms) {
        FadeTransition transition = new FadeTransition(motion(ms), n);
        transition.setToValue(to);
        return transition;
    }

    private static TranslateTransition slide(Node n, double toX, int ms) {
        TranslateTransition transition = new TranslateTransition(motion(ms), n);
        transition.setToX(toX);
        transition.setInterpolator(Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0));
        return transition;
    }

    private static TranslateTransition slideY(Node n, double toY, int ms) {
        TranslateTransition transition = new TranslateTransition(motion(ms), n);
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
        alert.setTitle("Semencraft Launcher");
        alert.setHeaderText("Semencraft Launcher");
        alert.setContentText(msg);
        styleDialog(alert);
        alert.showAndWait();
    }

    private void promptJavaDownload(FabricMinecraftService.JavaRuntimeInfo javaInfo) {
        String detected = javaInfo == null || javaInfo.versionString() == null || javaInfo.versionString().isBlank()
                ? "no detectado"
                : javaInfo.versionString();

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Java requerido");
        alert.setHeaderText("Se necesita Java 21 para iniciar");
        alert.setContentText(
                "El launcher necesita Java " + FabricMinecraftService.REQUIRED_JAVA_MAJOR + "+ para abrir Minecraft.\n\n" +
                        "Detectado: " + detected + "\n\n" +
                        "Si tu launcher viene desde el instalador MSI, la runtime deberia venir incluida. " +
                        "Si aun asi te falta Java o estas ejecutando una build manual, puedes descargarlo ahora."
        );

        ButtonType downloadButton = new ButtonType("Descargar Java 21");
        ButtonType closeButton = new ButtonType("Cerrar");
        alert.getButtonTypes().setAll(downloadButton, closeButton);
        styleDialog(alert);

        var selected = alert.showAndWait();
        if (selected.isPresent() && selected.get() == downloadButton) {
            openExternalUrl(FabricMinecraftService.JAVA_21_WINDOWS_DOWNLOAD_URL);
        }
    }

    private void openExternalUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            java.awt.Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Inicio: version, estado y datos del perfil
    // ------------------------------------------------------------------

    private void applyVersionLabels() {
        String version = shortVersion(LauncherUpdater.currentVersion());
        if (lblTitleVersion != null) {
            lblTitleVersion.setText("v" + version);
        }
        if (lblVersion != null) {
            lblVersion.setText("v" + version + "  -  Fabric " + FabricMinecraftService.TARGET_MINECRAFT_VERSION);
        }
        if (lblLauncherVersion != null) {
            lblLauncherVersion.setText("Semencraft Launcher v" + version);
        }
    }

    private static String shortVersion(String version) {
        if (version == null || version.isBlank()) {
            return "?";
        }
        return version.matches("\\d+\\.\\d+\\.0") ? version.substring(0, version.length() - 2) : version;
    }

    @FXML
    private void onConfigure() {
        showView(ViewMode.SETTINGS, true);
        applySettingsSection(SettingsSection.GENERAL, true);
    }

    @FXML
    private void onModsList() {
        showView(ViewMode.SETTINGS, true);
        applySettingsSection(SettingsSection.MODPACKS, true);
    }

    private void refreshUserPresenceUi() {
        boolean signedIn = loggedIn && !activeMinecraftUsername().isBlank();
        if (lblUserState != null) {
            lblUserState.setText(!signedIn ? "Sin sesión" : (minecraftRunning ? "Jugando" : "En línea"));
        }
        if (userStateDot != null) {
            userStateDot.getStyleClass().removeAll("state-online", "state-playing");
            if (signedIn) {
                userStateDot.getStyleClass().add(minecraftRunning ? "state-playing" : "state-online");
            }
        }
    }

    private void applyPerformanceMode(boolean enabled) {
        performanceMode = enabled;
        reducedMotion = enabled;
        Parent root = titleBar == null ? null : titleBar.getParent();
        if (root != null) {
            root.getStyleClass().remove("perf-mode");
            if (enabled) {
                root.getStyleClass().add("perf-mode");
            }
        }
        if (enabled) {
            if (titleFloatTransition != null) {
                titleFloatTransition.stop();
            }
            if (imgTitle != null) {
                imgTitle.setTranslateY(0);
            }
            stopIndicatorAnimations();
        } else if (titleFloatTransition == null || titleFloatTransition.getStatus() != Animation.Status.RUNNING) {
            startTitleFloat();
        }
        if (currentSkinModelView != null) {
            currentSkinModelView.setAnimated(!enabled && viewMode == ViewMode.SKIN);
        }
        applyLoginBackdropFx(login != null && login.isVisible());
    }

    private void setupRamSliderFill() {
        if (sliderRam == null) {
            return;
        }
        sliderRam.valueProperty().addListener((obs, oldV, newV) -> updateRamSliderFill());
        sliderRam.skinProperty().addListener((obs, oldV, newV) -> Platform.runLater(this::updateRamSliderFill));
        Platform.runLater(this::updateRamSliderFill);
    }

    private void updateRamSliderFill() {
        if (sliderRam == null) {
            return;
        }
        Node track = sliderRam.lookup(".track");
        if (track == null) {
            return;
        }
        double range = Math.max(1.0, sliderRam.getMax() - sliderRam.getMin());
        double percent = clamp((sliderRam.getValue() - sliderRam.getMin()) / range, 0.0, 1.0) * 100.0;
        track.setStyle(String.format(Locale.US,
                "-fx-background-color: linear-gradient(to right, #ffb547 0%%, #ffb547 %.1f%%, rgba(255,255,255,0.12) %.1f%%, rgba(255,255,255,0.12) 100%%);",
                percent, percent));
    }

    // ------------------------------------------------------------------
    // Notificaciones locales, sonido y toasts
    // ------------------------------------------------------------------

    private void playNotificationSound() {
        if (persistedConfig == null || persistedConfig.notificationSound()) {
            NotificationSound.play();
        }
    }

    /** Suena y avisa solo cuando el numero de no leidas SUBE (no al abrir el launcher). */
    private void announceUnreadCount(int unread) {
        int previous = lastAnnouncedUnreadCount;
        lastAnnouncedUnreadCount = unread;
        if (previous < 0 || unread <= previous) {
            return;
        }
        playNotificationSound();
        int fresh = unread - previous;
        if (!notificationsOpen) {
            showToast("info",
                    fresh == 1 ? "Nueva notificación" : fresh + " notificaciones nuevas",
                    "Toca aquí para verlas.",
                    this::openNotificationsPanel);
        }
        refreshTeamContextAsync();
    }

    private void announceCatalogChanges(boolean reloadCatalog) {
        ModpackManager.CatalogChanges changes = ModpackManager.consumeCatalogChanges();
        if (changes == null || changes.isEmpty()) {
            return;
        }
        String summary = changes.summary();
        String details = changes.details();
        addLocalNotification("modpack", "Modpack actualizado",
                details.isBlank() ? summary : summary + "\n\n" + details, null, null);
        showToast("success", "Modpack actualizado",
                summary + ". Solo se descargaron los cambios.", this::openNotificationsPanel);
        playNotificationSound();
        if (reloadCatalog) {
            loadModpackCatalogAsync();
        }
    }

    private void addLocalNotification(String kind, String title, String body, String actionLabel, Runnable action) {
        if ("update".equals(kind)) {
            localNotifications.removeIf(existing -> "update".equals(existing.kind()));
        }
        localNotifications.add(0, new LocalNotification(kind, title, body, Instant.now(), actionLabel, action));
        while (localNotifications.size() > MAX_LOCAL_NOTIFICATIONS) {
            localNotifications.remove(localNotifications.size() - 1);
        }
        if (!notificationsOpen) {
            unreadLocalNotifications++;
        }
        renderNotifications();
        updateNotificationBadge();
    }

    private void openNotificationsPanel() {
        if (!notificationsOpen) {
            onToggleNotifications();
        }
    }

    private void showToast(String kind, String title, String body, Runnable onClick) {
        if (toastLayer == null) {
            return;
        }
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("toast-title");
        VBox text = new VBox(2, titleLabel);
        if (body != null && !body.isBlank()) {
            Label bodyLabel = new Label(body);
            bodyLabel.getStyleClass().add("toast-body");
            bodyLabel.setWrapText(true);
            bodyLabel.setMaxWidth(300);
            text.getChildren().add(bodyLabel);
        }

        HBox toast = new HBox(text);
        toast.getStyleClass().addAll("toast", "toast-" + kind);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setMaxWidth(Region.USE_PREF_SIZE);
        toast.setOnMouseClicked(event -> {
            dismissToast(toast);
            if (onClick != null) {
                onClick.run();
            }
        });

        toastLayer.getChildren().add(toast);
        while (toastLayer.getChildren().size() > 3) {
            toastLayer.getChildren().remove(0);
        }
        toast.setOpacity(0.0);
        toast.setTranslateY(18.0);
        parallel(fade(toast, 1.0, 220), slideY(toast, 0.0, 260)).play();
        delay(6000, () -> dismissToast(toast));
    }

    private void dismissToast(Node toast) {
        if (toastLayer == null || !toastLayer.getChildren().contains(toast)
                || Boolean.TRUE.equals(toast.getProperties().get("closing"))) {
            return;
        }
        toast.getProperties().put("closing", Boolean.TRUE);
        ParallelTransition out = parallel(fade(toast, 0.0, 200), slideY(toast, 12.0, 200));
        out.setOnFinished(event -> toastLayer.getChildren().remove(toast));
        out.play();
    }

    // ------------------------------------------------------------------
    // Actualizaciones del launcher (GitHub Releases)
    // ------------------------------------------------------------------

    private void checkForLauncherUpdateOnStartup() {
        checkForLauncherUpdate(false);
    }

    @FXML
    private void onCheckUpdates() {
        checkForLauncherUpdate(true);
    }

    private void checkForLauncherUpdate(boolean manual) {
        if (updateCheckInFlight || updateInstallInProgress || servicesShutdown) {
            return;
        }
        updateCheckInFlight = true;
        if (btnCheckUpdates != null) {
            btnCheckUpdates.setDisable(true);
        }
        setUpdateStatus("Buscando actualizaciones...");
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return launcherUpdater.checkForUpdate();
                    } catch (IOException ex) {
                        throw new CompletionException(ex);
                    }
                })
                .whenComplete((info, error) -> Platform.runLater(() -> {
                    updateCheckInFlight = false;
                    if (btnCheckUpdates != null) {
                        btnCheckUpdates.setDisable(false);
                    }
                    if (servicesShutdown) {
                        return;
                    }
                    if (error != null) {
                        setUpdateStatus("No se pudo comprobar: " + rootMessage(error));
                        if (manual) {
                            showToast("info", "No se pudo buscar actualizaciones", rootMessage(error), null);
                        }
                        return;
                    }
                    if (info == null) {
                        setUpdateStatus("Tienes la última versión (v" + shortVersion(LauncherUpdater.currentVersion()) + ").");
                        if (manual) {
                            showToast("success", "Todo al día", "Ya tienes la última versión del launcher.", null);
                        }
                        return;
                    }
                    setUpdateStatus("Nueva versión disponible: v" + shortVersion(info.version()) + ".");
                    addLocalNotification("update",
                            "Actualización disponible: v" + shortVersion(info.version()),
                            "Hay una versión nueva del launcher lista para instalar.",
                            "Actualizar",
                            () -> showUpdateModal(info));
                    if (!launchInProgress) {
                        showUpdateModal(info);
                    }
                }));
    }

    private void setUpdateStatus(String text) {
        if (lblUpdateStatus != null) {
            lblUpdateStatus.setText(text);
        }
    }

    private void showUpdateModal(LauncherUpdater.UpdateInfo info) {
        if (modalLayer == null || info == null) {
            return;
        }
        boolean canInstall = info.hasInstaller() && LauncherUpdater.canInstallInPlace();

        Label title = new Label("Versión " + shortVersion(info.version()) + " disponible");
        title.getStyleClass().add("modal-title");
        title.setWrapText(true);

        String current = "Tienes la v" + shortVersion(LauncherUpdater.currentVersion()) + ". ";
        Label body = new Label(current + (canInstall
                ? "¿Quieres actualizar ahora? Se descarga el instalador, se desinstala la versión anterior y el launcher "
                + "se vuelve a abrir solo. Tus mundos, skins y ajustes se conservan."
                : "Esta copia del launcher no se instaló con el instalador oficial: se abrirá la página de descargas."));
        body.getStyleClass().add("modal-body");
        body.setWrapText(true);

        VBox card = new VBox(14, title, body);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(520);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        if (info.notes() != null && !info.notes().isBlank()) {
            Label notes = new Label(info.notes());
            notes.getStyleClass().add("modal-notes");
            notes.setWrapText(true);
            ScrollPane notesScroll = new ScrollPane(notes);
            notesScroll.getStyleClass().add("modal-notes-scroll");
            notesScroll.setFitToWidth(true);
            notesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            notesScroll.setMaxHeight(160);
            card.getChildren().add(notesScroll);
        }

        Label progressLabel = new Label("Preparando descarga...");
        progressLabel.getStyleClass().add("settings-hint");
        Region progressFill = new Region();
        progressFill.getStyleClass().add("modal-progress-fill");
        StackPane progressTrack = new StackPane(progressFill);
        progressTrack.getStyleClass().add("modal-progress-track");
        progressTrack.setAlignment(Pos.CENTER_LEFT);
        setRegionWidth(progressFill, 0.0);
        VBox progressBox = new VBox(8, progressLabel, progressTrack);
        progressBox.setVisible(false);
        progressBox.setManaged(false);
        card.getChildren().add(progressBox);

        Button later = new Button("Más tarde");
        later.getStyleClass().add("ghost-btn");
        Button now = new Button(canInstall ? "Actualizar ahora" : "Ir a la descarga");
        now.getStyleClass().add("primary-btn");
        now.setDefaultButton(true);
        Region actionsSpacer = new Region();
        HBox.setHgrow(actionsSpacer, Priority.ALWAYS);
        HBox actions = new HBox(10, actionsSpacer, later, now);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(6, 0, 0, 0));
        card.getChildren().add(actions);

        later.setOnAction(event -> {
            closeModal();
            showToast("update", "Actualización pendiente",
                    "Puedes instalarla cuando quieras desde la campana de notificaciones.", this::openNotificationsPanel);
        });
        now.setOnAction(event -> {
            if (!canInstall) {
                openExternalUrl(info.pageUrl());
                closeModal();
                return;
            }
            startUpdateDownload(info, now, later, progressBox, progressLabel, progressTrack, progressFill);
        });

        updateNotificationsDropdownVisibility(false, false);
        modalLayer.getChildren().setAll(card);
        modalLayer.setVisible(true);
        modalLayer.setManaged(true);
        modalLayer.toFront();
        modalLayer.setOpacity(0.0);
        card.setScaleX(0.96);
        card.setScaleY(0.96);
        new Timeline(new KeyFrame(motion(220),
                new KeyValue(modalLayer.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(card.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(card.scaleYProperty(), 1.0, Interpolator.EASE_OUT)
        )).play();
    }

    private void startUpdateDownload(
            LauncherUpdater.UpdateInfo info,
            Button now,
            Button later,
            VBox progressBox,
            Label progressLabel,
            StackPane progressTrack,
            Region progressFill
    ) {
        updateInstallInProgress = true;
        now.setDisable(true);
        later.setDisable(true);
        now.setText("Descargando...");
        progressBox.setVisible(true);
        progressBox.setManaged(true);
        progressLabel.setText("Descargando " + info.assetName() + "...");

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return launcherUpdater.downloadInstaller(info, (done, total) -> Platform.runLater(() -> {
                            double fraction = total > 0 ? Math.min(1.0, done / (double) total) : 0.0;
                            setRegionWidth(progressFill, Math.rint(resolveTrackContentWidth(progressTrack) * fraction));
                            progressLabel.setText(String.format(Locale.US, "Descargando... %.1f de %.1f MB",
                                    done / 1048576.0, Math.max(done, total) / 1048576.0));
                        }));
                    } catch (IOException ex) {
                        throw new CompletionException(ex);
                    }
                })
                .whenComplete((msi, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        updateInstallInProgress = false;
                        now.setDisable(false);
                        later.setDisable(false);
                        now.setText("Reintentar");
                        progressLabel.setText("No se pudo descargar: " + rootMessage(error));
                        return;
                    }
                    setRegionWidth(progressFill, resolveTrackContentWidth(progressTrack));
                    now.setText("Instalando...");
                    progressLabel.setText("Abriendo el instalador. El launcher se cerrará y volverá a abrirse al terminar.");
                    try {
                        LauncherUpdater.startInstaller(msi);
                    } catch (IOException ex) {
                        updateInstallInProgress = false;
                        now.setDisable(false);
                        later.setDisable(false);
                        now.setText("Reintentar");
                        progressLabel.setText("No se pudo abrir el instalador: " + rootMessage(ex));
                        return;
                    }
                    // Hay que liberar los archivos del launcher antes de que el MSI los reemplace.
                    delay(900, () -> {
                        shutdownBackgroundServices();
                        Platform.exit();
                        System.exit(0);
                    });
                }));
    }

    private void closeModal() {
        if (modalLayer == null || updateInstallInProgress) {
            return;
        }
        FadeTransition out = fade(modalLayer, 0.0, 180);
        out.setOnFinished(event -> {
            modalLayer.setVisible(false);
            modalLayer.setManaged(false);
            modalLayer.getChildren().clear();
        });
        out.play();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank() ? "error desconocido" : message;
    }

    // ------------------------------------------------------------------
    // Dialogos
    // ------------------------------------------------------------------

    /** Aplica el tema del launcher a los Alert de JavaFX (sin la barra de Windows). */
    private void styleDialog(Alert alert) {
        Stage owner = resolveStage();
        if (owner != null && owner.isShowing()) {
            alert.initOwner(owner);
        }
        alert.initStyle(StageStyle.TRANSPARENT);
        alert.setGraphic(null);
        DialogPane pane = alert.getDialogPane();
        pane.getStyleClass().add("sc-dialog");
        URL css = MainApp.class.getResource("style.css");
        if (css != null && !pane.getStylesheets().contains(css.toExternalForm())) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        alert.setOnShowing(event -> {
            if (pane.getScene() != null) {
                pane.getScene().setFill(Color.TRANSPARENT);
            }
        });
    }
}
