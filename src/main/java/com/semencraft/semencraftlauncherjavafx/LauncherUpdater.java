package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Busca versiones nuevas del launcher en GitHub Releases, descarga el MSI y
 * lo ejecuta. El MSI se encarga de desinstalar la version anterior y, con
 * SEMENCRAFT_RELAUNCH=1, de volver a abrir el launcher al terminar.
 */
final class LauncherUpdater {

    private static final String GITHUB_OWNER = "XK-GamerX";
    private static final String GITHUB_REPO = "SemencraftLauncherJavaFX";
    private static final String RELEASES_LATEST_API =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    static final String RELEASES_PAGE_URL =
            "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    private static final String EXE_NAME = "Semencraft Launcher.exe";
    private static final Pattern VERSION_NUMBERS = Pattern.compile("\\d+");

    private static volatile String cachedVersion;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    record UpdateInfo(
            String version,
            String tagName,
            String title,
            String notes,
            String downloadUrl,
            String assetName,
            long assetSize,
            String assetSha256,
            String pageUrl
    ) {
        boolean hasInstaller() {
            return downloadUrl != null && !downloadUrl.isBlank();
        }
    }

    interface DownloadProgress {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    static String currentVersion() {
        String version = cachedVersion;
        if (version != null) {
            return version;
        }
        version = "";
        try (InputStream in = LauncherUpdater.class.getResourceAsStream("launcher-version.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                version = props.getProperty("version", "").trim();
            }
        } catch (Exception ignored) {
        }
        if (version.isBlank()) {
            version = System.getProperty("jpackage.app-version", "").trim();
        }
        if (version.isBlank()) {
            version = "0.0.0";
        }
        cachedVersion = normalizeVersion(version);
        return cachedVersion;
    }

    /** Devuelve la release mas nueva si es mayor que la version actual; null si ya esta al dia. */
    UpdateInfo checkForUpdate() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_LATEST_API))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SemencraftLauncher/" + currentVersion())
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        JsonObject release;
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GitHub devolvio HTTP " + response.statusCode() + " al buscar actualizaciones.");
            }
            release = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Busqueda de actualizaciones interrumpida.", ex);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Respuesta invalida de GitHub: " + ex.getMessage(), ex);
        }

        if (bool(release, "draft") || bool(release, "prerelease")) {
            return null;
        }
        String tag = string(release, "tag_name");
        String remoteVersion = normalizeVersion(tag);
        if (compareVersions(remoteVersion, currentVersion()) <= 0) {
            return null;
        }

        String downloadUrl = "";
        String assetName = "";
        String assetSha256 = "";
        long assetSize = -1L;
        JsonArray assets = release.has("assets") && release.get("assets").isJsonArray()
                ? release.getAsJsonArray("assets")
                : new JsonArray();
        List<JsonObject> msiAssets = new ArrayList<>();
        for (JsonElement element : assets) {
            if (element.isJsonObject() && string(element.getAsJsonObject(), "name").toLowerCase(Locale.ROOT).endsWith(".msi")) {
                msiAssets.add(element.getAsJsonObject());
            }
        }
        msiAssets.sort((a, b) -> Boolean.compare(
                string(b, "name").toLowerCase(Locale.ROOT).contains("semencraft"),
                string(a, "name").toLowerCase(Locale.ROOT).contains("semencraft")));
        if (!msiAssets.isEmpty()) {
            JsonObject asset = msiAssets.get(0);
            downloadUrl = string(asset, "browser_download_url");
            assetName = string(asset, "name");
            assetSize = asset.has("size") ? asset.get("size").getAsLong() : -1L;
            String digest = string(asset, "digest");
            if (digest.toLowerCase(Locale.ROOT).startsWith("sha256:")) {
                assetSha256 = digest.substring("sha256:".length()).trim();
            }
        }

        String title = string(release, "name");
        return new UpdateInfo(
                remoteVersion,
                tag,
                title.isBlank() ? tag : title,
                cleanReleaseNotes(string(release, "body")),
                downloadUrl,
                assetName,
                assetSize,
                assetSha256,
                string(release, "html_url").isBlank() ? RELEASES_PAGE_URL : string(release, "html_url")
        );
    }

    Path downloadInstaller(UpdateInfo info, DownloadProgress progress) throws IOException {
        if (info == null || !info.hasInstaller()) {
            throw new IOException("La release no trae un instalador .msi.");
        }
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "semencraft-update");
        Files.createDirectories(dir);
        try (Stream<Path> old = Files.list(dir)) {
            for (Path file : old.toList()) {
                try {
                    Files.deleteIfExists(file);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        String safeName = info.assetName().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeName.isBlank()) {
            safeName = "Semencraft-Launcher-" + info.version() + ".msi";
        }
        Path target = dir.resolve(safeName);
        Path partial = dir.resolve(safeName + ".part");

        HttpRequest request = HttpRequest.newBuilder(URI.create(info.downloadUrl()))
                .header("User-Agent", "SemencraftLauncher/" + currentVersion())
                .header("Accept", "application/octet-stream")
                .timeout(Duration.ofMinutes(15))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("No se pudo descargar la actualizacion (HTTP " + response.statusCode() + ").");
            }
            long total = info.assetSize() > 0 ? info.assetSize() : response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long downloaded = 0L;
            long lastReport = 0L;
            try (InputStream input = response.body();
                 OutputStream output = Files.newOutputStream(partial,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    output.write(buffer, 0, read);
                    sha256.update(buffer, 0, read);
                    downloaded += read;
                    if (progress != null && downloaded - lastReport >= 256 * 1024) {
                        lastReport = downloaded;
                        progress.onProgress(downloaded, total);
                    }
                }
            }
            if (info.assetSize() > 0 && downloaded != info.assetSize()) {
                throw new IOException("La descarga quedo incompleta. Intenta de nuevo.");
            }
            if (!info.assetSha256().isBlank() && !toHex(sha256.digest()).equalsIgnoreCase(info.assetSha256())) {
                throw new IOException("El instalador descargado no paso la verificacion de integridad.");
            }
            if (progress != null) {
                progress.onProgress(downloaded, total > 0 ? total : downloaded);
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Descarga interrumpida.", ex);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("No se pudo descargar la actualizacion: " + ex.getMessage(), ex);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    /** true si este proceso es el launcher instalado (jpackage), no una build de desarrollo. */
    static boolean canInstallInPlace() {
        return installedExecutable() != null;
    }

    static Path installedExecutable() {
        String appPath = System.getProperty("jpackage.app-path", "");
        if (!appPath.isBlank()) {
            Path path = Path.of(appPath);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        try {
            String command = ProcessHandle.current().info().command().orElse("");
            if (command.toLowerCase(Locale.ROOT).endsWith(EXE_NAME.toLowerCase(Locale.ROOT))) {
                return Path.of(command);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Lanza el MSI en modo pasivo (solo barra de progreso). El instalador quita
     * la version anterior y vuelve a abrir el launcher. Quien llama debe cerrar
     * el launcher enseguida para liberar los archivos.
     */
    static void startInstaller(Path msi) throws IOException {
        Path log = msi.resolveSibling("install.log");
        new ProcessBuilder(
                "msiexec.exe",
                "/i", msi.toAbsolutePath().toString(),
                "/passive",
                "/norestart",
                "SEMENCRAFT_RELAUNCH=1",
                "/l*v", log.toAbsolutePath().toString()
        ).start();
    }

    static String normalizeVersion(String raw) {
        Matcher matcher = VERSION_NUMBERS.matcher(raw == null ? "" : raw);
        List<String> parts = new ArrayList<>();
        while (matcher.find() && parts.size() < 3) {
            parts.add(String.valueOf(Integer.parseInt(matcher.group())));
        }
        while (parts.size() < 3) {
            parts.add("0");
        }
        return String.join(".", parts);
    }

    static int compareVersions(String a, String b) {
        String[] left = normalizeVersion(a).split("\\.");
        String[] right = normalizeVersion(b).split("\\.");
        for (int i = 0; i < 3; i++) {
            int cmp = Integer.compare(Integer.parseInt(left[i]), Integer.parseInt(right[i]));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static String cleanReleaseNotes(String body) {
        if (body == null) {
            return "";
        }
        String cleaned = body.replace("\r", "")
                .replaceAll("(?m)^#+\\s*", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("(?m)^\\s*[-*]\\s+", "• ")
                .trim();
        return cleaned.length() > 900 ? cleaned.substring(0, 900).trim() + "..." : cleaned;
    }

    private static String string(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean bool(JsonObject obj, String key) {
        try {
            return obj != null && obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(Character.forDigit((value >>> 4) & 0x0F, 16));
            out.append(Character.forDigit(value & 0x0F, 16));
        }
        return out.toString();
    }
}
