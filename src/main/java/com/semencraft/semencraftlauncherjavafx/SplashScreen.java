package com.semencraft.semencraftlauncherjavafx;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.InputStream;
import java.util.Locale;

/**
 * Ventanita chica, sin bordes, que se muestra apenas se abre la app (como en
 * Adobe) mientras el launcher principal termina de cargar (verificar Java,
 * traer el catalogo de modpacks, etc). Reutiliza la imagen "splash.png" de
 * assets/, con una barra de progreso minimalista y una franja de texto,
 * ambas DEBAJO de la imagen (no encima).
 */
final class SplashScreen {

    private static final double WIDTH = 460.0;
    private static final double CORNER_RADIUS = 14.0;
    private static final double BAR_HEIGHT = 3.0;
    private static final Duration FADE_DURATION = Duration.millis(280);

    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final Region progressFill = new Region();
    private final Label lblStatus = new Label("Cargando Launcher...");
    private double trackWidth;

    SplashScreen() {
        Image splashImage = loadSplashImage();

        ImageView imageView = new ImageView(splashImage);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(WIDTH);

        double imageHeight = splashImage != null && splashImage.getWidth() > 0
                ? WIDTH * (splashImage.getHeight() / splashImage.getWidth())
                : WIDTH * 9.0 / 16.0;

        trackWidth = WIDTH;

        // Barra de progreso: delgada, minimalista, pegada justo debajo de la imagen.
        StackPane progressTrack = new StackPane();
        progressTrack.setPrefSize(trackWidth, BAR_HEIGHT);
        progressTrack.setMinSize(trackWidth, BAR_HEIGHT);
        progressTrack.setMaxSize(trackWidth, BAR_HEIGHT);
        progressTrack.setAlignment(Pos.CENTER_LEFT);
        progressTrack.setStyle("-fx-background-color: rgba(255,255,255,0.10);");

        progressFill.setPrefSize(0, BAR_HEIGHT);
        progressFill.setMinSize(0, BAR_HEIGHT);
        progressFill.setMaxHeight(BAR_HEIGHT);
        progressFill.setStyle("-fx-background-color: linear-gradient(to right, #ffc65c, #ff7a3d);");
        progressTrack.getChildren().add(progressFill);

        // Franja de texto: fondo oscuro solido, debajo de la barra.
        lblStatus.setStyle(
                "-fx-text-fill: #b4aec9; -fx-font-size: 11px; -fx-font-family: 'Segoe UI';");
        HBox textBar = new HBox(lblStatus);
        textBar.setAlignment(Pos.CENTER);
        textBar.setPrefWidth(WIDTH);
        textBar.setMinWidth(WIDTH);
        textBar.setPadding(new Insets(9, 12, 9, 12));
        textBar.setStyle("-fx-background-color: #0b0a12;");

        VBox card = new VBox(imageView, progressTrack, textBar);
        card.setPrefWidth(WIDTH);
        card.setStyle("-fx-background-color: transparent;");

        double totalHeight = imageHeight + BAR_HEIGHT + 30.0;

        Rectangle clip = new Rectangle(WIDTH, totalHeight);
        clip.setArcWidth(CORNER_RADIUS * 2);
        clip.setArcHeight(CORNER_RADIUS * 2);
        card.setClip(clip);

        DropShadow windowShadow = new DropShadow();
        windowShadow.setColor(Color.rgb(0, 0, 0, 0.55));
        windowShadow.setRadius(28);
        windowShadow.setSpread(0.06);

        StackPane shadowWrapper = new StackPane(card);
        shadowWrapper.setStyle("-fx-background-color: transparent;");
        shadowWrapper.setEffect(windowShadow);
        shadowWrapper.setPadding(new Insets(24));

        Scene scene = new Scene(shadowWrapper, WIDTH + 48, totalHeight + 48);
        scene.setFill(Color.TRANSPARENT);

        stage.setScene(scene);
        stage.setResizable(false);
        stage.setAlwaysOnTop(true);
        stage.centerOnScreen();
    }

    void show() {
        Region root = (Region) stage.getScene().getRoot();
        root.setOpacity(0.0);
        stage.show();

        FadeTransition fadeIn = new FadeTransition(FADE_DURATION, root);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }

    void updateProgress(double progress, String status) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        String text = (status != null && !status.isBlank()) ? status : lblStatus.getText();
        lblStatus.setText(String.format(Locale.US, "%s (%.0f%%)", stripTrailingDots(text), clamped * 100.0));
        progressFill.setPrefWidth(Math.rint(trackWidth * clamped));
    }

    private static String stripTrailingDots(String text) {
        return text.endsWith("...") ? text.substring(0, text.length() - 3) : text;
    }

    void closeWithFade(Runnable onFinished) {
        FadeTransition fade = new FadeTransition(FADE_DURATION, stage.getScene().getRoot());
        fade.setFromValue(stage.getScene().getRoot().getOpacity());
        fade.setToValue(0.0);
        fade.setOnFinished(e -> {
            stage.close();
            if (onFinished != null) {
                onFinished.run();
            }
        });
        fade.play();
    }

    private static Image loadSplashImage() {
        try (InputStream is = SplashScreen.class.getResourceAsStream("assets/splash.png")) {
            if (is != null) {
                return new Image(is);
            }
        } catch (Exception ignored) {
        }
        try {
            java.io.File file = new java.io.File(
                    "src/main/resources/com/semencraft/semencraftlauncherjavafx/assets/splash.png");
            if (file.exists()) {
                return new Image(file.toURI().toString());
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
