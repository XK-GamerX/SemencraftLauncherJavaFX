package com.semencraft.semencraftlauncherjavafx;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sonido de notificacion (assets/notification.wav). Se reproduce en un hilo
 * propio para no frenar la interfaz; el WAV se lee del jar una sola vez.
 */
final class NotificationSound {

    private static final ExecutorService PLAYER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "notification-sound");
        thread.setDaemon(true);
        return thread;
    });
    private static final long MIN_GAP_MS = 900L;

    private static volatile byte[] wavBytes;
    private static volatile long lastPlayedAt;

    private NotificationSound() {
    }

    static void play() {
        long now = System.currentTimeMillis();
        if (now - lastPlayedAt < MIN_GAP_MS) {
            return;
        }
        lastPlayedAt = now;
        PLAYER.execute(NotificationSound::playBlocking);
    }

    private static void playBlocking() {
        byte[] bytes = loadBytes();
        if (bytes == null || bytes.length == 0) {
            return;
        }
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
            Clip clip = AudioSystem.getClip();
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.open(audio);
            clip.start();
        } catch (Exception ignored) {
            // Sin dispositivo de audio: la notificacion se sigue mostrando igual.
        }
    }

    private static byte[] loadBytes() {
        byte[] cached = wavBytes;
        if (cached != null) {
            return cached;
        }
        try (InputStream in = NotificationSound.class.getResourceAsStream("assets/notification.wav")) {
            if (in != null) {
                cached = in.readAllBytes();
                wavBytes = cached;
            }
        } catch (Exception ignored) {
        }
        return cached;
    }
}
