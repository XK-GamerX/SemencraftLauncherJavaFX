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
import javafx.scene.effect.GaussianBlur;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Locale;
import java.util.function.Consumer;

public class LoginController {

    @FXML private StackPane loginRoot;
    @FXML private Region loginBackdrop;
    @FXML private ScrollPane loginScroll;
    @FXML private VBox loginOuter;
    @FXML private VBox loginCard;
    @FXML private ImageView imgLoginLogo;
    @FXML private TextField txtUsername;
    @FXML private TextField txtPassword;
    @FXML private Label lblError;
    @FXML private Button btnLogin;

    private static final String DEMO_PASSWORD = "123";
    private Consumer<String> onSuccess;
    private String passwordValue = "";

    public void setOnSuccess(Consumer<String> cb) {
        this.onSuccess = cb;
    }

    @FXML
    public void initialize() {
        var logo = LauncherController.loadAsset("title.png");
        if (logo != null) {
            imgLoginLogo.setImage(logo);
            imgLoginLogo.setEffect(null);
        }
        blurLoginBackdrop();
        configurePasswordMask();

        bindResponsiveSizes();

        txtUsername.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                txtPassword.requestFocus();
            }
        });
        txtPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onLogin();
            }
        });

        animateCardIn();
    }

    private void bindResponsiveSizes() {
        DoubleBinding cardWidth = clamp(loginRoot.widthProperty().multiply(0.42), 320, 560);
        loginCard.prefWidthProperty().bind(cardWidth);
        loginCard.maxWidthProperty().bind(cardWidth);
        loginCard.minWidthProperty().bind(clamp(loginRoot.widthProperty().multiply(0.30), 300, 420));

        imgLoginLogo.fitWidthProperty().bind(clamp(loginCard.widthProperty().multiply(0.58), 130, 300));

        txtUsername.prefHeightProperty().bind(clamp(loginRoot.heightProperty().multiply(0.072), 44, 58));
        txtPassword.prefHeightProperty().bind(txtUsername.prefHeightProperty());
        btnLogin.prefHeightProperty().bind(clamp(loginRoot.heightProperty().multiply(0.074), 48, 60));

        loginOuter.minHeightProperty().bind(loginRoot.heightProperty());
        loginOuter.prefWidthProperty().bind(loginRoot.widthProperty());

        if (loginScroll != null) {
            loginScroll.setPannable(true);
        }

        loginRoot.widthProperty().addListener((obs, oldV, newV) -> updateOuterPadding());
        loginRoot.heightProperty().addListener((obs, oldV, newV) -> updateOuterPadding());
        Platform.runLater(this::updateOuterPadding);
    }

    private void updateOuterPadding() {
        double h = loginRoot.getHeight();
        double w = loginRoot.getWidth();

        double vertical = clamp(h * 0.08, 18, 72);
        double horizontal = clamp(w * 0.02, 12, 28);

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
        loginBackdrop.setEffect(new GaussianBlur(84));
        loginBackdrop.setCache(true);
        loginBackdrop.setScaleX(1.16);
        loginBackdrop.setScaleY(1.16);
    }

    private void configurePasswordMask() {
        txtPassword.setTextFormatter(new javafx.scene.control.TextFormatter<>(change -> {
            int oldLen = passwordValue.length();
            int start = Math.max(0, Math.min(change.getRangeStart(), oldLen));
            int end = Math.max(start, Math.min(change.getRangeEnd(), oldLen));
            String inserted = change.getText() == null ? "" : change.getText();

            StringBuilder updated = new StringBuilder(passwordValue);
            updated.replace(start, end, inserted);
            passwordValue = updated.toString();

            String masked = "*".repeat(passwordValue.length());
            change.setRange(0, change.getControlText().length());
            change.setText(masked);
            change.setCaretPosition(masked.length());
            change.setAnchor(masked.length());
            return change;
        }));
    }

    public void resetAndAnimate() {
        txtUsername.clear();
        txtPassword.clear();
        passwordValue = "";
        lblError.setText("");
        lblError.setOpacity(0);
        btnLogin.setDisable(false);
        animateCardIn();
    }

    @FXML
    private void onLogin() {
        String user = txtUsername.getText().trim();
        String pass = passwordValue;
        lblError.setText("");

        if (user.isEmpty()) {
            showError("Introduce tu usuario.");
            shake(txtUsername);
            return;
        }
        if (pass.isEmpty()) {
            showError("Introduce tu contrasena.");
            shake(txtPassword);
            return;
        }
        if (!pass.equals(DEMO_PASSWORD)) {
            showError("Contrasena incorrecta.");
            shake(txtPassword);
            return;
        }

        btnLogin.setDisable(true);
        if (onSuccess != null) {
            onSuccess.accept(user);
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
        LauncherController.fade(lblError, 1.0, 200).play();
        LauncherController.delay(3200, () -> LauncherController.fade(lblError, 0.0, 400).play());
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
