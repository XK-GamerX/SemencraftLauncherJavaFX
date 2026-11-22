package com.semencraft.semencraftlauncherjavafx;

import javafx.animation.AnimationTimer;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.ScrollEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Scroll suave y mas rapido para todos los ScrollPane del launcher.
 * <p>
 * JavaFX ya respeta las "lineas por muesca" que el usuario elige en Windows (Configuracion > Mouse),
 * pero mueve muy pocos pixeles por linea. Aqui cada linea vale {@link #PIXELS_PER_LINE} pixeles
 * (parecido a un navegador) y el movimiento se anima para que no salte.
 */
final class SmoothScroll {
    private static final double PIXELS_PER_LINE = 45.0;
    /** Fraccion de la distancia pendiente que se recorre por cada cuadro (60 fps). */
    private static final double EASING = 0.22;

    private static final Map<ScrollPane, Motion> MOTIONS = new WeakHashMap<>();

    private SmoothScroll() {
    }

    static void install(Scene scene) {
        if (scene == null || Boolean.TRUE.equals(scene.getProperties().get("sc-smooth-scroll"))) {
            return;
        }
        scene.getProperties().put("sc-smooth-scroll", Boolean.TRUE);
        scene.addEventFilter(ScrollEvent.SCROLL, SmoothScroll::onScroll);
    }

    private static void onScroll(ScrollEvent event) {
        if (event.getDeltaY() == 0 || event.isShiftDown() || event.isControlDown() || !(event.getTarget() instanceof Node target)) {
            return;
        }
        double pixels = switch (event.getTextDeltaYUnits()) {
            case LINES -> event.getTextDeltaY() * PIXELS_PER_LINE;
            case PAGES -> Double.NaN;
            default -> event.getDeltaY() * 2.5;
        };

        for (Node node = target; node != null; node = node.getParent()) {
            // Las listas y los cuadros de texto ya hacen su propio scroll.
            if (node instanceof VirtualFlow<?> || node instanceof ListView<?> || node instanceof TextArea) {
                return;
            }
            if (node instanceof ScrollPane pane && canScroll(pane, event.getDeltaY())) {
                double viewport = pane.getViewportBounds().getHeight();
                double amount = Double.isNaN(pixels) ? Math.signum(event.getTextDeltaY()) * viewport * 0.9 : pixels;
                scrollBy(pane, -amount);
                event.consume();
                return;
            }
        }
    }

    /** Si el ScrollPane todavia puede moverse en esa direccion (si no, el scroll pasa al de afuera). */
    private static boolean canScroll(ScrollPane pane, double deltaY) {
        if (scrollableHeight(pane) <= 0.5) {
            return false;
        }
        Motion motion = MOTIONS.get(pane);
        double value = motion != null && motion.running ? motion.target : pane.getVvalue();
        return deltaY > 0 ? value > pane.getVmin() + 1e-6 : value < pane.getVmax() - 1e-6;
    }

    private static double scrollableHeight(ScrollPane pane) {
        Node content = pane.getContent();
        if (content == null) {
            return 0;
        }
        return content.getLayoutBounds().getHeight() - pane.getViewportBounds().getHeight();
    }

    private static void scrollBy(ScrollPane pane, double pixels) {
        double range = scrollableHeight(pane);
        if (range <= 0) {
            return;
        }
        Motion motion = MOTIONS.computeIfAbsent(pane, Motion::new);
        double span = pane.getVmax() - pane.getVmin();
        double start = motion.running ? motion.target : pane.getVvalue();
        motion.target = clamp(start + pixels / range * span, pane.getVmin(), pane.getVmax());
        motion.start();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Lleva el vvalue hacia el destino con una curva suave; se detiene sola al llegar. */
    private static final class Motion extends AnimationTimer {
        private final ScrollPane pane;
        private double target;
        private boolean running;
        private boolean applying;
        private long lastFrame;

        Motion(ScrollPane pane) {
            this.pane = pane;
            // Si el usuario arrastra la barra o el codigo mueve el scroll, se cancela la animacion.
            pane.vvalueProperty().addListener((obs, oldValue, newValue) -> {
                if (running && !applying) {
                    stop();
                }
            });
        }

        @Override
        public void start() {
            if (!running) {
                running = true;
                lastFrame = 0;
                super.start();
            }
        }

        @Override
        public void stop() {
            running = false;
            super.stop();
        }

        @Override
        public void handle(long now) {
            double frames = lastFrame == 0 ? 1.0 : Math.min(4.0, (now - lastFrame) / 16_666_667.0);
            lastFrame = now;
            double current = pane.getVvalue();
            double remaining = target - current;
            double range = scrollableHeight(pane);
            if (range <= 0 || Math.abs(remaining) * range < 0.5) {
                applying = true;
                pane.setVvalue(target);
                applying = false;
                stop();
                return;
            }
            double step = 1.0 - Math.pow(1.0 - EASING, frames);
            applying = true;
            pane.setVvalue(current + remaining * step);
            applying = false;
        }
    }
}
