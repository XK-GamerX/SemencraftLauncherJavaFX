package com.semencraft.semencraftlauncherjavafx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;
import javax.imageio.ImageIO;

public class MainApp extends Application {

    public static String FONT_FAMILY = null;

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
        applyStyles(scene);

        stage.setTitle("SemenCraft Launcher");
        stage.setScene(scene);
        stage.setMinWidth(920);
        stage.setMinHeight(620);
        stage.setResizable(true);
        configureApplicationIcons(stage);

        stage.centerOnScreen();
        stage.show();
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

    private void loadCustomFont() {
        try (InputStream is = MainApp.class.getResourceAsStream("assets/font.ttf")) {
            if (is != null) {
                Font f = Font.loadFont(is, 14);
                if (f != null) {
                    FONT_FAMILY = f.getFamily();
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            File file = new File("src/main/resources/com/semencraft/semencraftlauncherjavafx/assets/font.ttf");
            if (file.exists()) {
                Font loaded = Font.loadFont(file.toURI().toString(), 14);
                if (loaded != null) {
                    FONT_FAMILY = loaded.getFamily();
                }
            }
        } catch (Exception ignored) {
        }
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
        String fontPart = FONT_FAMILY != null
                ? "-fx-font-family: '" + FONT_FAMILY + "'; "
                : "";

        double screenHeight = Screen.getPrimary().getBounds().getHeight();
        double screenScale = clamp(screenHeight / 1080.0, 0.92, 1.25);

        double sceneScale = clamp(
                Math.min(scene.getWidth() / DESIGN_WIDTH, scene.getHeight() / DESIGN_HEIGHT),
                0.90,
                1.25
        );

        double scale = clamp(sceneScale * 0.70 + screenScale * 0.30, 0.90, 1.28);
        double fontSize = 14.0 * scale;

        scene.getRoot().setStyle(fontPart + "-fx-font-size: " + String.format(Locale.US, "%.2fpx;", fontSize));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.forLanguageTag("es-ES"));
        launch(args);
    }
}
