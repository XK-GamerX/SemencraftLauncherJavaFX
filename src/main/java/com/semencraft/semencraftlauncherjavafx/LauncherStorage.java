package com.semencraft.semencraftlauncherjavafx;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LauncherStorage {

    private static final String APP_DIR = ".semencraft";
    private static final String LAUNCHER_DIR = "launcher";
    private static final String MINECRAFT_DIR = "minecraft";
    private static final String CREDENTIALS_FILE = "credentials.dat";
    private static final String CONFIG_FILE = "config.json";
    private static final String MACHINE_SECRET = "SemencraftLauncher::v1::sealed";
    private static final SecureRandom RNG = new SecureRandom();

    private LauncherStorage() {
    }

    static void ensureEnvironment() {
        try {
            ensureDirectoriesOnly();
            if (!Files.exists(configPath())) {
                Files.writeString(
                        configPath(),
                        LauncherConfig.defaults().toJson(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
            }
        } catch (Exception ignored) {
        }
    }

    static LauncherConfig loadConfig() {
        ensureEnvironment();
        Path path = configPath();
        try {
            if (!Files.exists(path)) {
                return LauncherConfig.defaults();
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return LauncherConfig.fromJson(json);
        } catch (Exception ignored) {
            return LauncherConfig.defaults();
        }
    }

    static void saveConfig(LauncherConfig config) {
        ensureDirectoriesOnly();
        LauncherConfig safeConfig = config == null ? LauncherConfig.defaults() : config;
        try {
            Files.writeString(
                    configPath(),
                    safeConfig.toJson(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception ignored) {
        }
    }

    static void saveCredentials(String username) {
        ensureDirectoriesOnly();
        if (username == null || username.isBlank()) {
            return;
        }
        String payload = "{\"username\":\"" + jsonEscape(username.trim()) + "\",\"savedAt\":" + System.currentTimeMillis() + "}";
        try {
            String encrypted = encrypt(payload);
            Files.writeString(
                    credentialsPath(),
                    encrypted,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception ignored) {
        }
    }

    static String loadSavedUsername() {
        ensureDirectoriesOnly();
        Path path = credentialsPath();
        try {
            if (!Files.exists(path)) {
                return null;
            }
            String encoded = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (encoded.isEmpty()) {
                return null;
            }
            String decrypted = decrypt(encoded);
            if (decrypted == null || decrypted.isBlank()) {
                return null;
            }
            String user = extractString(decrypted, "username", "");
            return user.isBlank() ? null : user;
        } catch (Exception ignored) {
            return null;
        }
    }

    static Path appRootDirectory() {
        ensureDirectoriesOnly();
        return rootPath();
    }

    static Path launcherDirectory() {
        ensureDirectoriesOnly();
        return launcherPath();
    }

    static Path minecraftDirectory() {
        ensureDirectoriesOnly();
        return minecraftPath();
    }

    private static void ensureDirectoriesOnly() {
        try {
            Files.createDirectories(launcherPath());
            Files.createDirectories(minecraftPath());
        } catch (Exception ignored) {
        }
    }

    private static Path rootPath() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, APP_DIR);
        }
        String userHome = System.getProperty("user.home", ".");
        return Path.of(userHome, "AppData", "Roaming", APP_DIR);
    }

    private static Path launcherPath() {
        return rootPath().resolve(LAUNCHER_DIR);
    }

    private static Path minecraftPath() {
        return rootPath().resolve(MINECRAFT_DIR);
    }

    private static Path configPath() {
        return launcherPath().resolve(CONFIG_FILE);
    }

    private static Path credentialsPath() {
        return launcherPath().resolve(CREDENTIALS_FILE);
    }

    private static String encrypt(String plain) throws Exception {
        byte[] iv = new byte[12];
        RNG.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
    }

    private static String decrypt(String encoded) throws Exception {
        String[] parts = encoded.split("\\.", 2);
        if (parts.length != 2) {
            return null;
        }
        byte[] iv = Base64.getDecoder().decode(parts[0]);
        byte[] cipherBytes = Base64.getDecoder().decode(parts[1]);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(cipherBytes);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static SecretKeySpec key() throws Exception {
        String machine = System.getenv("COMPUTERNAME");
        String seed = MACHINE_SECRET + "::" + System.getProperty("user.name", "user") + "::" +
                System.getProperty("os.name", "os") + "::" + (machine == null ? "pc" : machine);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
        byte[] aesKey = new byte[16];
        System.arraycopy(keyBytes, 0, aesKey, 0, aesKey.length);
        return new SecretKeySpec(aesKey, "AES");
    }

    private static String extractString(String json, String key, String fallback) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return fallback;
        }
        return jsonUnescape(m.group(1));
    }

    private static int extractInt(String json, String key, int fallback) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return fallback;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean extractBoolean(String json, String key, boolean fallback) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return fallback;
        }
        return Boolean.parseBoolean(m.group(1));
    }

    private static String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String jsonUnescape(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    static final class LauncherConfig {
        private final String modpack;
        private final int ramGb;
        private final String launchMode;
        private final String windowResolution;
        private final boolean borderlessWindow;
        private final boolean closeLauncherOnStart;
        private final boolean discordRpcEnabled;

        LauncherConfig(
                String modpack,
                int ramGb,
                String launchMode,
                String windowResolution,
                boolean borderlessWindow,
                boolean closeLauncherOnStart,
                boolean discordRpcEnabled
        ) {
            this.modpack = (modpack == null || modpack.isBlank()) ? "Normal" : modpack;
            this.ramGb = Math.max(2, Math.min(16, ramGb));
            this.launchMode = (launchMode == null || launchMode.isBlank()) ? "Ventana" : launchMode;
            this.windowResolution = (windowResolution == null || windowResolution.isBlank()) ? "1280x720" : windowResolution;
            this.borderlessWindow = borderlessWindow;
            this.closeLauncherOnStart = closeLauncherOnStart;
            this.discordRpcEnabled = discordRpcEnabled;
        }

        static LauncherConfig defaults() {
            return new LauncherConfig("Normal", 6, "Ventana", "1280x720", false, false, true);
        }

        String modpack() {
            return modpack;
        }

        int ramGb() {
            return ramGb;
        }

        String launchMode() {
            return launchMode;
        }

        String windowResolution() {
            return windowResolution;
        }

        boolean borderlessWindow() {
            return borderlessWindow;
        }

        boolean closeLauncherOnStart() {
            return closeLauncherOnStart;
        }

        boolean discordRpcEnabled() {
            return discordRpcEnabled;
        }

        String toJson() {
            return "{\n" +
                    "  \"modpack\": \"" + jsonEscape(modpack) + "\",\n" +
                    "  \"ramGb\": " + ramGb + ",\n" +
                    "  \"launchMode\": \"" + jsonEscape(launchMode) + "\",\n" +
                    "  \"windowResolution\": \"" + jsonEscape(windowResolution) + "\",\n" +
                    "  \"borderlessWindow\": " + borderlessWindow + ",\n" +
                    "  \"closeLauncherOnStart\": " + closeLauncherOnStart + ",\n" +
                    "  \"discordRpcEnabled\": " + discordRpcEnabled + "\n" +
                    "}\n";
        }

        static LauncherConfig fromJson(String json) {
            if (json == null || json.isBlank()) {
                return defaults();
            }
            return new LauncherConfig(
                    extractString(json, "modpack", "Normal"),
                    extractInt(json, "ramGb", 6),
                    extractString(json, "launchMode", "Ventana"),
                    extractString(json, "windowResolution", "1280x720"),
                    extractBoolean(json, "borderlessWindow", false),
                    extractBoolean(json, "closeLauncherOnStart", false),
                    extractBoolean(json, "discordRpcEnabled", true)
            );
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof LauncherConfig other)) {
                return false;
            }
            return ramGb == other.ramGb
                    && borderlessWindow == other.borderlessWindow
                    && closeLauncherOnStart == other.closeLauncherOnStart
                    && discordRpcEnabled == other.discordRpcEnabled
                    && modpack.equals(other.modpack)
                    && launchMode.equals(other.launchMode)
                    && windowResolution.equals(other.windowResolution);
        }

        @Override
        public int hashCode() {
            int result = modpack.hashCode();
            result = 31 * result + ramGb;
            result = 31 * result + launchMode.hashCode();
            result = 31 * result + windowResolution.hashCode();
            result = 31 * result + Boolean.hashCode(borderlessWindow);
            result = 31 * result + Boolean.hashCode(closeLauncherOnStart);
            result = 31 * result + Boolean.hashCode(discordRpcEnabled);
            return result;
        }
    }
}
