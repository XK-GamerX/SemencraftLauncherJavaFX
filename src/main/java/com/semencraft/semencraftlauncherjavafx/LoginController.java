package com.semencraft.semencraftlauncherjavafx;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.value.ObservableDoubleValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.geometry.Rectangle2D;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LoginController {

    @FXML private StackPane loginRoot;
    @FXML private Region loginBackdrop;
    @FXML private ScrollPane loginScroll;
    @FXML private VBox loginOuter;
    @FXML private HBox loginCard;
    @FXML private VBox loginFormPanel;
    @FXML private StackPane loginArt;
    @FXML private ImageView imgLoginArt;
    @FXML private VBox loginPane;
    @FXML private VBox registerPane;
    @FXML private ImageView imgLoginLogo;

    @FXML private TextField txtLoginUsername;
    @FXML private PasswordField txtLoginPassword;
    @FXML private CheckBox chkRememberMe;
    @FXML private Label lblLoginError;
    @FXML private Button btnLogin;
    @FXML private Hyperlink linkShowRegister;

    @FXML private TextField txtRegisterMinecraftUser;
    @FXML private PasswordField txtRegisterPassword;
    @FXML private PasswordField txtRegisterPasswordRepeat;
    @FXML private TextField txtRegisterDiscordUser;
    @FXML private Label lblRegisterHint;
    @FXML private Label lblRegisterError;
    @FXML private Button btnRegister;
    @FXML private Hyperlink linkShowLogin;

    @FXML private VBox verificationPane;
    @FXML private Label lblVerificationHint;
    @FXML private Label lblVerificationError;
    @FXML private TextField txtVerificationCode;
    @FXML private Button btnVerifyCode;
    @FXML private Hyperlink linkBackToRegister;

    private static final double ART_RADIUS = 14;

    private final SupabaseDatabaseService githubDatabase = new SupabaseDatabaseService();
    private Consumer<String> onSuccess;
    private AuthMode authMode = AuthMode.LOGIN;
    private SupabaseDatabaseService.PendingVerification pendingVerification;

    private enum AuthMode {
        LOGIN,
        REGISTER,
        VERIFY
    }

    public void setOnSuccess(Consumer<String> cb) {
        this.onSuccess = cb;
    }

    /** Si es false, el launcher no guarda la sesion y pedira login la proxima vez. */
    public boolean isRememberMe() {
        return chkRememberMe == null || chkRememberMe.isSelected();
    }

    @FXML
    public void initialize() {
        // Logo textual simplificado; el logo grande (title.png) queda solo en Inicio.
        var logo = LauncherController.loadAsset("logo-semencraft.png");
        if (logo != null) {
            imgLoginLogo.setImage(logo);
            imgLoginLogo.setEffect(null);
        }

        blurLoginBackdrop();
        setupArtPanel();
        bindResponsiveSizes();
        configureKeyboardFlow();
        switchMode(AuthMode.LOGIN, false);
        animateCardIn();
    }

    private void bindResponsiveSizes() {
        DoubleBinding cardWidth = clamp(loginRoot.widthProperty().multiply(0.74), 720, 1080);
        DoubleBinding cardHeight = clamp(loginRoot.heightProperty().multiply(0.80), 500, 640);
        loginCard.prefWidthProperty().bind(cardWidth);
        loginCard.maxWidthProperty().bind(cardWidth);
        loginCard.minHeightProperty().bind(cardHeight);
        loginCard.setMaxHeight(Region.USE_PREF_SIZE);

        DoubleBinding formWidth = clamp(cardWidth.multiply(0.42), 360, 440);
        loginFormPanel.prefWidthProperty().bind(formWidth);
        loginFormPanel.minWidthProperty().bind(formWidth);
        loginFormPanel.maxWidthProperty().bind(formWidth);

        imgLoginLogo.fitWidthProperty().bind(clamp(loginRoot.heightProperty().multiply(0.17), 100, 140));

        if (loginScroll != null) {
            loginScroll.setPannable(false);
        }

        bindControlHeights(txtLoginUsername, txtLoginPassword, txtRegisterMinecraftUser, txtRegisterPassword,
                txtRegisterPasswordRepeat, txtRegisterDiscordUser, txtVerificationCode);
        bindButtonHeights(btnLogin, btnRegister, btnVerifyCode);

        // Alto del area visible del scroll: asi la barra solo sale si la tarjeta de verdad no entra.
        loginOuter.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> loginScroll.getViewportBounds().getHeight(), loginScroll.viewportBoundsProperty()));

        loginRoot.widthProperty().addListener((obs, oldV, newV) -> updateOuterPadding());
        loginRoot.heightProperty().addListener((obs, oldV, newV) -> updateOuterPadding());
        Platform.runLater(this::updateOuterPadding);
    }

    /**
     * Imagen del juego en blanco y negro que llena la mitad derecha (recorte tipo "cover").
     * El recorte se extiende a la izquierda para que solo se redondeen las esquinas de afuera.
     */
    private void setupArtPanel() {
        Image art = LauncherController.loadAsset("bg.png");
        if (art != null) {
            imgLoginArt.setImage(art);
        }
        imgLoginArt.setEffect(new ColorAdjust(0, -1.0, -0.38, 0.05));
        // Fuera del layout: si no, el tamano original de la imagen estira la tarjeta.
        imgLoginArt.setManaged(false);
        loginArt.setPrefWidth(0);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(ART_RADIUS * 2);
        clip.setArcHeight(ART_RADIUS * 2);
        clip.setX(-ART_RADIUS);
        clip.widthProperty().bind(loginArt.widthProperty().add(ART_RADIUS));
        clip.heightProperty().bind(loginArt.heightProperty());
        loginArt.setClip(clip);

        loginArt.widthProperty().addListener((obs, oldV, newV) -> fitArtImage());
        loginArt.heightProperty().addListener((obs, oldV, newV) -> fitArtImage());
    }

    private void fitArtImage() {
        Image art = imgLoginArt.getImage();
        double w = loginArt.getWidth();
        double h = loginArt.getHeight();
        if (art == null || w <= 0 || h <= 0) {
            return;
        }
        double scale = Math.max(w / art.getWidth(), h / art.getHeight());
        double cropW = w / scale;
        double cropH = h / scale;
        imgLoginArt.setViewport(new Rectangle2D((art.getWidth() - cropW) / 2, (art.getHeight() - cropH) / 2, cropW, cropH));
        imgLoginArt.setFitWidth(w);
        imgLoginArt.setFitHeight(h);
        imgLoginArt.relocate(0, 0);
    }

    private void bindControlHeights(TextField... controls) {
        if (controls == null) {
            return;
        }
        for (TextField control : controls) {
            if (control == null) {
                continue;
            }
            control.prefHeightProperty().bind(clamp(loginRoot.heightProperty().multiply(0.062), 42, 48));
        }
    }

    private void bindButtonHeights(Button... buttons) {
        if (buttons == null) {
            return;
        }
        for (Button button : buttons) {
            if (button == null) {
                continue;
            }
            button.prefHeightProperty().bind(clamp(loginRoot.heightProperty().multiply(0.064), 44, 50));
        }
    }

    private void configureKeyboardFlow() {
        txtLoginUsername.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                txtLoginPassword.requestFocus();
            }
        });
        txtLoginPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onLogin();
            }
        });

        txtRegisterMinecraftUser.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                txtRegisterPassword.requestFocus();
            }
        });
        txtRegisterPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                txtRegisterPasswordRepeat.requestFocus();
            }
        });
        txtRegisterPasswordRepeat.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                txtRegisterDiscordUser.requestFocus();
            }
        });
        txtRegisterDiscordUser.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onRegister();
            }
        });
        txtVerificationCode.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onVerifyCode();
            }
        });
    }

    private void updateOuterPadding() {
        double h = loginRoot.getHeight();
        double w = loginRoot.getWidth();

        double vertical = clamp(h * 0.07, 18, 64);
        double horizontal = clamp(w * 0.03, 14, 34);

        loginOuter.setStyle(String.format(Locale.US,
                "-fx-padding: %.0f %.0f %.0f %.0f;",
                vertical,
                horizontal,
                vertical,
                horizontal
        ));
    }

    private static DoubleBinding clamp(ObservableDoubleValue val, double lo, double hi) {
        return Bindings.createDoubleBinding(
                () -> Math.max(lo, Math.min(hi, val.get())),
                val
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void animateCardIn() {
        loginCard.setOpacity(0);
        loginCard.setTranslateY(20);
        loginCard.setScaleX(0.97);
        loginCard.setScaleY(0.97);

        new ParallelTransition(
                makeFade(loginCard, 1.0, 500),
                makeSlideY(loginCard, 480),
                makeScale(loginCard, 420)
        ).play();
    }

    private void blurLoginBackdrop() {
        if (loginBackdrop == null) {
            return;
        }
        // El fondo es un color plano: el desenfoque real lo aplica LauncherController
        // sobre la sidebar y el contenido. Aqui no hace falta un GaussianBlur extra.
        loginBackdrop.setEffect(null);
    }

    public void resetAndAnimate() {
        clearFields();
        clearMessages();
        pendingVerification = null;
        setBusy(false);
        switchMode(AuthMode.LOGIN, false);
        animateCardIn();
    }

    @FXML
    private void onShowRegister() {
        clearMessages();
        pendingVerification = null;
        switchMode(AuthMode.REGISTER, true);
        Platform.runLater(() -> loginScroll.setVvalue(0.0));
        txtRegisterMinecraftUser.requestFocus();
    }

    @FXML
    private void onShowLogin() {
        clearMessages();
        pendingVerification = null;
        switchMode(AuthMode.LOGIN, true);
        Platform.runLater(() -> loginScroll.setVvalue(0.0));
        txtLoginUsername.requestFocus();
    }

    @FXML
    private void onLogin() {
        String user = txtLoginUsername.getText().trim();
        String pass = txtLoginPassword.getText();
        clearMessages();

        if (user.isEmpty()) {
            showError(lblLoginError, "Introduce tu usuario.");
            shake(txtLoginUsername);
            return;
        }
        if (pass == null || pass.isBlank()) {
            showError(lblLoginError, "Introduce tu contrasena.");
            shake(txtLoginPassword);
            return;
        }

        setBusy(true);
        CompletableFuture
                .supplyAsync(() -> githubDatabase.authenticate(user, pass))
                .thenAccept(result -> Platform.runLater(() -> {
                    setBusy(false);
                    if (!result.success()) {
                        showError(lblLoginError, result.message());
                        shake(txtLoginPassword);
                        return;
                    }
                    if (onSuccess != null && result.account() != null) {
                        onSuccess.accept(result.account().minecraftUsername());
                    }
                }));
    }

    @FXML
    private void onRegister() {
        String minecraftUser = txtRegisterMinecraftUser.getText().trim();
        String password = txtRegisterPassword.getText();
        String repeat = txtRegisterPasswordRepeat.getText();
        String discordUser = txtRegisterDiscordUser.getText().trim();
        clearMessages();

        if (!SupabaseDatabaseService.isValidMinecraftUsername(minecraftUser)) {
            showError(lblRegisterError, "El nombre de Minecraft debe tener entre 5 y 16 caracteres y solo usar letras, numeros o _.");
            shake(txtRegisterMinecraftUser);
            return;
        }
        if (password == null || password.length() < 4) {
            showError(lblRegisterError, "La contrasena debe tener al menos 4 caracteres.");
            shake(txtRegisterPassword);
            return;
        }
        if (!password.equals(repeat)) {
            showError(lblRegisterError, "Las contrasenas no coinciden.");
            shake(txtRegisterPasswordRepeat);
            return;
        }
        if (discordUser.isBlank()) {
            showError(lblRegisterError, "Introduce tu usuario de Discord.");
            shake(txtRegisterDiscordUser);
            return;
        }

        setBusy(true);
        lblRegisterHint.setText("Validando usuario de Discord y preparando el codigo...");
        CompletableFuture
                .supplyAsync(() -> githubDatabase.startRegistration(new SupabaseDatabaseService.RegistrationRequest(minecraftUser, password, discordUser)))
                .thenAccept(result -> Platform.runLater(() -> {
                    setBusy(false);
                    lblRegisterHint.setText("Debe coincidir con tu usuario en el servidor de Discord.");
                    if (!result.success()) {
                        showError(lblRegisterError, result.message());
                        shake(txtRegisterDiscordUser);
                        return;
                    }
                    pendingVerification = result.pendingVerification();
                    txtVerificationCode.clear();
                    setErrorText(lblVerificationError, "");
                    if (lblVerificationHint != null && pendingVerification != null) {
                        lblVerificationHint.setText(
                                "Revisa tus MDs en Discord. Te enviamos un codigo a "
                                        + pendingVerification.discordDisplayName()
                                        + ". Caduca en 10 minutos (hasta "
                                        + formatVerificationExpiry(pendingVerification.expiresAt())
                                        + ")."
                        );
                    }
                    switchMode(AuthMode.VERIFY, true);
                    Platform.runLater(() -> {
                        loginScroll.setVvalue(0.0);
                        txtVerificationCode.requestFocus();
                    });
                }));
    }

    @FXML
    private void onVerifyCode() {
        if (pendingVerification == null) {
            showError(lblVerificationError, "La verificacion ya no esta disponible. Vuelve a registrarte.");
            return;
        }

        String code = txtVerificationCode.getText().trim();
        clearMessages();
        if (code.isBlank()) {
            showError(lblVerificationError, "Introduce el codigo que recibiste por Discord.");
            shake(txtVerificationCode);
            return;
        }

        setBusy(true);
        CompletableFuture
                .supplyAsync(() -> githubDatabase.finishRegistration(
                        new SupabaseDatabaseService.VerificationRequest(
                                pendingVerification.verificationId(),
                                code
                        )
                ))
                .thenAccept(result -> Platform.runLater(() -> {
                    setBusy(false);
                    if (!result.success()) {
                        showError(lblVerificationError, result.message());
                        shake(txtVerificationCode);
                        return;
                    }
                    if (onSuccess != null && result.account() != null) {
                        pendingVerification = null;
                        onSuccess.accept(result.account().minecraftUsername());
                    }
                }));
    }

    @FXML
    private void onBackToRegister() {
        pendingVerification = null;
        txtVerificationCode.clear();
        clearMessages();
        switchMode(AuthMode.REGISTER, true);
        Platform.runLater(() -> {
            loginScroll.setVvalue(0.0);
            txtRegisterMinecraftUser.requestFocus();
        });
    }

    private void switchMode(AuthMode nextMode, boolean animate) {
        authMode = nextMode;
        boolean showLogin = nextMode == AuthMode.LOGIN;
        boolean showRegister = nextMode == AuthMode.REGISTER;
        boolean showVerify = nextMode == AuthMode.VERIFY;
        setPaneVisible(loginPane, showLogin);
        setPaneVisible(registerPane, showRegister);
        setPaneVisible(verificationPane, showVerify);

        if (!animate) {
            return;
        }

        Node target = showLogin ? loginPane : showRegister ? registerPane : verificationPane;
        if (target == null) {
            return;
        }
        target.setOpacity(0.0);
        target.setTranslateY(10.0);
        new ParallelTransition(
                makeFade(target, 1.0, 220),
                makeSlideY(target, 220)
        ).play();
    }

    private static void setPaneVisible(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setManaged(visible);
        node.setVisible(visible);
        if (!visible) {
            node.setOpacity(1.0);
            node.setTranslateY(0.0);
        }
    }

    private void clearMessages() {
        setErrorText(lblLoginError, "");
        setErrorText(lblRegisterError, "");
        setErrorText(lblVerificationError, "");
    }

    private static void setErrorText(Label label, String text) {
        if (label == null) {
            return;
        }
        label.setText(text == null ? "" : text);
        label.setOpacity((text == null || text.isBlank()) ? 0.0 : 1.0);
    }

    private void showError(Label label, String msg) {
        if (label == null) {
            return;
        }
        label.setText(msg);
        LauncherController.fade(label, 1.0, 200).play();
        LauncherController.delay(4200, () -> {
            if (label.getText() != null && !label.getText().isBlank()) {
                LauncherController.fade(label, 0.0, 350).play();
            }
        });
    }

    private void setBusy(boolean busy) {
        boolean disable = busy;
        if (btnLogin != null) {
            btnLogin.setDisable(disable);
        }
        if (btnRegister != null) {
            btnRegister.setDisable(disable);
        }
        if (btnVerifyCode != null) {
            btnVerifyCode.setDisable(disable);
        }
        if (linkShowRegister != null) {
            linkShowRegister.setDisable(disable);
        }
        if (linkShowLogin != null) {
            linkShowLogin.setDisable(disable);
        }
        if (linkBackToRegister != null) {
            linkBackToRegister.setDisable(disable);
        }
        setInputsDisabled(disable,
                txtLoginUsername,
                txtLoginPassword,
                txtRegisterMinecraftUser,
                txtRegisterPassword,
                txtRegisterPasswordRepeat,
                txtRegisterDiscordUser,
                txtVerificationCode
        );
    }

    private static void setInputsDisabled(boolean disabled, TextField... fields) {
        if (fields == null) {
            return;
        }
        for (TextField field : fields) {
            if (field != null) {
                field.setDisable(disabled);
            }
        }
    }

    private void clearFields() {
        txtLoginUsername.clear();
        txtLoginPassword.clear();
        txtRegisterMinecraftUser.clear();
        txtRegisterPassword.clear();
        txtRegisterPasswordRepeat.clear();
        txtRegisterDiscordUser.clear();
        txtVerificationCode.clear();
        if (lblRegisterHint != null) {
            lblRegisterHint.setText("Debe coincidir con tu usuario en el servidor de Discord.");
        }
        if (lblVerificationHint != null) {
            lblVerificationHint.setText("Revisa tus MDs en Discord y escribe aquí tu código temporal.");
        }
    }

    private static String formatVerificationExpiry(String rawInstant) {
        try {
            Instant instant = Instant.parse(rawInstant);
            return DateTimeFormatter.ofPattern("HH:mm")
                    .withLocale(Locale.ROOT)
                    .withZone(ZoneId.systemDefault())
                    .format(instant);
        } catch (Exception ignored) {
            return "unos minutos";
        }
    }

    private static void shake(Node node) {
        new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(55), new KeyValue(node.translateXProperty(), -9)),
                new KeyFrame(Duration.millis(110), new KeyValue(node.translateXProperty(), 9)),
                new KeyFrame(Duration.millis(165), new KeyValue(node.translateXProperty(), -7)),
                new KeyFrame(Duration.millis(220), new KeyValue(node.translateXProperty(), 7)),
                new KeyFrame(Duration.millis(275), new KeyValue(node.translateXProperty(), -4)),
                new KeyFrame(Duration.millis(310), new KeyValue(node.translateXProperty(), 0))
        ).play();
    }

    private static FadeTransition makeFade(Node n, double to, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setToValue(to);
        ft.setInterpolator(Interpolator.EASE_OUT);
        return ft;
    }

    private static TranslateTransition makeSlideY(Node n, int ms) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), n);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0));
        return tt;
    }

    private static ScaleTransition makeScale(Node n, int ms) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), n);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(Interpolator.EASE_OUT);
        return st;
    }
}
