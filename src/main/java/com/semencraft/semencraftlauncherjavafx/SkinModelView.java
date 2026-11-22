package com.semencraft.semencraftlauncherjavafx;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/**
 * Modelo 3D del jugador a partir de la skin (64x64 o 64x32), como en otros launchers.
 * Pensado para PCs modestas: dos mallas de pocos triangulos, sin sombras, y el giro
 * automatico solo corre cuando se pide (se apaga en "Optimizar launcher").
 */
final class SkinModelView extends StackPane {

    private static final int TEXTURE_SCALE = 8;
    private static final double MAX_TILT = 25.0;

    private final Rotate yaw = new Rotate(-28, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(-8, Rotate.X_AXIS);
    private final Timeline spin;
    private final SubScene subScene;
    private final double aspect;
    private double dragStartX;
    private double dragStartY;
    private double dragStartYaw;
    private double dragStartPitch;
    private boolean animated;

    static boolean isSupported() {
        return Platform.isSupported(ConditionalFeature.SCENE3D);
    }

    SkinModelView(Image skin, boolean slim, double width, double height, boolean lightweight) {
        boolean legacy = skin.getHeight() < 64;
        PhongMaterial material = new PhongMaterial(Color.WHITE);
        material.setDiffuseMap(pixelScaled(skin, TEXTURE_SCALE));
        material.setSpecularColor(Color.BLACK);

        TriangleMesh base = new TriangleMesh();
        TriangleMesh overlay = new TriangleMesh();
        double texW = skin.getWidth();
        double texH = skin.getHeight();
        double armWidth = slim ? 3 : 4;

        // Cabeza y sombrero
        addBox(base, -4, -16, -4, 8, 8, 8, 0, 0, 0, texW, texH);
        addBox(overlay, -4, -16, -4, 8, 8, 8, 32, 0, 0.5, texW, texH);
        // Torso
        addBox(base, -4, -8, -2, 8, 12, 4, 16, 16, 0, texW, texH);
        // Brazo derecho del jugador (a la izquierda de quien mira) y brazo izquierdo
        addBox(base, -4 - armWidth, -8, -2, armWidth, 12, 4, 40, 16, 0, texW, texH);
        addBox(base, 4, -8, -2, armWidth, 12, 4, legacy ? 40 : 32, legacy ? 16 : 48, 0, texW, texH);
        // Piernas
        addBox(base, -4, 4, -2, 4, 12, 4, 0, 16, 0, texW, texH);
        addBox(base, 0, 4, -2, 4, 12, 4, legacy ? 0 : 16, legacy ? 16 : 48, 0, texW, texH);
        if (!legacy) {
            // Segunda capa: chaqueta, mangas y pantalon
            addBox(overlay, -4, -8, -2, 8, 12, 4, 16, 32, 0.25, texW, texH);
            addBox(overlay, -4 - armWidth, -8, -2, armWidth, 12, 4, 40, 32, 0.25, texW, texH);
            addBox(overlay, 4, -8, -2, armWidth, 12, 4, 48, 48, 0.25, texW, texH);
            addBox(overlay, -4, 4, -2, 4, 12, 4, 0, 32, 0.25, texW, texH);
            addBox(overlay, 0, 4, -2, 4, 12, 4, 0, 48, 0.25, texW, texH);
        }

        MeshView baseView = new MeshView(base);
        baseView.setMaterial(material);
        baseView.setCullFace(CullFace.BACK);
        MeshView overlayView = new MeshView(overlay);
        overlayView.setMaterial(material);
        overlayView.setCullFace(CullFace.BACK);

        Group model = new Group(baseView, overlayView);
        model.getTransforms().addAll(pitch, yaw);

        AmbientLight ambient = new AmbientLight(Color.gray(0.78));
        PointLight key = new PointLight(Color.gray(0.38));
        key.setTranslateX(-40);
        key.setTranslateY(-50);
        key.setTranslateZ(-80);
        Group world = new Group(model, ambient, key);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setFieldOfView(26);
        camera.setNearClip(1);
        camera.setFarClip(500);
        camera.setTranslateZ(-82);

        aspect = width / height;
        subScene = new SubScene(world, width, height, true,
                lightweight ? SceneAntialiasing.DISABLED : SceneAntialiasing.BALANCED);
        subScene.setFill(Color.TRANSPARENT);
        subScene.setCamera(camera);

        getChildren().add(subScene);
        setViewerHeight(height);

        spin = new Timeline(new KeyFrame(Duration.seconds(14),
                new KeyValue(yaw.angleProperty(), yaw.getAngle() + 360, Interpolator.LINEAR)));
        spin.setCycleCount(Animation.INDEFINITE);

        setOnMousePressed(event -> {
            spin.pause();
            dragStartX = event.getSceneX();
            dragStartY = event.getSceneY();
            dragStartYaw = yaw.getAngle();
            dragStartPitch = pitch.getAngle();
        });
        setOnMouseDragged(event -> {
            yaw.setAngle(dragStartYaw - (event.getSceneX() - dragStartX) * 0.6);
            double tilt = dragStartPitch + (event.getSceneY() - dragStartY) * 0.3;
            pitch.setAngle(Math.max(-MAX_TILT, Math.min(MAX_TILT, tilt)));
        });
        setOnMouseReleased(event -> {
            if (animated) {
                restartSpinFromCurrentAngle();
            }
        });
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                spin.stop();
            }
        });
    }

    /** Cambia el alto (el ancho sigue la proporcion); la camara encuadra igual al modelo. */
    void setViewerHeight(double height) {
        double width = Math.round(height * aspect);
        subScene.setWidth(width);
        subScene.setHeight(height);
        setPrefSize(width, height);
        setMinSize(width, height);
        setMaxSize(width, height);
    }

    /** Giro automatico lento; se pausa al arrastrar y al salir de la vista. */
    void setAnimated(boolean animated) {
        this.animated = animated;
        if (animated) {
            restartSpinFromCurrentAngle();
        } else {
            spin.stop();
        }
    }

    private void restartSpinFromCurrentAngle() {
        spin.stop();
        spin.getKeyFrames().setAll(new KeyFrame(Duration.seconds(14),
                new KeyValue(yaw.angleProperty(), yaw.getAngle() + 360, Interpolator.LINEAR)));
        spin.playFromStart();
    }

    /**
     * JavaFX filtra las texturas de forma lineal: se amplia la skin con vecino mas
     * cercano para que los pixeles se vean nitidos.
     */
    static Image pixelScaled(Image skin, int scale) {
        int w = (int) skin.getWidth();
        int h = (int) skin.getHeight();
        WritableImage scaled = new WritableImage(w * scale, h * scale);
        PixelReader reader = skin.getPixelReader();
        PixelWriter writer = scaled.getPixelWriter();
        int[] row = new int[w * scale];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = reader.getArgb(x, y);
                for (int i = 0; i < scale; i++) {
                    row[x * scale + i] = argb;
                }
            }
            for (int i = 0; i < scale; i++) {
                writer.setPixels(0, y * scale + i, row.length, 1,
                        javafx.scene.image.PixelFormat.getIntArgbInstance(), row, 0, row.length);
            }
        }
        return scaled;
    }

    /**
     * Caja con el mapeo UV estandar de Minecraft. El jugador mira hacia -Z (la camara).
     * (u, v) es la esquina de la caja en la textura; inflate agranda la capa exterior.
     */
    private static void addBox(TriangleMesh mesh, double x, double y, double z, double w, double h, double d,
                               double u, double v, double inflate, double texW, double texH) {
        double x0 = x - inflate;
        double x1 = x + w + inflate;
        double y0 = y - inflate;
        double y1 = y + h + inflate;
        double z0 = z - inflate;
        double z1 = z + d + inflate;

        // Cada cara: esquinas TL, TR, BL, BR vistas desde afuera + rectangulo en la textura.
        addFace(mesh, x0, y0, z0, x1, y0, z0, x0, y1, z0, x1, y1, z0,
                u + d, v + d, w, h, texW, texH);                       // frente
        addFace(mesh, x1, y0, z1, x0, y0, z1, x1, y1, z1, x0, y1, z1,
                u + d + w + d, v + d, w, h, texW, texH);               // espalda
        addFace(mesh, x0, y0, z1, x0, y0, z0, x0, y1, z1, x0, y1, z0,
                u, v + d, d, h, texW, texH);                           // lado derecho del jugador
        addFace(mesh, x1, y0, z0, x1, y0, z1, x1, y1, z0, x1, y1, z1,
                u + d + w, v + d, d, h, texW, texH);                   // lado izquierdo del jugador
        addFace(mesh, x0, y0, z1, x1, y0, z1, x0, y0, z0, x1, y0, z0,
                u + d, v, w, d, texW, texH);                           // arriba
        addFace(mesh, x0, y1, z0, x1, y1, z0, x0, y1, z1, x1, y1, z1,
                u + d + w, v, w, d, texW, texH);                       // abajo
    }

    private static void addFace(TriangleMesh mesh,
                                double tlx, double tly, double tlz,
                                double trx, double try_, double trz,
                                double blx, double bly, double blz,
                                double brx, double bry, double brz,
                                double tu, double tv, double tw, double th,
                                double texW, double texH) {
        int p = mesh.getPoints().size() / 3;
        mesh.getPoints().addAll(
                (float) tlx, (float) tly, (float) tlz,
                (float) trx, (float) try_, (float) trz,
                (float) blx, (float) bly, (float) blz,
                (float) brx, (float) bry, (float) brz
        );
        // Pequeno margen para que el filtrado no tome pixeles vecinos.
        double inset = 0.02;
        float u0 = (float) ((tu + inset) / texW);
        float u1 = (float) ((tu + tw - inset) / texW);
        float v0 = (float) ((tv + inset) / texH);
        float v1 = (float) ((tv + th - inset) / texH);
        int t = mesh.getTexCoords().size() / 2;
        mesh.getTexCoords().addAll(u0, v0, u1, v0, u0, v1, u1, v1);
        // Dos triangulos por cara (orden antihorario visto desde afuera).
        mesh.getFaces().addAll(
                p, t, p + 2, t + 2, p + 1, t + 1,
                p + 1, t + 1, p + 2, t + 2, p + 3, t + 3
        );
    }
}
