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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.nio.file.attribute.DosFileAttributeView;

final class ModpackManager {

    static final String TEMPLATE_NORMAL = "Normal";
    static final String TEMPLATE_OPTIMIZED = "Optimizada";
    static final String TEMPLATE_CUSTOM = "Custom";
    static final String SECTION_RESOURCEPACKS = "Resourcepacks";
    static final String FILTER_TYPE_ALL = "Todo";
    static final String FILTER_TYPE_MODS = "Mods";
    static final String FILTER_TYPE_RESOURCEPACKS = "Resourcepacks";
    static final String FILTER_SECTION_ALL = "Todas";

    private static final String CATALOG_DIR_NAME = "SemencraftModpacks";
    private static final String PROFILE_FILE_NAME = "modpack-profile.json";
    private static final String OPTIONS_PATCH_FILE = "OptionsPatch/options.txt";
    private static final String GITHUB_OWNER = "XK-GamerX";
    private static final String GITHUB_REPO = "SemencraftModpacks";
    private static final String GITHUB_BRANCH = "main";
    private static final String GITHUB_API_COMMIT_URL = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/commits/" + GITHUB_BRANCH;
    private static final String GITHUB_ZIP_URL = "https://codeload.github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/zip/refs/heads/" + GITHUB_BRANCH;
    private static final String GITHUB_CACHE_DIR_NAME = "modpacks-github-cache";
    private static final String GITHUB_STATE_FILE = "github-state.json";
    private static final long GITHUB_SYNC_MIN_INTERVAL_MS = 120_000L;
    private static final Pattern MODS_TOML_NAME = Pattern.compile("displayName\\s*=\\s*\"([^\"]+)\"");
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Object githubSyncLock = new Object();
    private volatile long lastGithubSyncAttemptMs;

    enum EntryType {
        MOD,
        RESOURCEPACK
    }

    record OptionalEntry(
            String id,
            EntryType type,
            String section,
            String displayName,
            String fileName,
            Path sourcePath,
            byte[] iconBytes
    ) {
        OptionalEntry {
            id = id == null ? "" : id;
            type = type == null ? EntryType.MOD : type;
            section = section == null || section.isBlank() ? "General" : section.trim();
            displayName = displayName == null || displayName.isBlank() ? fileName : displayName.trim();
            fileName = fileName == null ? "" : fileName;
            sourcePath = sourcePath == null ? Path.of(".") : sourcePath;
            iconBytes = iconBytes == null ? null : iconBytes.clone();
        }
    }

    record RequiredEntry(Path sourcePath, Path targetRelativePath) {
        RequiredEntry {
            sourcePath = sourcePath == null ? Path.of(".") : sourcePath;
            targetRelativePath = targetRelativePath == null ? Path.of(".") : targetRelativePath.normalize();
        }
    }

    record Catalog(
            Path rootPath,
            List<OptionalEntry> optionalEntries,
            List<RequiredEntry> requiredEntries,
            List<String> sections
    ) {
        Catalog {
            rootPath = rootPath == null ? Path.of(CATALOG_DIR_NAME).toAbsolutePath().normalize() : rootPath;
            optionalEntries = optionalEntries == null ? List.of() : List.copyOf(optionalEntries);
            requiredEntries = requiredEntries == null ? List.of() : List.copyOf(requiredEntries);
            sections = sections == null ? List.of() : List.copyOf(sections);
        }

        OptionalEntry findOptionalById(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            for (OptionalEntry entry : optionalEntries) {
                if (id.equals(entry.id())) {
                    return entry;
                }
            }
            return null;
        }
    }

    record Selection(
            String selectedTemplate,
            String baseTemplate,
            LinkedHashSet<String> enabledOptionalIds
    ) {
        Selection {
            selectedTemplate = normalizeTemplate(selectedTemplate);
            baseTemplate = normalizeBaseTemplate(baseTemplate);
            enabledOptionalIds = enabledOptionalIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(enabledOptionalIds);
        }

        boolean isEnabled(String entryId) {
            if (entryId == null || entryId.isBlank()) {
                return false;
            }
            return enabledOptionalIds.contains(entryId);
        }
    }

    record SyncResult(int copiedFiles, int removedMods, int removedResourcepacks) {
    }

    Path resolveCatalogRoot() {
        Path githubCached = githubCatalogContentPath().toAbsolutePath().normalize();
        try {
            syncGithubCatalogIfNeeded(false);
        } catch (Exception ignored) {
        }
        if (isValidCatalogRoot(githubCached)) {
            return githubCached;
        }
        Path extractedFallback = findLatestValidExtractRoot();
        if (isValidCatalogRoot(extractedFallback)) {
            return extractedFallback;
        }

        Path launcherLocal = LauncherStorage.launcherDirectory().resolve(CATALOG_DIR_NAME).toAbsolutePath().normalize();
        if (isValidCatalogRoot(launcherLocal)) {
            return launcherLocal;
        }

        Path localProject = Path.of(CATALOG_DIR_NAME).toAbsolutePath().normalize();
        if (isValidCatalogRoot(localProject)) {
            return localProject;
        }
        return githubCached;
    }

    Catalog loadCatalog() throws IOException {
        Path root = resolveCatalogRoot();
        if (!Files.isDirectory(root)) {
            return new Catalog(root, List.of(), List.of(), List.of());
        }

        List<OptionalEntry> optionalEntries = new ArrayList<>();
        List<RequiredEntry> requiredEntries = new ArrayList<>();
        LinkedHashSet<String> sections = new LinkedHashSet<>();

        Path modTypesRoot = root.resolve("Normal").resolve("ModTypes");
        if (Files.isDirectory(modTypesRoot)) {
            try (Stream<Path> sectionDirs = Files.list(modTypesRoot)) {
                sectionDirs
                        .filter(Files::isDirectory)
                        .sorted()
                        .forEach(sectionPath -> {
                            String sectionName = sectionPath.getFileName().toString();
                            sections.add(sectionName);
                            try (Stream<Path> mods = Files.list(sectionPath)) {
                                mods.filter(Files::isRegularFile)
                                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                                        .sorted()
                                        .forEach(modFile -> optionalEntries.add(new OptionalEntry(
                                                buildEntryId(root, EntryType.MOD, modFile),
                                                EntryType.MOD,
                                                sectionName,
                                                readModName(modFile),
                                                modFile.getFileName().toString(),
                                                modFile,
                                                readModIcon(modFile)
                                        )));
                            } catch (Exception ignored) {
                            }
                        });
            }
        }

        Path resourcepacksRoot = root.resolve("Normal").resolve("Profile").resolve("resourcepacks");
        if (Files.isDirectory(resourcepacksRoot)) {
            sections.add(SECTION_RESOURCEPACKS);
            try (Stream<Path> packs = Files.list(resourcepacksRoot)) {
                packs.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(packFile -> optionalEntries.add(new OptionalEntry(
                                buildEntryId(root, EntryType.RESOURCEPACK, packFile),
                                EntryType.RESOURCEPACK,
                                SECTION_RESOURCEPACKS,
                                readResourcepackName(packFile),
                                packFile.getFileName().toString(),
                                packFile,
                                readResourcepackIcon(packFile)
                        )));
            }
        }

        Path requiredRoot = root.resolve("Required");
        if (Files.isDirectory(requiredRoot)) {
            try (Stream<Path> walk = Files.walk(requiredRoot)) {
                walk.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(file -> requiredEntries.add(new RequiredEntry(file, requiredRoot.relativize(file))));
            }
        }

        optionalEntries.sort((a, b) -> {
            int sectionCompare = a.section().compareToIgnoreCase(b.section());
            if (sectionCompare != 0) {
                return sectionCompare;
            }
            return a.displayName().compareToIgnoreCase(b.displayName());
        });

        return new Catalog(root, optionalEntries, requiredEntries, List.copyOf(sections));
    }

    private boolean isValidCatalogRoot(Path root) {
        if (root == null) {
            return false;
        }
        return Files.isDirectory(root.resolve("Normal"))
                && Files.isDirectory(root.resolve("Required"))
                && Files.isDirectory(root.resolve("OptionsPatch"));
    }

    private Path findLatestValidExtractRoot() {
        Path cacheRoot = githubCacheRootPath();
        if (!Files.isDirectory(cacheRoot)) {
            return null;
        }
        try (Stream<Path> entries = Files.list(cacheRoot)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        Path name = path.getFileName();
                        return name != null && name.toString().startsWith("extract-");
                    })
                    .filter(this::isValidCatalogRoot)
                    .sorted((a, b) -> Long.compare(lastModifiedMillis(b), lastModifiedMillis(a)))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void syncGithubCatalogIfNeeded(boolean force) throws IOException {
        synchronized (githubSyncLock) {
            Path contentPath = githubCatalogContentPath();
            long now = System.currentTimeMillis();
            if (!force
                    && isValidCatalogRoot(contentPath)
                    && (now - lastGithubSyncAttemptMs) < GITHUB_SYNC_MIN_INTERVAL_MS) {
                return;
            }
            lastGithubSyncAttemptMs = now;

            String remoteSha = fetchLatestGithubSha();
            if (remoteSha == null || remoteSha.isBlank()) {
                return;
            }
            String localSha = readCachedGithubSha();
            if (!force && remoteSha.equalsIgnoreCase(localSha) && isValidCatalogRoot(contentPath)) {
                return;
            }

            Path extracted = downloadAndExtractGithubCatalog();
            if (!isValidCatalogRoot(extracted)) {
                deleteDirectoryRecursive(extracted);
                throw new IOException("El repo descargado no contiene la estructura esperada de SemencraftModpacks.");
            }
            replaceDirectory(extracted, contentPath);
            writeGithubState(remoteSha);
        }
    }

    private String fetchLatestGithubSha() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GITHUB_API_COMMIT_URL))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SemencraftLauncher")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GitHub API devolvio HTTP " + response.statusCode() + " al consultar commits.");
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            return optionalString(json, "sha", "");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Consulta de GitHub interrumpida.", ex);
        } catch (Exception ex) {
            if (ex instanceof IOException ioEx) {
                throw ioEx;
            }
            throw new IOException("No se pudo consultar el commit del repo de modpacks: " + ex.getMessage(), ex);
        }
    }

    private Path downloadAndExtractGithubCatalog() throws IOException {
        Files.createDirectories(githubCacheRootPath());
        Path tempExtract = Files.createTempDirectory(githubCacheRootPath(), "extract-");
        HttpRequest request = HttpRequest.newBuilder(URI.create(GITHUB_ZIP_URL))
                .header("User-Agent", "SemencraftLauncher")
                .timeout(Duration.ofMinutes(3))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                deleteDirectoryRecursive(tempExtract);
                throw new IOException("No se pudo descargar SemencraftModpacks (HTTP " + response.statusCode() + ").");
            }
            try (InputStream body = response.body();
                 ZipInputStream zip = new ZipInputStream(body)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String rawName = entry.getName();
                    if (rawName == null || rawName.isBlank()) {
                        continue;
                    }
                    String normalized = rawName.replace('\\', '/');
                    int firstSlash = normalized.indexOf('/');
                    if (firstSlash < 0 || firstSlash + 1 >= normalized.length()) {
                        continue;
                    }
                    String relative = normalized.substring(firstSlash + 1);
                    if (relative.isBlank()) {
                        continue;
                    }
                    Path out = tempExtract.resolve(relative).normalize();
                    if (!out.startsWith(tempExtract)) {
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                        continue;
                    }
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(
                            out,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE
                    )) {
                        zip.transferTo(os);
                    }
                }
            }
            return tempExtract;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            deleteDirectoryRecursive(tempExtract);
            throw new IOException("Descarga del repo de modpacks interrumpida.", ex);
        } catch (Exception ex) {
            deleteDirectoryRecursive(tempExtract);
            if (ex instanceof IOException ioEx) {
                throw ioEx;
            }
            throw new IOException("No se pudo descargar/extrair el repo de modpacks: " + ex.getMessage(), ex);
        }
    }

    private void replaceDirectory(Path sourceDir, Path targetDir) throws IOException {
        if (sourceDir == null || !Files.exists(sourceDir)) {
            throw new IOException("No hay contenido de modpacks para instalar.");
        }
        if (!Files.isDirectory(sourceDir)) {
            throw new IOException("La fuente de modpacks no es una carpeta valida: " + sourceDir);
        }
        Files.createDirectories(targetDir.getParent());
        Path tempTarget = targetDir.resolveSibling(targetDir.getFileName().toString() + ".tmp");
        Path backup = targetDir.resolveSibling(targetDir.getFileName().toString() + ".bak");
        deleteDirectoryRecursive(tempTarget);
        deleteDirectoryRecursive(backup);
        try {
            copyDirectoryRecursive(sourceDir, tempTarget);
            if (Files.exists(targetDir)) {
                moveDirectoryWithFallback(targetDir, backup);
                if (Files.exists(targetDir)) {
                    deleteDirectoryRecursive(targetDir);
                }
            }
            moveDirectoryWithFallback(tempTarget, targetDir);
            if (Files.exists(tempTarget)) {
                deleteDirectoryRecursive(tempTarget);
            }
            deleteDirectoryRecursive(backup);
            if (Files.exists(sourceDir)) {
                deleteDirectoryRecursive(sourceDir);
            }
        } catch (Exception ex) {
            deleteDirectoryRecursive(targetDir);
            if (Files.exists(backup)) {
                try {
                    moveDirectoryWithFallback(backup, targetDir);
                } catch (Exception restoreEx) {
                    ex.addSuppressed(restoreEx);
                }
            }
            throw new IOException("No se pudo actualizar el cache del repo de modpacks.", ex);
        } finally {
            if (Files.exists(tempTarget)) {
                deleteDirectoryRecursive(tempTarget);
            }
        }
    }

    private static void moveDirectoryWithFallback(Path sourceDir, Path targetDir) throws IOException {
        if (sourceDir == null || !Files.exists(sourceDir)) {
            return;
        }
        deleteDirectoryRecursive(targetDir);
        try {
            Files.move(sourceDir, targetDir, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception moveEx) {
            copyDirectoryRecursive(sourceDir, targetDir);
        }
    }

    private static void copyDirectoryRecursive(Path sourceDir, Path targetDir) throws IOException {
        if (sourceDir == null || !Files.isDirectory(sourceDir)) {
            throw new IOException("No se puede copiar una carpeta invalida: " + sourceDir);
        }
        deleteDirectoryRecursive(targetDir);
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            paths = walk.sorted().toList();
        }
        for (Path source : paths) {
            Path relative = sourceDir.relativize(source);
            Path destination = relative.toString().isBlank() ? targetDir : targetDir.resolve(relative);
            if (Files.isDirectory(source)) {
                Files.createDirectories(destination);
                continue;
            }
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            clearReadOnly(destination);
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private String readCachedGithubSha() {
        Path state = githubStatePath();
        if (!Files.exists(state)) {
            return "";
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(state, StandardCharsets.UTF_8)).getAsJsonObject();
            return optionalString(json, "sha", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private void writeGithubState(String sha) {
        try {
            Files.createDirectories(githubCacheRootPath());
            JsonObject json = new JsonObject();
            json.addProperty("sha", sha == null ? "" : sha);
            json.addProperty("updatedAt", System.currentTimeMillis());
            Files.writeString(
                    githubStatePath(),
                    json.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception ignored) {
        }
    }

    Selection defaultSelectionForTemplate(Catalog catalog, String template) {
        Catalog safeCatalog = catalog == null ? new Catalog(resolveCatalogRoot(), List.of(), List.of(), List.of()) : catalog;
        String normalizedTemplate = normalizeTemplate(template);
        String baseTemplate = TEMPLATE_CUSTOM.equals(normalizedTemplate) ? TEMPLATE_NORMAL : normalizeBaseTemplate(normalizedTemplate);
        LinkedHashSet<String> enabled = defaultEnabledIds(safeCatalog, baseTemplate);
        return new Selection(normalizedTemplate, baseTemplate, enabled);
    }

    LinkedHashSet<String> defaultEnabledIds(Catalog catalog, String baseTemplate) {
        LinkedHashSet<String> enabled = new LinkedHashSet<>();
        if (catalog == null || catalog.optionalEntries().isEmpty()) {
            return enabled;
        }
        String normalized = normalizeBaseTemplate(baseTemplate);
        for (OptionalEntry entry : catalog.optionalEntries()) {
            if (TEMPLATE_OPTIMIZED.equals(normalized)) {
                if (entry.type() == EntryType.MOD && isOptimizedDisabledSection(entry.section())) {
                    continue;
                }
                if (entry.type() == EntryType.RESOURCEPACK) {
                    continue;
                }
            }
            enabled.add(entry.id());
        }
        return enabled;
    }

    Selection normalizeSelection(Catalog catalog, Selection raw) {
        if (raw == null) {
            return defaultSelectionForTemplate(catalog, TEMPLATE_NORMAL);
        }
        LinkedHashSet<String> validIds = new LinkedHashSet<>();
        for (String id : raw.enabledOptionalIds()) {
            if (catalog != null && catalog.findOptionalById(id) != null) {
                validIds.add(id);
            }
        }
        if (validIds.isEmpty() && catalog != null && !catalog.optionalEntries().isEmpty()) {
            validIds.addAll(defaultEnabledIds(catalog, raw.baseTemplate()));
        }
        return new Selection(raw.selectedTemplate(), raw.baseTemplate(), validIds);
    }

    Selection loadSavedCustomSelection(Catalog catalog) {
        Path profilePath = profilePath();
        if (!Files.exists(profilePath)) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(profilePath, StandardCharsets.UTF_8)).getAsJsonObject();
            String baseTemplate = normalizeBaseTemplate(optionalString(root, "baseTemplate", TEMPLATE_NORMAL));
            LinkedHashSet<String> enabled = new LinkedHashSet<>();
            JsonArray array = root.has("enabled") && root.get("enabled").isJsonArray() ? root.getAsJsonArray("enabled") : null;
            if (array != null) {
                for (JsonElement element : array) {
                    if (element != null && element.isJsonPrimitive()) {
                        String id = element.getAsString();
                        if (catalog == null || catalog.findOptionalById(id) != null) {
                            enabled.add(id);
                        }
                    }
                }
            }
            if (enabled.isEmpty()) {
                enabled.addAll(defaultEnabledIds(catalog, baseTemplate));
            }
            return new Selection(TEMPLATE_CUSTOM, baseTemplate, enabled);
        } catch (Exception ignored) {
            return null;
        }
    }

    void saveCustomSelection(Selection selection) {
        if (selection == null) {
            return;
        }
        try {
            Files.createDirectories(LauncherStorage.launcherDirectory());
            JsonObject json = new JsonObject();
            json.addProperty("baseTemplate", normalizeBaseTemplate(selection.baseTemplate()));
            JsonArray enabled = new JsonArray();
            for (String id : selection.enabledOptionalIds()) {
                enabled.add(id);
            }
            json.add("enabled", enabled);
            Files.writeString(
                    profilePath(),
                    json.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception ignored) {
        }
    }

    SyncResult synchronizeForLaunch(Catalog catalog, Selection selection, Path gameDir) throws IOException {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(gameDir, "gameDir");

        if (!Files.isDirectory(catalog.rootPath())) {
            throw new IOException("No se encontro la carpeta SemencraftModpacks en: " + catalog.rootPath());
        }

        Files.createDirectories(gameDir);
        Path modsDir = gameDir.resolve("mods");
        Path resourcepacksDir = gameDir.resolve("resourcepacks");
        Files.createDirectories(modsDir);
        Files.createDirectories(resourcepacksDir);
        unlockTreeForSync(modsDir);
        unlockTreeForSync(resourcepacksDir);

        int copiedFiles = 0;
        Set<String> expectedMods = new LinkedHashSet<>();
        Set<String> expectedResourcepacks = new LinkedHashSet<>();
        Map<Path, String> sourceHashCache = new LinkedHashMap<>();

        for (RequiredEntry required : catalog.requiredEntries()) {
            Path relative = required.targetRelativePath().normalize();
            Path target = gameDir.resolve(relative).normalize();
            if (!target.startsWith(gameDir.toAbsolutePath().normalize())) {
                continue;
            }
            if (copyFileIfChanged(required.sourcePath(), target, sourceHashCache)) {
                copiedFiles++;
            }
            rememberManagedTarget(relative, expectedMods, expectedResourcepacks);
        }

        for (String enabledId : selection.enabledOptionalIds()) {
            OptionalEntry entry = catalog.findOptionalById(enabledId);
            if (entry == null) {
                continue;
            }
            Path relativeTarget = entry.type() == EntryType.MOD
                    ? Path.of("mods").resolve(entry.fileName())
                    : Path.of("resourcepacks").resolve(entry.fileName());
            Path target = gameDir.resolve(relativeTarget).normalize();
            if (!target.startsWith(gameDir.toAbsolutePath().normalize())) {
                continue;
            }
            if (copyFileIfChanged(entry.sourcePath(), target, sourceHashCache)) {
                copiedFiles++;
            }
            rememberManagedTarget(relativeTarget, expectedMods, expectedResourcepacks);
        }

        int removedMods = removeUnexpectedFiles(modsDir, expectedMods);
        int removedResourcepacks = removeUnexpectedFiles(resourcepacksDir, expectedResourcepacks);
        copiedFiles += applyOptionsPatchIfNeeded(catalog, selection, gameDir);
        applyLockHints(modsDir, expectedMods);
        applyLockHints(resourcepacksDir, expectedResourcepacks);

        return new SyncResult(copiedFiles, removedMods, removedResourcepacks);
    }

    static String normalizeTemplate(String value) {
        if (value == null || value.isBlank()) {
            return TEMPLATE_NORMAL;
        }
        String cleaned = value.trim();
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.equals("custom")) {
            return TEMPLATE_CUSTOM;
        }
        if (lower.equals("optimizado") || lower.equals("optimizada")) {
            return TEMPLATE_OPTIMIZED;
        }
        if (lower.equals("normal")) {
            return TEMPLATE_NORMAL;
        }
        return TEMPLATE_NORMAL;
    }

    private static String normalizeBaseTemplate(String value) {
        String normalized = normalizeTemplate(value);
        if (TEMPLATE_CUSTOM.equals(normalized)) {
            return TEMPLATE_NORMAL;
        }
        return normalized;
    }

    private static boolean isOptimizedDisabledSection(String sectionName) {
        if (sectionName == null || sectionName.isBlank()) {
            return false;
        }
        String lower = sectionName.toLowerCase(Locale.ROOT);
        return lower.contains("calidad") || lower.contains("quality") || lower.contains("visual");
    }

    private static String buildEntryId(Path root, EntryType type, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        return type.name().toLowerCase(Locale.ROOT) + ":" + relative;
    }

    private static String readModName(Path jarFile) {
        String fallback = stripExtension(jarFile.getFileName().toString());
        try (ZipFile zip = new ZipFile(jarFile.toFile())) {
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric != null) {
                try (InputStream in = zip.getInputStream(fabric)) {
                    JsonObject obj = JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    String name = optionalString(obj, "name", null);
                    if (name != null && !name.isBlank()) {
                        return name.trim();
                    }
                    String id = optionalString(obj, "id", null);
                    if (id != null && !id.isBlank()) {
                        return id.trim();
                    }
                }
            }
            ZipEntry modsToml = zip.getEntry("META-INF/mods.toml");
            if (modsToml != null) {
                try (InputStream in = zip.getInputStream(modsToml)) {
                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    Matcher matcher = MODS_TOML_NAME.matcher(text);
                    if (matcher.find()) {
                        return matcher.group(1).trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static byte[] readModIcon(Path jarFile) {
        try (ZipFile zip = new ZipFile(jarFile.toFile())) {
            String preferredPath = readPreferredFabricIconPath(zip);
            if (preferredPath != null && !preferredPath.isBlank()) {
                byte[] preferred = readZipEntryBytes(zip, preferredPath, 2_000_000);
                if (preferred != null && preferred.length > 0) {
                    return preferred;
                }
            }
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null || entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name == null) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".png")) {
                    continue;
                }
                if (lower.startsWith("meta-inf/")) {
                    continue;
                }
                byte[] bytes = readZipEntryBytes(zip, name, 2_000_000);
                if (bytes != null && bytes.length > 0) {
                    return bytes;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String readPreferredFabricIconPath(ZipFile zip) {
        if (zip == null) {
            return null;
        }
        try {
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(fabric)) {
                JsonObject obj = JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonElement icon = obj.get("icon");
                if (icon == null || icon.isJsonNull()) {
                    return null;
                }
                if (icon.isJsonPrimitive()) {
                    return icon.getAsString();
                }
                if (icon.isJsonObject()) {
                    String selectedPath = null;
                    int bestSize = -1;
                    for (Map.Entry<String, JsonElement> entry : icon.getAsJsonObject().entrySet()) {
                        if (entry.getValue() == null || !entry.getValue().isJsonPrimitive()) {
                            continue;
                        }
                        String path = entry.getValue().getAsString();
                        int size = -1;
                        try {
                            size = Integer.parseInt(entry.getKey());
                        } catch (Exception ignored) {
                        }
                        if (selectedPath == null || size > bestSize) {
                            selectedPath = path;
                            bestSize = size;
                        }
                    }
                    return selectedPath;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] readZipEntryBytes(ZipFile zip, String entryPath, int maxBytes) {
        if (zip == null || entryPath == null || entryPath.isBlank()) {
            return null;
        }
        try {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null || entry.isDirectory()) {
                return null;
            }
            long size = entry.getSize();
            if (size > maxBytes) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] data = in.readAllBytes();
                if (data.length > maxBytes) {
                    return null;
                }
                return data;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readResourcepackName(Path packPath) {
        String fallback = stripExtension(packPath.getFileName().toString());
        String lower = packPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip")) {
            return fallback;
        }
        try (ZipFile zip = new ZipFile(packPath.toFile())) {
            ZipEntry mcmeta = zip.getEntry("pack.mcmeta");
            if (mcmeta == null) {
                return fallback;
            }
            try (InputStream in = zip.getInputStream(mcmeta)) {
                JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject packObj = root.has("pack") && root.get("pack").isJsonObject() ? root.getAsJsonObject("pack") : null;
                if (packObj == null || !packObj.has("description")) {
                    return fallback;
                }
                JsonElement description = packObj.get("description");
                if (description.isJsonPrimitive()) {
                    String text = description.getAsString();
                    return text == null || text.isBlank() ? fallback : text.trim();
                }
                if (description.isJsonObject()) {
                    JsonObject obj = description.getAsJsonObject();
                    String text = optionalString(obj, "text", null);
                    if (text != null && !text.isBlank()) {
                        return text.trim();
                    }
                    String translate = optionalString(obj, "translate", null);
                    if (translate != null && !translate.isBlank()) {
                        return translate.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static byte[] readResourcepackIcon(Path packPath) {
        if (packPath == null || !Files.exists(packPath)) {
            return null;
        }
        String lower = packPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(packPath.toFile())) {
                byte[] icon = readZipEntryBytes(zip, "pack.png", 2_000_000);
                if (icon != null && icon.length > 0) {
                    return icon;
                }
            } catch (Exception ignored) {
            }
            return null;
        }
        if (Files.isDirectory(packPath)) {
            Path packIcon = packPath.resolve("pack.png");
            try {
                if (Files.exists(packIcon) && Files.size(packIcon) <= 2_000_000) {
                    return Files.readAllBytes(packIcon);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static int applyOptionsPatchIfNeeded(Catalog catalog, Selection selection, Path gameDir) throws IOException {
        if (!shouldApplyOptionsPatch(selection)) {
            return 0;
        }
        Path patchFile = catalog.rootPath().resolve(OPTIONS_PATCH_FILE);
        if (!Files.exists(patchFile)) {
            return 0;
        }

        Path targetOptions = gameDir.resolve("options.txt");
        List<String> patchLines = Files.readAllLines(patchFile, StandardCharsets.UTF_8);
        if (patchLines.isEmpty()) {
            return 0;
        }
        List<String> existing = Files.exists(targetOptions)
                ? Files.readAllLines(targetOptions, StandardCharsets.UTF_8)
                : List.of();
        List<String> merged = mergeOptions(existing, patchLines);
        if (!existing.equals(merged)) {
            Files.write(
                    targetOptions,
                    merged,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            return 1;
        }
        return 0;
    }

    private static boolean shouldApplyOptionsPatch(Selection selection) {
        if (selection == null) {
            return false;
        }
        if (TEMPLATE_OPTIMIZED.equals(normalizeTemplate(selection.selectedTemplate()))) {
            return true;
        }
        return TEMPLATE_OPTIMIZED.equals(normalizeBaseTemplate(selection.baseTemplate()));
    }

    private static List<String> mergeOptions(List<String> existing, List<String> patchLines) {
        if (existing == null || existing.isEmpty()) {
            return new ArrayList<>(patchLines);
        }
        if (patchLines == null || patchLines.isEmpty()) {
            return new ArrayList<>(existing);
        }

        LinkedHashMap<String, String> patchByKey = new LinkedHashMap<>();
        List<String> patchWithoutKey = new ArrayList<>();
        for (String line : patchLines) {
            String key = optionKey(line);
            if (key == null) {
                patchWithoutKey.add(line);
            } else {
                patchByKey.put(key, line);
            }
        }

        List<String> out = new ArrayList<>(Math.max(existing.size(), patchLines.size()));
        Set<String> usedPatch = new LinkedHashSet<>();
        for (String line : existing) {
            String key = optionKey(line);
            if (key != null && patchByKey.containsKey(key)) {
                out.add(patchByKey.get(key));
                usedPatch.add(key);
            } else {
                out.add(line);
            }
        }

        for (Map.Entry<String, String> entry : patchByKey.entrySet()) {
            if (!usedPatch.contains(entry.getKey())) {
                out.add(entry.getValue());
            }
        }
        out.addAll(patchWithoutKey);
        return out;
    }

    private static String optionKey(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        int idx = line.indexOf(':');
        if (idx <= 0) {
            return null;
        }
        return line.substring(0, idx).trim();
    }

    private static void rememberManagedTarget(Path relativeTarget, Set<String> expectedMods, Set<String> expectedResourcepacks) {
        if (relativeTarget == null) {
            return;
        }
        Path normalized = relativeTarget.normalize();
        if (normalized.getNameCount() < 2) {
            return;
        }
        String first = normalized.getName(0).toString().toLowerCase(Locale.ROOT);
        Path childPath = normalized.subpath(1, normalized.getNameCount());
        String key = normalizeRelativeKey(childPath);
        if (key.isBlank()) {
            return;
        }
        if ("mods".equals(first)) {
            expectedMods.add(key);
        } else if ("resourcepacks".equals(first)) {
            expectedResourcepacks.add(key);
        }
    }

    private static int removeUnexpectedFiles(Path managedRoot, Set<String> expectedRelativeKeys) throws IOException {
        Files.createDirectories(managedRoot);
        List<Path> existingFiles;
        try (Stream<Path> walk = Files.walk(managedRoot)) {
            existingFiles = walk.filter(Files::isRegularFile).toList();
        }
        int removed = 0;
        for (Path file : existingFiles) {
            Path relative = managedRoot.relativize(file);
            String key = normalizeRelativeKey(relative);
            if (!expectedRelativeKeys.contains(key)) {
                clearReadOnly(file);
                Files.deleteIfExists(file);
                removed++;
            }
        }
        deleteEmptyDirectories(managedRoot);
        return removed;
    }

    private static void deleteEmptyDirectories(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Collections.reverseOrder())
                    .filter(Files::isDirectory)
                    .filter(path -> !path.equals(root))
                    .forEach(path -> {
                        try (Stream<Path> children = Files.list(path)) {
                            if (children.findAny().isEmpty()) {
                                clearReadOnly(path);
                                Files.deleteIfExists(path);
                            }
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    private static void deleteDirectoryRecursive(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Collections.reverseOrder())
                    .forEach(path -> {
                        try {
                            clearReadOnly(path);
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    private static boolean copyFileIfChanged(Path source, Path target, Map<Path, String> sourceHashCache) throws IOException {
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Path normalizedSource = source.toAbsolutePath().normalize();
        String sourceHash = sourceHashCache.get(normalizedSource);
        if (sourceHash == null || sourceHash.isBlank()) {
            sourceHash = sha256Hex(source);
            sourceHashCache.put(normalizedSource, sourceHash);
        }
        if (Files.exists(target)) {
            String targetHash = sha256Hex(target);
            if (sourceHash.equalsIgnoreCase(targetHash)) {
                return false;
            }
        }
        clearReadOnly(target);
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(
                     target,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE
             )) {
            in.transferTo(out);
        }
        try {
            Files.setLastModifiedTime(target, Files.getLastModifiedTime(source));
        } catch (Exception ignored) {
        }
        return true;
    }

    private static void unlockTreeForSync(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.forEach(ModpackManager::clearReadOnly);
        } catch (Exception ignored) {
        }
    }

    private static void applyLockHints(Path managedRoot, Set<String> expectedRelativeKeys) {
        if (managedRoot == null || expectedRelativeKeys == null || expectedRelativeKeys.isEmpty()) {
            return;
        }
        try (Stream<Path> walk = Files.walk(managedRoot)) {
            walk.filter(Files::isRegularFile)
                    .forEach(file -> {
                        Path relative = managedRoot.relativize(file);
                        String key = normalizeRelativeKey(relative);
                        if (!expectedRelativeKeys.contains(key)) {
                            return;
                        }
                        try {
                            setReadOnly(file);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
        try {
            DosFileAttributeView dosView = Files.getFileAttributeView(managedRoot, DosFileAttributeView.class);
            if (dosView != null) {
                dosView.setHidden(true);
            }
        } catch (Exception ignored) {
        }
    }

    private static void setReadOnly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            path.toFile().setWritable(false, false);
            path.toFile().setReadOnly();
        } catch (Exception ignored) {
        }
        try {
            DosFileAttributeView dosView = Files.getFileAttributeView(path, DosFileAttributeView.class);
            if (dosView != null) {
                dosView.setReadOnly(true);
            }
        } catch (Exception ignored) {
        }
    }

    private static void clearReadOnly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            path.toFile().setWritable(true, false);
        } catch (Exception ignored) {
        }
        try {
            DosFileAttributeView dosView = Files.getFileAttributeView(path, DosFileAttributeView.class);
            if (dosView != null) {
                dosView.setReadOnly(false);
            }
        } catch (Exception ignored) {
        }
    }

    private static String sha256Hex(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception ex) {
            throw new IOException("No se pudo inicializar SHA-256.", ex);
        }
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
        StringBuilder out = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            out.append(String.format(Locale.ROOT, "%02x", b));
        }
        return out.toString();
    }

    private static String normalizeRelativeKey(Path path) {
        if (path == null) {
            return "";
        }
        return path.normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static long lastModifiedMillis(Path path) {
        if (path == null) {
            return 0L;
        }
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private Path profilePath() {
        return LauncherStorage.launcherDirectory().resolve(PROFILE_FILE_NAME);
    }

    private Path githubCacheRootPath() {
        return LauncherStorage.launcherDirectory().resolve(GITHUB_CACHE_DIR_NAME);
    }

    private Path githubCatalogContentPath() {
        return githubCacheRootPath().resolve(CATALOG_DIR_NAME);
    }

    private Path githubStatePath() {
        return githubCacheRootPath().resolve(GITHUB_STATE_FILE);
    }

    private static String optionalString(JsonObject root, String key, String fallback) {
        if (root == null || key == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return root.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String stripExtension(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }
}
