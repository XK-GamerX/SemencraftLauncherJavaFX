package com.semencraft.semencraftlauncherjavafx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;
import javax.imageio.ImageIO;

public class MainApp extends Application {

    private static final String[] CERA_PRO_WEIGHTS = {"Light", "Medium", "Bold", "Black"};

    private static final double DESIGN_WIDTH = 1366.0;
    private static final double DESIGN_HEIGHT = 768.0;
    private static final String ASSET_FS_DIR = "src/main/resources/com/semencraft/semencraftlauncherjavafx/assets/";
    private static final String[] STAGE_ICON_CANDIDATES = {
            "circular-black.bmp",
            "circular-black.png",
            "icon.png"
    };
    private static final String[] TASKBAR_ICON_CANDIDATES = {
            "square-blue.bmp",
            "square-blue.png",
            "icon.png"
    };

    @Override
    public void start(Stage stage) throws Exception {
        // 1) Mostramos la ventanita splash (sin bordes) de inmediato, ANTES de
        // cargar el FXML/controlador del launcher. Platform.runLater() cede el
        // turno al hilo de JavaFX para que esta ventana se pinte en pantalla
        // antes de que arranque la carga pesada (que es sincronica).
        SplashScreen splash = new SplashScreen();
        splash.show();

        Platform.runLater(() -> initLauncher(stage, splash));
    }

    private void initLauncher(Stage stage, SplashScreen splash) {
        try {
            loadCustomFont();

            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("launcher.fxml"));
            Parent root = loader.load();

            LauncherController ctrl = loader.getController();
            ctrl.setStage(stage);

            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            double width = clamp(bounds.getWidth() * 0.88, 980, 1700);
            double height = clamp(bounds.getHeight() * 0.88, 640, 1050);

            stage.initStyle(StageStyle.UNDECORATED);

            Scene scene = new Scene(root, width, height);
            scene.setFill(Color.web("#0b0a12"));
            applyStyles(scene);

            stage.setTitle("Semencraft Launcher");
            stage.setScene(scene);
            stage.setMinWidth(920);
            stage.setMinHeight(620);
            // IMPORTANTE: con StageStyle.UNDECORATED, dejar resizable=true hace que
            // Windows reserve una franja invisible en los bordes (sobre todo arriba)
            // para el "agarre" nativo de resize, y esa franja se queda con los clics
            // ANTES de que lleguen a JavaFX. Eso es lo que estaba tapando los botones
            // de minimizar/maximizar/cerrar. Como no hay logica propia de resize por
            // bordes en este proyecto, lo desactivamos.
            stage.setResizable(false);
            configureApplicationIcons(stage);

            stage.centerOnScreen();

            // 2) El launcher recien se muestra (y el splash recien se cierra) cuando
            // la verificacion de Java y la carga del catalogo de modpacks terminan.
            // Mientras tanto, el progreso real se refleja en la ventanita splash.
            ctrl.setStartupProgressListener(new LauncherController.StartupProgressListener() {
                @Override
                public void onProgress(double progress, String status) {
                    splash.updateProgress(progress, status);
                }

                @Override
                public void onReady() {
                    // Animamos la opacidad de la VENTANA (Stage), no la del nodo raiz:
                    // asi el fade compone todo lo ya pintado a nivel de sistema y no
                    // se ve ningun flash blanco de por medio mientras sube la opacidad.
                    stage.setOpacity(0.0);
                    stage.show();
                    Timeline launcherFadeIn = new Timeline(
                            new KeyFrame(Duration.millis(320),
                                    new KeyValue(stage.opacityProperty(), 1.0))
                    );
                    launcherFadeIn.play();
                    splash.closeWithFade(null);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            splash.closeWithFade(null);
            stage.show();
        }
    }

    private static void configureApplicationIcons(Stage stage) {
        Image stageIcon = loadUiIconAsset(STAGE_ICON_CANDIDATES);
        if (stageIcon != null) {
            stage.getIcons().add(stageIcon);
        }
        applyTaskbarIcon();
    }

    private static Image loadUiIconAsset(String... names) {
        if (names == null) {
            return null;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            try (InputStream is = MainApp.class.getResourceAsStream("assets/" + name)) {
                if (is != null) {
                    Image image = new Image(is);
                    if (!image.isError()) {
                        return image;
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                File file = new File(ASSET_FS_DIR + name);
                if (file.exists()) {
                    Image image = new Image(file.toURI().toString());
                    if (!image.isError()) {
                        return image;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void applyTaskbarIcon() {
        try {
            if (!Taskbar.isTaskbarSupported()) {
                return;
            }
            Taskbar taskbar = Taskbar.getTaskbar();
            if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                return;
            }
            BufferedImage icon = loadAwtIconAsset(TASKBAR_ICON_CANDIDATES);
            if (icon != null) {
                taskbar.setIconImage(icon);
            }
        } catch (Exception ignored) {
        }
    }

    private static BufferedImage loadAwtIconAsset(String... names) {
        if (names == null) {
            return null;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            try (InputStream is = MainApp.class.getResourceAsStream("assets/" + name)) {
                if (is != null) {
                    BufferedImage image = ImageIO.read(is);
                    if (image != null) {
                        return image;
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                File file = new File(ASSET_FS_DIR + name);
                if (file.exists()) {
                    BufferedImage image = ImageIO.read(file);
                    if (image != null) {
                        return image;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Cera Pro en cuatro pesos. JavaFX las registra por nombre completo
     * (CeraPro-Medium, CeraPro-Bold...), que es como las referencia style.css.
     */
    private void loadCustomFont() {
        for (String weight : CERA_PRO_WEIGHTS) {
            loadFontFamily("fonts/CeraPro-" + weight + ".ttf", 14);
        }
        // Fuente pixelada de siempre ("Minecraft"): libro de notificaciones y avisos.
        loadFontFamily("font.ttf", 14);
        // "Minecraft Ten": titulos y botones principales.
        loadFontFamily("font2.ttf", 14);
    }

    private static String loadFontFamily(String assetName, double size) {
        try (InputStream is = MainApp.class.getResourceAsStream("assets/" + assetName)) {
            if (is != null) {
                Font loaded = Font.loadFont(is, size);
                if (loaded != null) {
                    return loaded.getFamily();
                }
            }
        } catch (Exception ignored) {
        }

        try {
            File file = new File("src/main/resources/com/semencraft/semencraftlauncherjavafx/assets/" + assetName);
            if (file.exists()) {
                Font loaded = Font.loadFont(file.toURI().toString(), size);
                if (loaded != null) {
                    return loaded.getFamily();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static void applyStyles(Scene scene) {
        URL css = MainApp.class.getResource("style.css");
        if (css != null) {
            String cssUrl = css.toExternalForm();
            if (!scene.getStylesheets().contains(cssUrl)) {
                scene.getStylesheets().add(cssUrl);
            }
        }

        applyRootFont(scene);
        scene.widthProperty().addListener((obs, oldV, newV) -> applyRootFont(scene));
        scene.heightProperty().addListener((obs, oldV, newV) -> applyRootFont(scene));
    }

    private static void applyRootFont(Scene scene) {

        double screenHeight = Screen.getPrimary().getBounds().getHeight();
        double screenScale = clamp(screenHeight / 1080.0, 0.92, 1.25);

        double sceneScale = clamp(
                Math.min(scene.getWidth() / DESIGN_WIDTH, scene.getHeight() / DESIGN_HEIGHT),
                0.90,
                1.25
        );

        double scale = clamp(sceneScale * 0.70 + screenScale * 0.30, 0.90, 1.28);
        double fontSize = 14.0 * scale;

        scene.getRoot().setStyle("-fx-font-size: " + String.format(Locale.US, "%.2fpx;", fontSize));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.forLanguageTag("es-ES"));
        launch(args);
    }
}
