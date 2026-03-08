package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class DiscordRpcService {

    static final String LARGE_ASSET_KEY = "icon";
    static final String SMALL_ASSET_PLACEHOLDER_KEY = "icon";
    private static final String SMALL_ASSET_ENV = "SEMENCRAFT_DISCORD_SMALL_ASSET_KEY";

    private static final String APP_ID_ENV = "SEMENCRAFT_DISCORD_APP_ID";
    private static final String FALLBACK_APP_ID = "1335743218528292947";

    private static final int OPCODE_HANDSHAKE = 0;
    private static final int OPCODE_FRAME = 1;

    enum LauncherRpcState {
        IDLE,
        SETTINGS,
        INSTALLING,
        LAUNCHING,
        PLAYING
    }

    private final Gson gson = new Gson();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Object workerLock = new Object();
    private final String appId;
    private final long sessionStartTimestamp = System.currentTimeMillis() / 1000L;

    private Thread workerThread;
    private RandomAccessFile pipe;
    private PresenceData desiredPresence;
    private PresenceData sentPresence;

    DiscordRpcService() {
        this.appId = resolveAppId();
    }

    boolean hasConfiguredAppId() {
        return !appId.isBlank();
    }

    static String appIdEnvironmentVariable() {
        return APP_ID_ENV;
    }

    private static String resolveAppId() {
        String fromEnvironment = System.getenv(APP_ID_ENV);
        if (isLikelyDiscordAppId(fromEnvironment)) {
            return fromEnvironment.trim();
        }
        if (isLikelyDiscordAppId(FALLBACK_APP_ID)) {
            return FALLBACK_APP_ID.trim();
        }
        return "";
    }

    private static boolean isLikelyDiscordAppId(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 17 || trimmed.length() > 20) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    void start() {
        if (appId.isBlank() || started.getAndSet(true)) {
            return;
        }
        workerThread = new Thread(this::workerLoop, "discord-rpc-ipc");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    void updatePresence(LauncherRpcState state, String details, String username) {
        if (!started.get()) {
            return;
        }
        PresenceData next = new PresenceData(
                state == null ? LauncherRpcState.IDLE : state,
                normalizeDetails(details),
                normalizeUser(username)
        );
        synchronized (workerLock) {
            desiredPresence = next;
            workerLock.notifyAll();
        }
    }

    void shutdown() {
        if (!started.getAndSet(false)) {
            return;
        }
        synchronized (workerLock) {
            workerLock.notifyAll();
        }
        Thread thread = workerThread;
        workerThread = null;
        if (thread != null) {
            thread.interrupt();
        }
        clearPresenceSafe();
        closePipe();
    }

    private void workerLoop() {
        while (started.get()) {
            try {
                ensureConnected();
                if (pipe == null) {
                    waitQuietly(2200L);
                    continue;
                }

                PresenceData next;
                synchronized (workerLock) {
                    next = desiredPresence;
                }
                if (next != null && !next.equals(sentPresence)) {
                    sendPresence(next);
                    sentPresence = next;
                }
                waitQuietly(1400L);
            } catch (Exception ignored) {
                closePipe();
                sentPresence = null;
                waitQuietly(2600L);
            }
        }
        closePipe();
    }

    private void ensureConnected() {
        if (pipe != null) {
            return;
        }
        for (int i = 0; i <= 9; i++) {
            if (!started.get()) {
                return;
            }
            String pipePath = "\\\\.\\pipe\\discord-ipc-" + i;
            RandomAccessFile candidate = null;
            try {
                candidate = new RandomAccessFile(pipePath, "rw");
                sendHandshake(candidate);
                pipe = candidate;
                sentPresence = null;
                return;
            } catch (FileNotFoundException ignored) {
            } catch (Exception ex) {
            } finally {
                if (pipe == null && candidate != null) {
                    try {
                        candidate.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private void sendHandshake(RandomAccessFile channel) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        payload.addProperty("client_id", appId);
        writePacket(channel, OPCODE_HANDSHAKE, gson.toJson(payload));
    }

    private void sendPresence(PresenceData data) throws IOException {
        JsonObject activity = new JsonObject();
        activity.addProperty("state", stateText(data.state(), data.username()));
        activity.addProperty("details", data.details());

        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", sessionStartTimestamp);
        activity.add("timestamps", timestamps);

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", LARGE_ASSET_KEY);
        assets.addProperty("large_text", "SemenCraft Launcher");
        String smallAssetKey = expectedSmallAssetKey(data.state());
        if (smallAssetKey.isBlank()) {
            smallAssetKey = SMALL_ASSET_PLACEHOLDER_KEY;
        }
        assets.addProperty("small_image", smallAssetKey);
        assets.addProperty("small_text", smallAssetText(data.state()));
        activity.add("assets", assets);

        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);

        JsonObject frame = new JsonObject();
        frame.addProperty("cmd", "SET_ACTIVITY");
        frame.add("args", args);
        frame.addProperty("nonce", UUID.randomUUID().toString());

        writePacket(pipe, OPCODE_FRAME, gson.toJson(frame));
    }

    private void clearPresenceSafe() {
        if (pipe == null) {
            return;
        }
        try {
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", JsonNull.INSTANCE);

            JsonObject frame = new JsonObject();
            frame.addProperty("cmd", "SET_ACTIVITY");
            frame.add("args", args);
            frame.addProperty("nonce", UUID.randomUUID().toString());
            writePacket(pipe, OPCODE_FRAME, gson.toJson(frame));
        } catch (Exception ignored) {
        }
    }

    private static void writePacket(RandomAccessFile channel, int opcode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        writeLeInt(channel, opcode);
        writeLeInt(channel, bytes.length);
        channel.write(bytes);
    }

    private static void writeLeInt(RandomAccessFile channel, int value) throws IOException {
        channel.write(value & 0xFF);
        channel.write((value >>> 8) & 0xFF);
        channel.write((value >>> 16) & 0xFF);
        channel.write((value >>> 24) & 0xFF);
    }

    private void closePipe() {
        RandomAccessFile channel = pipe;
        pipe = null;
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (Exception ignored) {
        }
    }

    private void waitQuietly(long millis) {
        synchronized (workerLock) {
            if (!started.get()) {
                return;
            }
            try {
                workerLock.wait(millis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String normalizeDetails(String details) {
        if (details == null || details.isBlank()) {
            return "Semencraft Launcher";
        }
        String trimmed = details.trim();
        if (trimmed.length() <= 120) {
            return trimmed;
        }
        return trimmed.substring(0, 117) + "...";
    }

    private static String normalizeUser(String username) {
        if (username == null) {
            return "";
        }
        String trimmed = username.trim();
        if (trimmed.isBlank() || "--".equals(trimmed) || "-".equals(trimmed)) {
            return "";
        }
        return trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 24);
    }

    private static String stateText(LauncherRpcState state, String username) {
        String base = switch (state) {
            case IDLE -> "En launcher";
            case SETTINGS -> "En configuracion";
            case INSTALLING -> "Instalando Minecraft";
            case LAUNCHING -> "Abriendo Minecraft";
            case PLAYING -> "Minecraft en ejecucion";
        };
        if (username.isBlank()) {
            return base;
        }
        return base + " | " + username;
    }

    static String expectedSmallAssetKey(LauncherRpcState state) {
        String configured = normalizeAssetKey(System.getenv(SMALL_ASSET_ENV));
        if (configured != null) {
            return configured;
        }
        LauncherRpcState safeState = state == null ? LauncherRpcState.IDLE : state;
        return safeState.name().toLowerCase(Locale.ROOT);
    }

    private static String smallAssetText(LauncherRpcState state) {
        LauncherRpcState safeState = state == null ? LauncherRpcState.IDLE : state;
        return switch (safeState) {
            case IDLE -> "En launcher";
            case SETTINGS -> "Configurando";
            case INSTALLING -> "Instalando";
            case LAUNCHING -> "Iniciando Minecraft";
            case PLAYING -> "Jugando";
        };
    }

    static String[] expectedSmallAssetKeys() {
        return new String[]{
                LauncherRpcState.IDLE.name().toLowerCase(Locale.ROOT),
                LauncherRpcState.SETTINGS.name().toLowerCase(Locale.ROOT),
                LauncherRpcState.INSTALLING.name().toLowerCase(Locale.ROOT),
                LauncherRpcState.LAUNCHING.name().toLowerCase(Locale.ROOT),
                LauncherRpcState.PLAYING.name().toLowerCase(Locale.ROOT)
        };
    }

    private static String normalizeAssetKey(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || trimmed.length() > 300) {
            return null;
        }
        return trimmed;
    }

    private record PresenceData(LauncherRpcState state, String details, String username) {
    }
}
