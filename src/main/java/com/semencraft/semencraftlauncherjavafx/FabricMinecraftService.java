package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.Gson;
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
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class FabricMinecraftService {

    static final String TARGET_MINECRAFT_VERSION = "1.21.8";

    private static final String USER_AGENT = "SemencraftLauncher/1.0";
    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String FABRIC_LOADER_URL = "https://meta.fabricmc.net/v2/versions/loader/" + TARGET_MINECRAFT_VERSION;
    private static final String FABRIC_PROFILE_URL = "https://meta.fabricmc.net/v2/versions/loader/" + TARGET_MINECRAFT_VERSION + "/%s/profile/json";
    private static final String ASSET_OBJECT_BASE_URL = "https://resources.download.minecraft.net/";
    private static final int DOWNLOAD_RETRY_COUNT = 5;
    private static final long DOWNLOAD_RETRY_BASE_DELAY_MS = 1200L;
    private static final long PROCESS_STARTUP_CHECK_MS = 8000L;
    private static final int LAUNCH_LOG_TAIL_LINES = 40;

    private static final double CORE_START = 0.10;
    private static final double CORE_END = 0.56;
    private static final double ASSET_START = 0.56;
    private static final double ASSET_END = 0.92;

    private static final Pattern RESOLUTION_PATTERN = Pattern.compile("\\s*(\\d{3,5})\\s*[xX]\\s*(\\d{3,5})\\s*");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final Gson gson = new Gson();

    interface ProgressListener {
        void onProgress(String status, double totalProgress, double currentDownloadProgress);
    }

    record LaunchResult(Process process, String profileId, String loaderVersion) {
    }

    private record DownloadItem(String label, String url, Path target, String sha1, long size) {
    }

    LaunchResult installAndLaunch(String username, LauncherStorage.LauncherConfig config, ProgressListener progress) throws Exception {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(config, "config");

        LauncherStorage.ensureEnvironment();
        Path gameDir = LauncherStorage.minecraftDirectory();
        Path launcherDir = LauncherStorage.launcherDirectory();
        Path versionsDir = gameDir.resolve("versions");
        Path librariesDir = gameDir.resolve("libraries");
        Path assetsDir = gameDir.resolve("assets");
        Path assetIndexesDir = assetsDir.resolve("indexes");
        Path assetObjectsDir = assetsDir.resolve("objects");
        Path logConfigsDir = assetsDir.resolve("log_configs");
        Path launchLogPath = launcherDir.resolve("latest-launch.log");

        report(progress, "Preparando directorios...", 0.02, 0.0);
        Files.createDirectories(gameDir);
        Files.createDirectories(versionsDir);
        Files.createDirectories(librariesDir);
        Files.createDirectories(assetIndexesDir);
        Files.createDirectories(assetObjectsDir);
        Files.createDirectories(logConfigsDir);
        Files.createDirectories(launcherDir);

        report(progress, "Consultando versiones de Minecraft...", 0.05, 0.0);
        JsonObject versionManifest = fetchJsonObject(VERSION_MANIFEST_URL);
        JsonObject minecraftEntry = findMinecraftVersion(versionManifest, TARGET_MINECRAFT_VERSION);
        String minecraftMetaUrl = requiredString(minecraftEntry, "url");
        JsonObject minecraftMeta = fetchJsonObject(minecraftMetaUrl);

        report(progress, "Consultando loader de Fabric...", 0.08, 0.0);
        JsonArray loaderCandidates = fetchJsonArray(FABRIC_LOADER_URL);
        JsonObject selectedLoader = pickFabricLoader(loaderCandidates);
        String loaderVersion = requiredString(requiredObject(selectedLoader, "loader"), "version");
        JsonObject fabricProfile = fetchJsonObject(String.format(Locale.US, FABRIC_PROFILE_URL, loaderVersion));
        String profileId = requiredString(fabricProfile, "id");
        String mainClass = requiredString(fabricProfile, "mainClass");

        Path vanillaVersionDir = versionsDir.resolve(TARGET_MINECRAFT_VERSION);
        Path vanillaMetaPath = vanillaVersionDir.resolve(TARGET_MINECRAFT_VERSION + ".json");
        Path vanillaJarPath = vanillaVersionDir.resolve(TARGET_MINECRAFT_VERSION + ".jar");
        Files.createDirectories(vanillaVersionDir);
        writeJson(vanillaMetaPath, minecraftMeta);

        Path fabricVersionDir = versionsDir.resolve(profileId);
        Path fabricMetaPath = fabricVersionDir.resolve(profileId + ".json");
        Files.createDirectories(fabricVersionDir);
        writeJson(fabricMetaPath, fabricProfile);

        Map<Path, DownloadItem> downloadMap = new LinkedHashMap<>();
        LinkedHashMap<String, Path> classpathLibraries = new LinkedHashMap<>();
        LinkedHashMap<String, Path> nativeJars = new LinkedHashMap<>();

        JsonObject clientDownload = requiredObject(requiredObject(minecraftMeta, "downloads"), "client");
        addDownload(
                downloadMap,
                "Cliente Minecraft " + TARGET_MINECRAFT_VERSION,
                requiredString(clientDownload, "url"),
                vanillaJarPath,
                optionalString(clientDownload, "sha1"),
                optionalLong(clientDownload, "size")
        );

        JsonObject assetIndex = requiredObject(minecraftMeta, "assetIndex");
        String assetsIndexId = requiredString(assetIndex, "id");
        Path assetIndexPath = assetIndexesDir.resolve(assetsIndexId + ".json");
        addDownload(
                downloadMap,
                "Indice de assets " + assetsIndexId,
                requiredString(assetIndex, "url"),
                assetIndexPath,
                optionalString(assetIndex, "sha1"),
                optionalLong(assetIndex, "size")
        );

        JsonObject logging = optionalObject(minecraftMeta, "logging");
        String loggingConfigPath = null;
        if (logging != null) {
            JsonObject clientLog = optionalObject(logging, "client");
            JsonObject fileObj = clientLog == null ? null : optionalObject(clientLog, "file");
            if (fileObj != null) {
                String logFileId = requiredString(fileObj, "id");
                Path logFilePath = logConfigsDir.resolve(logFileId);
                addDownload(
                        downloadMap,
                        "Configuracion de logs",
                        requiredString(fileObj, "url"),
                        logFilePath,
                        optionalString(fileObj, "sha1"),
                        optionalLong(fileObj, "size")
                );
                loggingConfigPath = logFilePath.toAbsolutePath().toString();
            }
        }

        String osName = currentOsName();
        String arch = currentArch();
        Map<String, Boolean> defaultFeatures = baseFeatures(false);

        JsonArray vanillaLibraries = requiredArray(minecraftMeta, "libraries");
        for (JsonElement element : vanillaLibraries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject lib = element.getAsJsonObject();
            if (!isAllowedByRules(lib, defaultFeatures, osName, arch)) {
                continue;
            }
            addVanillaLibraryDownloads(lib, librariesDir, downloadMap, classpathLibraries, nativeJars, osName, arch);
        }

        JsonArray fabricLibraries = requiredArray(fabricProfile, "libraries");
        for (JsonElement element : fabricLibraries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject lib = element.getAsJsonObject();
            String name = requiredString(lib, "name");
            if (isNativeLibraryName(name)) {
                continue;
            }

            MavenArtifact maven = MavenArtifact.from(name, optionalString(lib, "url"));
            Path target = librariesDir.resolve(maven.path());
            addDownload(
                    downloadMap,
                    name,
                    maven.url(),
                    target,
                    optionalString(lib, "sha1"),
                    optionalLong(lib, "size")
            );
            putLibraryPath(classpathLibraries, libraryCoordinateKey(name, null), target);
        }

        List<DownloadItem> coreDownloads = new ArrayList<>(downloadMap.values());
        downloadCoreFiles(coreDownloads, progress);

        report(progress, "Leyendo indice de assets...", CORE_END, 1.0);
        JsonObject assetsIndexJson = readJsonObject(assetIndexPath);
        JsonObject assetObjects = requiredObject(assetsIndexJson, "objects");
        downloadAssets(assetObjects, assetObjectsDir, progress);

        report(progress, "Extrayendo natives...", 0.94, 0.0);
        Path nativesDir = fabricVersionDir.resolve("natives");
        cleanDirectory(nativesDir);
        extractNativeJars(new LinkedHashSet<>(nativeJars.values()), nativesDir);

        LinkedHashSet<Path> classpath = new LinkedHashSet<>(classpathLibraries.values());
        classpath.add(vanillaJarPath);
        String classpathValue = buildClasspath(classpath);
        String authUuid = offlineUuid(username);
        String accessToken = UUID.randomUUID().toString().replace("-", "");
        int[] resolution = resolveResolution(config.windowResolution());
        boolean customResolution = !"Pantalla completa".equalsIgnoreCase(config.launchMode());

        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("auth_player_name", username);
        tokens.put("version_name", profileId);
        tokens.put("game_directory", gameDir.toAbsolutePath().toString());
        tokens.put("assets_root", assetsDir.toAbsolutePath().toString());
        tokens.put("assets_index_name", assetsIndexId);
        tokens.put("auth_uuid", authUuid);
        tokens.put("auth_access_token", accessToken);
        tokens.put("clientid", UUID.randomUUID().toString());
        tokens.put("auth_xuid", "0");
        tokens.put("user_type", "legacy");
        tokens.put("version_type", "release");
        tokens.put("natives_directory", nativesDir.toAbsolutePath().toString());
        tokens.put("launcher_name", "semencraft-launcher");
        tokens.put("launcher_version", "1.0");
        tokens.put("classpath", classpathValue);
        tokens.put("classpath_separator", System.getProperty("path.separator"));
        tokens.put("library_directory", librariesDir.toAbsolutePath().toString());
        tokens.put("resolution_width", Integer.toString(resolution[0]));
        tokens.put("resolution_height", Integer.toString(resolution[1]));
        tokens.put("quickPlayPath", "");
        tokens.put("quickPlaySingleplayer", "");
        tokens.put("quickPlayMultiplayer", "");
        tokens.put("quickPlayRealms", "");
        tokens.put("user_properties", "{}");
        tokens.put("auth_session", accessToken);
        if (loggingConfigPath != null) {
            tokens.put("path", loggingConfigPath);
        }

        Map<String, Boolean> argFeatures = baseFeatures(customResolution);

        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.add("-Xms1G");
        jvmArgs.add("-Xmx" + config.ramGb() + "G");
        collectArguments(jvmArgs, requiredObject(minecraftMeta, "arguments"), "jvm", tokens, argFeatures, osName, arch);
        collectArguments(jvmArgs, requiredObject(fabricProfile, "arguments"), "jvm", tokens, argFeatures, osName, arch);
        normalizeJvmArguments(jvmArgs);

        List<String> gameArgs = new ArrayList<>();
        collectArguments(gameArgs, requiredObject(minecraftMeta, "arguments"), "game", tokens, argFeatures, osName, arch);
        JsonObject fabricArgs = requiredObject(fabricProfile, "arguments");
        if (fabricArgs.has("game")) {
            collectArguments(gameArgs, fabricArgs, "game", tokens, argFeatures, osName, arch);
        }
        if ("Pantalla completa".equalsIgnoreCase(config.launchMode()) && !gameArgs.contains("--fullscreen")) {
            gameArgs.add("--fullscreen");
        }

        List<String> command = new ArrayList<>();
        command.add(resolveJavaBinary().toString());
        command.addAll(jvmArgs);
        command.add(mainClass);
        command.addAll(gameArgs);

        report(progress, "Iniciando Minecraft (Fabric " + loaderVersion + ")...", 0.99, 1.0);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(launchLogPath.toFile());
        Process process = pb.start();
        ensureProcessStarted(process, launchLogPath);

        report(progress, "Minecraft iniciado.", 1.0, 1.0);
        return new LaunchResult(process, profileId, loaderVersion);
    }

    private static void addVanillaLibraryDownloads(
            JsonObject lib,
            Path librariesDir,
            Map<Path, DownloadItem> downloadMap,
            LinkedHashMap<String, Path> classpath,
            LinkedHashMap<String, Path> nativeJars,
            String osName,
            String arch
    ) {
        JsonObject downloads = optionalObject(lib, "downloads");
        if (downloads == null) {
            return;
        }

        String name = requiredString(lib, "name");
        JsonObject artifact = optionalObject(downloads, "artifact");
        if (artifact != null) {
            Path target = librariesDir.resolve(requiredString(artifact, "path"));
            addDownload(
                    downloadMap,
                    name,
                    requiredString(artifact, "url"),
                    target,
                    optionalString(artifact, "sha1"),
                    optionalLong(artifact, "size")
            );
            if (isNativeLibraryName(name)) {
                if (isNativeForCurrentArch(name, osName, arch)) {
                    putLibraryPath(nativeJars, libraryCoordinateKey(name, null), target);
                }
            } else {
                putLibraryPath(classpath, libraryCoordinateKey(name, null), target);
            }
        }

        String nativeClassifier = resolveNativeClassifier(lib, downloads, osName, arch);
        if (nativeClassifier == null) {
            return;
        }

        JsonObject classifiers = optionalObject(downloads, "classifiers");
        JsonObject nativeArtifact = classifiers == null ? null : optionalObject(classifiers, nativeClassifier);
        if (nativeArtifact == null) {
            return;
        }

        Path nativeTarget = librariesDir.resolve(requiredString(nativeArtifact, "path"));
        addDownload(
                downloadMap,
                name + " (" + nativeClassifier + ")",
                requiredString(nativeArtifact, "url"),
                nativeTarget,
                optionalString(nativeArtifact, "sha1"),
                optionalLong(nativeArtifact, "size")
        );
        putLibraryPath(nativeJars, libraryCoordinateKey(name, nativeClassifier), nativeTarget);
    }

    private static String libraryCoordinateKey(String coordinate, String classifierOverride) {
        if (coordinate == null || coordinate.isBlank()) {
            return "";
        }
        String[] parts = coordinate.split(":");
        if (parts.length < 2) {
            return coordinate.trim();
        }
        String classifier = classifierOverride;
        if ((classifier == null || classifier.isBlank()) && parts.length > 3) {
            classifier = parts[3];
        }
        if (classifier == null || classifier.isBlank()) {
            return parts[0] + ":" + parts[1];
        }
        return parts[0] + ":" + parts[1] + ":" + classifier;
    }

    private static void putLibraryPath(LinkedHashMap<String, Path> target, String key, Path value) {
        if (target == null || value == null) {
            return;
        }
        String resolvedKey = (key == null || key.isBlank()) ? value.toAbsolutePath().toString() : key;
        target.remove(resolvedKey);
        target.put(resolvedKey, value);
    }

    private static String resolveNativeClassifier(JsonObject lib, JsonObject downloads, String osName, String arch) {
        JsonObject classifiers = optionalObject(downloads, "classifiers");
        if (classifiers == null || classifiers.entrySet().isEmpty()) {
            return null;
        }

        for (String candidate : nativeClassifierCandidates(lib, osName, arch)) {
            if (optionalObject(classifiers, candidate) != null) {
                return candidate;
            }
        }

        String nativePrefix = "natives-" + osName;
        for (Map.Entry<String, JsonElement> entry : classifiers.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            String key = entry.getKey();
            String lowered = key.toLowerCase(Locale.ROOT);
            if (lowered.startsWith(nativePrefix) && matchesNativeClassifierArch(lowered, arch)) {
                return key;
            }
        }

        for (Map.Entry<String, JsonElement> entry : classifiers.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static List<String> nativeClassifierCandidates(JsonObject lib, String osName, String arch) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        JsonObject natives = optionalObject(lib, "natives");
        if (natives != null) {
            addNativeTemplateCandidates(candidates, optionalString(natives, osName), arch);
            if ("osx".equals(osName)) {
                addNativeTemplateCandidates(candidates, optionalString(natives, "macos"), arch);
            }
        }

        String nativePrefix = "natives-" + osName;
        candidates.add(nativePrefix);
        for (String token : nativeArchTokens(arch)) {
            candidates.add(nativePrefix + "-" + token);
        }
        if ("osx".equals(osName)) {
            candidates.add("natives-macos");
            for (String token : nativeArchTokens(arch)) {
                candidates.add("natives-macos-" + token);
            }
        }

        return new ArrayList<>(candidates);
    }

    private static void addNativeTemplateCandidates(LinkedHashSet<String> out, String template, String arch) {
        if (template == null || template.isBlank()) {
            return;
        }
        if (!template.contains("${arch}")) {
            out.add(template);
            return;
        }
        for (String token : nativeArchTokens(arch)) {
            out.add(template.replace("${arch}", token));
        }
    }

    private static List<String> nativeArchTokens(String arch) {
        List<String> tokens = new ArrayList<>();
        if ("x86".equals(arch)) {
            tokens.add("32");
            tokens.add("x86");
            return tokens;
        }
        if ("arm64".equals(arch)) {
            tokens.add("arm64");
            tokens.add("aarch64");
            tokens.add("64");
            return tokens;
        }
        tokens.add("64");
        tokens.add("x64");
        tokens.add("x86_64");
        tokens.add("amd64");
        return tokens;
    }

    private static boolean matchesNativeClassifierArch(String classifier, String arch) {
        if (classifier == null || classifier.isBlank()) {
            return false;
        }
        String lowered = classifier.toLowerCase(Locale.ROOT);
        if (lowered.contains("arm64") || lowered.contains("aarch64")) {
            return "arm64".equals(arch);
        }
        if (lowered.contains("x86_64") || lowered.contains("amd64") || lowered.contains("x64")) {
            return "x64".equals(arch);
        }
        if (lowered.contains("x86") || lowered.contains("-32")) {
            return "x86".equals(arch);
        }
        if (lowered.endsWith("-64")) {
            return "x64".equals(arch);
        }
        return "x64".equals(arch);
    }

    private static void ensureProcessStarted(Process process, Path launchLogPath) throws IOException {
        boolean exited;
        try {
            exited = process.waitFor(PROCESS_STARTUP_CHECK_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Inicio de Minecraft interrumpido.", ex);
        }
        if (!exited) {
            return;
        }

        int exitCode = process.exitValue();
        String logTail = readLogTail(launchLogPath, LAUNCH_LOG_TAIL_LINES);
        StringBuilder message = new StringBuilder();
        message.append("Minecraft se cerro al iniciar (codigo ").append(exitCode).append(").");
        if (logTail == null || logTail.isBlank()) {
            message.append("\n\nNo se encontraron detalles en latest-launch.log.");
        } else {
            message.append("\n\nUltimas lineas de latest-launch.log:\n").append(logTail);
        }
        throw new IOException(message.toString());
    }

    private static String readLogTail(Path logPath, int maxLines) {
        if (maxLines <= 0 || logPath == null || !Files.exists(logPath)) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - maxLines);
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) {
                    continue;
                }
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void downloadCoreFiles(List<DownloadItem> items, ProgressListener progress) throws Exception {
        if (items.isEmpty()) {
            report(progress, "No hay archivos base para descargar.", CORE_END, 1.0);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            DownloadItem item = items.get(i);
            int idx = i;
            String status = "Descargando base (" + (i + 1) + "/" + items.size() + "): " + item.label;
            downloadWithRetries(item, ratio -> {
                double segment = (idx + ratio) / items.size();
                double total = CORE_START + (CORE_END - CORE_START) * segment;
                report(progress, status, total, ratio);
            });
        }
        report(progress, "Archivos base listos.", CORE_END, 1.0);
    }

    private void downloadAssets(JsonObject objects, Path assetObjectsDir, ProgressListener progress) throws Exception {
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(objects.entrySet());
        if (entries.isEmpty()) {
            report(progress, "No hay assets para descargar.", ASSET_END, 1.0);
            return;
        }

        report(progress, "Verificando assets locales...", ASSET_START, 0.0);
        List<DownloadItem> pending = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : entries) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject value = entry.getValue().getAsJsonObject();
            String hash = requiredString(value, "hash");
            if (hash.length() < 3) {
                continue;
            }
            String prefix = hash.substring(0, 2);
            String url = ASSET_OBJECT_BASE_URL + prefix + "/" + hash;
            Path target = assetObjectsDir.resolve(prefix).resolve(hash);
            DownloadItem item = new DownloadItem(
                    entry.getKey(),
                    url,
                    target,
                    hash,
                    optionalLong(value, "size")
            );
            if (!isUpToDate(target, item.sha1(), item.size())) {
                pending.add(item);
            }
        }

        if (pending.isEmpty()) {
            report(progress, "Assets ya estan al dia.", ASSET_END, 1.0);
            return;
        }

        for (int i = 0; i < pending.size(); i++) {
            DownloadItem item = pending.get(i);
            int idx = i;
            String status = "Descargando assets (" + (i + 1) + "/" + pending.size() + ")";
            downloadWithRetries(item, ratio -> {
                double segment = (idx + ratio) / pending.size();
                double total = ASSET_START + (ASSET_END - ASSET_START) * segment;
                report(progress, status, total, ratio);
            });
        }
        report(progress, "Assets listos.", ASSET_END, 1.0);
    }

    private void downloadWithRetries(DownloadItem item, java.util.function.DoubleConsumer progress) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= DOWNLOAD_RETRY_COUNT; attempt++) {
            try {
                downloadIfNeeded(item, progress);
                return;
            } catch (Exception ex) {
                lastError = ex;
                if (attempt >= DOWNLOAD_RETRY_COUNT || !isRetryableDownloadError(ex)) {
                    break;
                }
                progress.accept(0.0);
                long waitMs = DOWNLOAD_RETRY_BASE_DELAY_MS * attempt;
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Descarga interrumpida.", interrupted);
                }
            }
        }

        if (canReuseLocalWithoutChecksums(item.target(), item.sha1(), item.size())) {
            progress.accept(1.0);
            return;
        }

        String suffix = lastError == null
                ? "Error desconocido."
                : (lastError.getMessage() == null || lastError.getMessage().isBlank()
                ? lastError.toString()
                : lastError.getMessage());
        throw new IOException(
                "No se pudo descargar '" + item.label() + "' tras " + DOWNLOAD_RETRY_COUNT + " intentos. " + suffix,
                lastError
        );
    }

    private static boolean isRetryableDownloadError(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("error http 404")
                        || lower.contains("error http 403")
                        || lower.contains("sha1 invalido")
                        || lower.contains("tamano invalido")) {
                    return false;
                }
                if (lower.contains("connection reset")
                        || lower.contains("timed out")
                        || lower.contains("forcibly closed")
                        || lower.contains("connection aborted")
                        || lower.contains("premature end")
                        || lower.contains("unexpected end")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return ex instanceof IOException;
    }

    private void extractNativeJars(LinkedHashSet<Path> nativeJars, Path nativesDir) throws Exception {
        Files.createDirectories(nativesDir);
        for (Path jar : nativeJars) {
            if (!Files.exists(jar)) {
                continue;
            }
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = entry.getName().replace('\\', '/');
                    if (name.startsWith("META-INF/")) {
                        continue;
                    }
                    Path out = nativesDir.resolve(name).normalize();
                    if (!out.startsWith(nativesDir)) {
                        continue;
                    }
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(
                            out,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE
                    )) {
                        zis.transferTo(os);
                    }
                }
            }
        }
    }

    private static void cleanDirectory(Path dir) throws IOException {
        Files.createDirectories(dir);
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .filter(path -> !path.equals(dir))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    private void downloadIfNeeded(DownloadItem item, java.util.function.DoubleConsumer progress) throws Exception {
        Path target = item.target();
        Files.createDirectories(target.getParent());
        if (isUpToDate(target, item.sha1(), item.size())) {
            progress.accept(1.0);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(item.url()))
                .header("User-Agent", USER_AGENT)
                .GET()
                .timeout(Duration.ofMinutes(5))
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Error HTTP " + statusCode + " al descargar " + item.url());
        }

        long expectedLength = response.headers().firstValueAsLong("Content-Length").orElse(item.size());
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(
                     temp,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE
             )) {
            byte[] buffer = new byte[64 * 1024];
            long read = 0;
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) {
                    continue;
                }
                out.write(buffer, 0, n);
                read += n;
                if (expectedLength > 0) {
                    progress.accept(Math.min(1.0, (double) read / expectedLength));
                }
            }
        } catch (Exception ex) {
            Files.deleteIfExists(temp);
            throw ex;
        }

        if (item.sha1() != null && !item.sha1().isBlank()) {
            String actual = sha1Hex(temp);
            if (!item.sha1().equalsIgnoreCase(actual)) {
                Files.deleteIfExists(temp);
                throw new IOException("SHA1 invalido para " + item.label() + ". Esperado " + item.sha1() + ", obtenido " + actual);
            }
        } else if (item.size() > 0 && Files.size(temp) != item.size()) {
            Files.deleteIfExists(temp);
            throw new IOException("Tamano invalido para " + item.label());
        }

        try {
            moveReplace(temp, target);
        } catch (IOException ex) {
            if (isUpToDate(target, item.sha1(), item.size())) {
                Files.deleteIfExists(temp);
                progress.accept(1.0);
                return;
            }
            Files.deleteIfExists(temp);
            throw ex;
        }
        progress.accept(1.0);
    }

    private static boolean isUpToDate(Path file, String sha1, long size) throws Exception {
        if (!Files.exists(file)) {
            return false;
        }
        if (size > 0 && Files.size(file) == size) {
            return true;
        }
        if (sha1 != null && !sha1.isBlank()) {
            return sha1.equalsIgnoreCase(sha1Hex(file));
        }
        return canReuseLocalWithoutChecksums(file, sha1, size);
    }

    private static boolean canReuseLocalWithoutChecksums(Path file, String sha1, long size) throws IOException {
        if (file == null || !Files.exists(file)) {
            return false;
        }
        if ((sha1 != null && !sha1.isBlank()) || size > 0) {
            return false;
        }
        return Files.size(file) > 0;
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileSystemException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha1Hex(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) {
                    continue;
                }
                digest.update(buffer, 0, n);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private static String buildClasspath(LinkedHashSet<Path> entries) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Path entry : entries) {
            if (!first) {
                sb.append(System.getProperty("path.separator"));
            }
            sb.append(entry.toAbsolutePath());
            first = false;
        }
        return sb.toString();
    }

    private static String offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static int[] resolveResolution(String resolutionValue) {
        int width = 1280;
        int height = 720;
        if (resolutionValue != null) {
            Matcher matcher = RESOLUTION_PATTERN.matcher(resolutionValue);
            if (matcher.matches()) {
                try {
                    width = Math.max(854, Integer.parseInt(matcher.group(1)));
                    height = Math.max(480, Integer.parseInt(matcher.group(2)));
                } catch (Exception ignored) {
                }
            }
        }
        return new int[]{width, height};
    }

    private static Map<String, Boolean> baseFeatures(boolean hasCustomResolution) {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("is_demo_user", false);
        features.put("has_custom_resolution", hasCustomResolution);
        features.put("has_quick_plays_support", false);
        features.put("is_quick_play_singleplayer", false);
        features.put("is_quick_play_multiplayer", false);
        features.put("is_quick_play_realms", false);
        return features;
    }

    private static String currentOsName() {
        String raw = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (raw.contains("win")) {
            return "windows";
        }
        if (raw.contains("mac") || raw.contains("darwin")) {
            return "osx";
        }
        return "linux";
    }

    private static String currentArch() {
        String raw = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (raw.contains("aarch64") || raw.contains("arm64")) {
            return "arm64";
        }
        if (raw.contains("86") && !raw.contains("64")) {
            return "x86";
        }
        return "x64";
    }

    private static boolean isNativeLibraryName(String libraryName) {
        return libraryName != null && libraryName.contains(":natives-");
    }

    private static boolean isNativeForCurrentArch(String libraryName, String osName, String arch) {
        if (!isNativeLibraryName(libraryName)) {
            return false;
        }
        String classifier = libraryName.substring(libraryName.lastIndexOf(':') + 1).toLowerCase(Locale.ROOT);
        if (!classifier.startsWith("natives-" + osName)) {
            return false;
        }
        if (classifier.equals("natives-" + osName)) {
            return "x64".equals(arch);
        }
        if (classifier.contains("arm64") || classifier.contains("aarch64")) {
            return "arm64".equals(arch);
        }
        if (classifier.contains("x86_64") || classifier.contains("amd64") || classifier.contains("x64")) {
            return "x64".equals(arch);
        }
        if (classifier.contains("x86") || classifier.contains("-32")) {
            return "x86".equals(arch);
        }
        if (classifier.endsWith("-64")) {
            return "x64".equals(arch);
        }
        return true;
    }

    private static boolean isAllowedByRules(
            JsonObject node,
            Map<String, Boolean> features,
            String osName,
            String arch
    ) {
        JsonArray rules = optionalArray(node, "rules");
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        for (JsonElement ruleElement : rules) {
            if (!ruleElement.isJsonObject()) {
                continue;
            }
            JsonObject rule = ruleElement.getAsJsonObject();
            if (!ruleMatches(rule, features, osName, arch)) {
                continue;
            }
            String action = optionalString(rule, "action");
            allowed = "allow".equalsIgnoreCase(action);
        }
        return allowed;
    }

    private static boolean ruleMatches(
            JsonObject rule,
            Map<String, Boolean> features,
            String osName,
            String arch
    ) {
        JsonObject os = optionalObject(rule, "os");
        if (os != null) {
            String ruleOsName = optionalString(os, "name");
            if (ruleOsName != null && !ruleOsName.equalsIgnoreCase(osName)) {
                return false;
            }
            String ruleArch = optionalString(os, "arch");
            if (ruleArch != null) {
                if ("x86".equalsIgnoreCase(ruleArch) && !"x86".equals(arch)) {
                    return false;
                }
                if ("x64".equalsIgnoreCase(ruleArch) && !"x64".equals(arch)) {
                    return false;
                }
                if ("arm64".equalsIgnoreCase(ruleArch) && !"arm64".equals(arch)) {
                    return false;
                }
            }
        }

        JsonObject featureRules = optionalObject(rule, "features");
        if (featureRules != null) {
            for (Map.Entry<String, JsonElement> feature : featureRules.entrySet()) {
                boolean expected = feature.getValue().getAsBoolean();
                boolean actual = features.getOrDefault(feature.getKey(), false);
                if (expected != actual) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void collectArguments(
            List<String> out,
            JsonObject argsRoot,
            String key,
            Map<String, String> tokens,
            Map<String, Boolean> features,
            String osName,
            String arch
    ) {
        JsonArray args = optionalArray(argsRoot, key);
        if (args == null) {
            return;
        }
        for (JsonElement arg : args) {
            if (arg.isJsonPrimitive()) {
                out.add(replaceTokens(arg.getAsString(), tokens));
                continue;
            }
            if (!arg.isJsonObject()) {
                continue;
            }
            JsonObject obj = arg.getAsJsonObject();
            if (!isAllowedByRules(obj, features, osName, arch)) {
                continue;
            }
            JsonElement value = obj.get("value");
            if (value == null) {
                continue;
            }
            if (value.isJsonPrimitive()) {
                out.add(replaceTokens(value.getAsString(), tokens));
            } else if (value.isJsonArray()) {
                for (JsonElement valueEntry : value.getAsJsonArray()) {
                    if (valueEntry.isJsonPrimitive()) {
                        out.add(replaceTokens(valueEntry.getAsString(), tokens));
                    }
                }
            }
        }
    }

    private static void normalizeJvmArguments(List<String> jvmArgs) {
        for (int i = 0; i < jvmArgs.size(); i++) {
            String arg = jvmArgs.get(i);
            if (arg == null) {
                continue;
            }
            String trimmed = arg.trim();
            if (trimmed.startsWith("-D") && trimmed.contains("= ")) {
                trimmed = trimmed.replace("= ", "=");
            }
            jvmArgs.set(i, trimmed);
        }
    }

    private static String replaceTokens(String raw, Map<String, String> tokens) {
        String result = raw;
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            result = result.replace("${" + token.getKey() + "}", token.getValue());
        }
        return result;
    }

    private static Path resolveJavaBinary() {
        String executable = currentOsName().equals("windows") ? "javaw.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        if (Files.exists(java)) {
            return java;
        }
        return Path.of("java");
    }

    private static void addDownload(
            Map<Path, DownloadItem> map,
            String label,
            String url,
            Path target,
            String sha1,
            long size
    ) {
        if (url == null || url.isBlank() || target == null) {
            return;
        }
        map.putIfAbsent(target, new DownloadItem(label, url, target, sha1, size));
    }

    private static JsonObject findMinecraftVersion(JsonObject manifest, String version) {
        JsonArray versions = requiredArray(manifest, "versions");
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            if (version.equals(optionalString(entry, "id"))) {
                return entry;
            }
        }
        throw new IllegalStateException("No se encontro la version de Minecraft " + version + " en el manifest.");
    }

    private static JsonObject pickFabricLoader(JsonArray candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No se encontro un loader de Fabric para " + TARGET_MINECRAFT_VERSION);
        }
        for (JsonElement candidate : candidates) {
            if (!candidate.isJsonObject()) {
                continue;
            }
            JsonObject loaderObj = optionalObject(candidate.getAsJsonObject(), "loader");
            if (loaderObj != null && optionalBoolean(loaderObj, "stable", false)) {
                return candidate.getAsJsonObject();
            }
        }
        return candidates.get(0).getAsJsonObject();
    }

    private JsonObject fetchJsonObject(String url) throws Exception {
        String json = fetchText(url);
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private JsonArray fetchJsonArray(String url) throws Exception {
        String json = fetchText(url);
        return JsonParser.parseString(json).getAsJsonArray();
    }

    private JsonObject readJsonObject(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private String fetchText(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .timeout(Duration.ofMinutes(2))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Error HTTP " + code + " al consultar " + url);
        }
        return response.body();
    }

    private void writeJson(Path path, JsonObject jsonObject) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(
                path,
                gson.toJson(jsonObject),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private static void report(ProgressListener progress, String status, double total, double current) {
        if (progress == null) {
            return;
        }
        double clampedTotal = Math.max(0.0, Math.min(1.0, total));
        double clampedCurrent = Math.max(0.0, Math.min(1.0, current));
        progress.onProgress(status, clampedTotal, clampedCurrent);
    }

    private static JsonObject requiredObject(JsonObject root, String key) {
        JsonObject obj = optionalObject(root, key);
        if (obj == null) {
            throw new IllegalStateException("Campo JSON faltante: " + key);
        }
        return obj;
    }

    private static JsonArray requiredArray(JsonObject root, String key) {
        JsonArray arr = optionalArray(root, key);
        if (arr == null) {
            throw new IllegalStateException("Campo JSON faltante: " + key);
        }
        return arr;
    }

    private static String requiredString(JsonObject root, String key) {
        String value = optionalString(root, key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Campo JSON faltante/vacio: " + key);
        }
        return value;
    }

    private static String optionalString(JsonObject root, String key) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return null;
        }
        return root.get(key).getAsString();
    }

    private static long optionalLong(JsonObject root, String key) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return -1L;
        }
        try {
            return root.get(key).getAsLong();
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static boolean optionalBoolean(JsonObject root, String key, boolean fallback) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return root.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static JsonObject optionalObject(JsonObject root, String key) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull() || !root.get(key).isJsonObject()) {
            return null;
        }
        return root.getAsJsonObject(key);
    }

    private static JsonArray optionalArray(JsonObject root, String key) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull() || !root.get(key).isJsonArray()) {
            return null;
        }
        return root.getAsJsonArray(key);
    }

    private record MavenArtifact(String url, String path) {
        static MavenArtifact from(String coordinate, String repoUrl) {
            String repo = (repoUrl == null || repoUrl.isBlank()) ? "https://maven.fabricmc.net/" : repoUrl;
            if (!repo.endsWith("/")) {
                repo += "/";
            }

            String[] parts = coordinate.split(":");
            if (parts.length < 3) {
                throw new IllegalArgumentException("Coordenada Maven invalida: " + coordinate);
            }

            String group = parts[0].replace('.', '/');
            String artifact = parts[1];
            String version = parts[2];
            String classifier = parts.length > 3 ? parts[3] : null;
            String extension = "jar";

            if (version.contains("@")) {
                String[] split = version.split("@", 2);
                version = split[0];
                extension = split[1];
            }
            if (classifier != null && classifier.contains("@")) {
                String[] split = classifier.split("@", 2);
                classifier = split[0];
                extension = split[1];
            }

            String fileName = artifact + "-" + version;
            if (classifier != null && !classifier.isBlank()) {
                fileName += "-" + classifier;
            }
            fileName += "." + extension;

            String path = group + "/" + artifact + "/" + version + "/" + fileName;
            return new MavenArtifact(repo + path, path);
        }
    }
}
