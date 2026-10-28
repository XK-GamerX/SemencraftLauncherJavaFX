package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
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
import java.util.UUID;
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
    private static final String RESOURCEPACK_PROFILE_FILE_NAME = "resourcepack-profile.json";
    private static final String OPTIONS_PATCH_FILE = "OptionsPatch/options.txt";
    private static final String GITHUB_OWNER = "XK-GamerX";
    private static final String GITHUB_REPO = "SemencraftModpacks";
    private static final String GITHUB_BRANCH = "main";
    private static final String GITHUB_API_COMMIT_URL = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/commits/" + GITHUB_BRANCH;
    private static final String GITHUB_ZIP_URL = "https://codeload.github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/zip/refs/heads/" + GITHUB_BRANCH;
    private static final String GITHUB_LFS_MEDIA_BASE_URL = "https://media.githubusercontent.com/media/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/" + GITHUB_BRANCH + "/";
    private static final String GIT_LFS_POINTER_HEADER = "version https://git-lfs.github.com/spec/v1";
    private static final Pattern GIT_LFS_OID = Pattern.compile("(?m)^oid\\s+sha256:([0-9a-fA-F]{64})\\s*$");
    private static final Pattern GIT_LFS_SIZE = Pattern.compile("(?m)^size\\s+(\\d+)\\s*$");
    private static final long MAX_LFS_POINTER_BYTES = 8_192L;
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
            String description,
            String fileName,
            Path sourcePath,
            byte[] iconBytes
    ) {
        OptionalEntry {
            id = id == null ? "" : id;
            type = type == null ? EntryType.MOD : type;
            section = section == null || section.isBlank() ? "General" : section.trim();
            displayName = displayName == null || displayName.isBlank() ? fileName : displayName.trim();
            description = description == null ? "" : description.trim();
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
                                                "",
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
                                readResourcepackDescription(packFile),
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
                // A cached GitHub archive can still contain Git LFS pointer files.
                // Hydrate them even when the repository SHA did not change.
                hydrateGithubLfsPointers(contentPath);
                return;
            }
            lastGithubSyncAttemptMs = now;

            String remoteSha = fetchLatestGithubSha();
            if (remoteSha == null || remoteSha.isBlank()) {
                return;
            }
            String localSha = readCachedGithubSha();
            if (!force && remoteSha.equalsIgnoreCase(localSha) && isValidCatalogRoot(contentPath)) {
                hydrateGithubLfsPointers(contentPath);
                return;
            }

            Path extracted = downloadAndExtractGithubCatalog();
            if (!isValidCatalogRoot(extracted)) {
                deleteDirectoryRecursive(extracted);
                throw new IOException("El repo descargado no contiene la estructura esperada de SemencraftModpacks.");
            }
            hydrateGithubLfsPointers(extracted);
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

    /**
     * GitHub's codeload ZIP archives may contain Git LFS pointer text instead of
     * the real binary. This is especially important for large .jar files such as
     * watermedia_binaries. Resolve every pointer before the catalog is used.
     */
    private void hydrateGithubLfsPointers(Path catalogRoot) throws IOException {
        if (catalogRoot == null || !Files.isDirectory(catalogRoot)) {
            return;
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(catalogRoot)) {
            files = walk.filter(Files::isRegularFile).toList();
        }

        for (Path file : files) {
            LfsPointer pointer = readGitLfsPointer(file);
            if (pointer == null) {
                continue;
            }
            Path relative = catalogRoot.relativize(file);
            downloadGithubLfsObject(relative, file, pointer);
        }
    }

    private LfsPointer readGitLfsPointer(Path file) {
        try {
            long fileSize = Files.size(file);
            if (fileSize <= 0L || fileSize > MAX_LFS_POINTER_BYTES) {
                return null;
            }

            String pointerText = Files.readString(file, StandardCharsets.UTF_8);
            if (!pointerText.startsWith(GIT_LFS_POINTER_HEADER)) {
                return null;
            }

            Matcher oidMatcher = GIT_LFS_OID.matcher(pointerText);
            Matcher sizeMatcher = GIT_LFS_SIZE.matcher(pointerText);
            if (!oidMatcher.find() || !sizeMatcher.find()) {
                return null;
            }

            long expectedSize = Long.parseLong(sizeMatcher.group(1));
            if (expectedSize <= 0L) {
                return null;
            }
            return new LfsPointer(oidMatcher.group(1).toLowerCase(Locale.ROOT), expectedSize);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void downloadGithubLfsObject(Path relativePath, Path destination, LfsPointer pointer) throws IOException {
        String relativeUrl = encodeGithubPath(relativePath);
        URI uri = URI.create(GITHUB_LFS_MEDIA_BASE_URL + relativeUrl);
        Path tempFile = destination.resolveSibling(destination.getFileName() + ".lfs-" + UUID.randomUUID() + ".tmp");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", "SemencraftLauncher")
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GitHub LFS devolvio HTTP " + response.statusCode() + " para " + relativePath);
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long downloaded = 0L;
            try (InputStream input = response.body();
                 OutputStream output = Files.newOutputStream(
                         tempFile,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE
                 )) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    downloaded += read;
                }
            }

            if (downloaded != pointer.size()) {
                throw new IOException(
                        "Descarga Git LFS incompleta para " + relativePath
                                + ": esperados " + pointer.size() + " bytes, recibidos " + downloaded
                );
            }

            String actualSha = toHex(digest.digest());
            if (!actualSha.equalsIgnoreCase(pointer.sha256())) {
                throw new IOException("SHA-256 invalido al descargar Git LFS: " + relativePath);
            }

            try {
                Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicMoveFailed) {
                Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Descarga Git LFS interrumpida: " + relativePath, ex);
        } catch (Exception ex) {
            if (ex instanceof IOException ioEx) {
                throw ioEx;
            }
            throw new IOException("No se pudo descargar el objeto Git LFS " + relativePath + ": " + ex.getMessage(), ex);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static String encodeGithubPath(Path path) {
        StringBuilder encoded = new StringBuilder();
        for (Path segment : path) {
            if (!encoded.isEmpty()) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(segment.toString(), StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return encoded.toString();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0x0F, 16));
            result.append(Character.forDigit(value & 0x0F, 16));
        }
        return result.toString();
    }

    private static void validateJarFile(Path jarPath) throws IOException {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            throw new IOException("Falta un mod requerido: " + jarPath);
        }
        LfsPointer pointer = readGitLfsPointerStatic(jarPath);
        if (pointer != null) {
            throw new IOException(
                    "El mod " + jarPath.getFileName()
                            + " sigue siendo un puntero de Git LFS y no un JAR real."
            );
        }
        try (ZipFile ignored = new ZipFile(jarPath.toFile())) {
            // Opening the ZIP is enough to verify the central directory exists.
        } catch (Exception ex) {
            throw new IOException("El mod descargado esta corrupto o incompleto: " + jarPath.getFileName(), ex);
        }
    }

    private static LfsPointer readGitLfsPointerStatic(Path file) {
        try {
            long fileSize = Files.size(file);
            if (fileSize <= 0L || fileSize > MAX_LFS_POINTER_BYTES) {
                return null;
            }
            String pointerText = Files.readString(file, StandardCharsets.UTF_8);
            if (!pointerText.startsWith(GIT_LFS_POINTER_HEADER)) {
                return null;
            }
            Matcher oidMatcher = GIT_LFS_OID.matcher(pointerText);
            Matcher sizeMatcher = GIT_LFS_SIZE.matcher(pointerText);
            if (!oidMatcher.find() || !sizeMatcher.find()) {
                return null;
            }
            long expectedSize = Long.parseLong(sizeMatcher.group(1));
            return expectedSize > 0L
                    ? new LfsPointer(oidMatcher.group(1).toLowerCase(Locale.ROOT), expectedSize)
                    : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private record LfsPointer(String sha256, long size) {
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
            if (entry.type() == EntryType.RESOURCEPACK) {
                continue;
            }
            if (TEMPLATE_OPTIMIZED.equals(normalized)) {
                if (entry.type() == EntryType.MOD && isOptimizedDisabledSection(entry.section())) {
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

    List<OptionalEntry> resourcepackEntries(Catalog catalog) {
        if (catalog == null || catalog.optionalEntries().isEmpty()) {
            return List.of();
        }
        List<OptionalEntry> resourcepacks = new ArrayList<>();
        for (OptionalEntry entry : catalog.optionalEntries()) {
            if (entry.type() == EntryType.RESOURCEPACK) {
                resourcepacks.add(entry);
            }
        }
        resourcepacks.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return List.copyOf(resourcepacks);
    }

    List<String> loadAppliedResourcepackSelection(Catalog catalog, Path gameDir) {
        List<String> fromOptions = readSelectedResourcepacksFromOptions(catalog, gameDir);
        if (!fromOptions.isEmpty()) {
            return fromOptions;
        }
        return loadSavedResourcepackSelection(catalog);
    }

    List<String> loadSavedResourcepackSelection(Catalog catalog) {
        Path profilePath = resourcepackProfilePath();
        if (!Files.exists(profilePath)) {
            return List.of();
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(profilePath, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray array = root.has("selected") && root.get("selected").isJsonArray()
                    ? root.getAsJsonArray("selected")
                    : null;
            if (array == null) {
                return List.of();
            }
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (JsonElement element : array) {
                if (element == null || !element.isJsonPrimitive()) {
                    continue;
                }
                String id = element.getAsString();
                OptionalEntry entry = catalog == null ? null : catalog.findOptionalById(id);
                if (entry != null && entry.type() == EntryType.RESOURCEPACK) {
                    ids.add(id);
                }
            }
            return List.copyOf(ids);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    void saveResourcepackSelection(List<String> selectedIds) {
        try {
            Files.createDirectories(LauncherStorage.launcherDirectory());
            JsonObject root = new JsonObject();
            JsonArray selected = new JsonArray();
            if (selectedIds != null) {
                for (String id : selectedIds) {
                    if (id != null && !id.isBlank()) {
                        selected.add(id);
                    }
                }
            }
            root.add("selected", selected);
            Files.writeString(
                    resourcepackProfilePath(),
                    root.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception ignored) {
        }
    }

    void applyResourcepackSelectionToGame(Catalog catalog, Path gameDir, List<String> orderedSelectedIds) throws IOException {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(gameDir, "gameDir");

        Files.createDirectories(gameDir);
        Path resourcepacksDir = gameDir.resolve("resourcepacks");
        Files.createDirectories(resourcepacksDir);
        unlockTreeForSync(resourcepacksDir);

        Map<Path, String> sourceHashCache = new LinkedHashMap<>();
        Set<String> expectedResourcepacks = new LinkedHashSet<>();
        syncAllManagedResourcepacks(catalog, resourcepacksDir, expectedResourcepacks, sourceHashCache);
        removeUnexpectedFiles(resourcepacksDir, expectedResourcepacks);
        applyLockHints(resourcepacksDir, expectedResourcepacks);
        writeSelectedResourcepacks(catalog, gameDir, orderedSelectedIds);
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
            if (relative.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                validateJarFile(required.sourcePath());
            }
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
            if (entry == null || entry.type() != EntryType.MOD) {
                continue;
            }
            Path relativeTarget = entry.type() == EntryType.MOD
                    ? Path.of("mods").resolve(entry.fileName())
                    : Path.of("resourcepacks").resolve(entry.fileName());
            if (entry.type() == EntryType.MOD) {
                validateJarFile(entry.sourcePath());
            }
            Path target = gameDir.resolve(relativeTarget).normalize();
            if (!target.startsWith(gameDir.toAbsolutePath().normalize())) {
                continue;
            }
            if (copyFileIfChanged(entry.sourcePath(), target, sourceHashCache)) {
                copiedFiles++;
            }
            rememberManagedTarget(relativeTarget, expectedMods, expectedResourcepacks);
        }

        copiedFiles += syncAllManagedResourcepacks(catalog, resourcepacksDir, expectedResourcepacks, sourceHashCache);

        int removedMods = removeUnexpectedFiles(modsDir, expectedMods);
        int removedResourcepacks = removeUnexpectedFiles(resourcepacksDir, expectedResourcepacks);
        copiedFiles += applyOptionsPatchIfNeeded(catalog, selection, gameDir);
        copiedFiles += writeSelectedResourcepacks(catalog, gameDir, loadAppliedResourcepackSelection(catalog, gameDir));
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
        return packPath == null ? "" : stripExtension(packPath.getFileName().toString());
    }

    private static String readResourcepackDescription(Path packPath) {
        String lower = packPath == null ? "" : packPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip")) {
            return "";
        }
        try (ZipFile zip = new ZipFile(packPath.toFile())) {
            ZipEntry mcmeta = zip.getEntry("pack.mcmeta");
            if (mcmeta == null) {
                return "";
            }
            try (InputStream in = zip.getInputStream(mcmeta)) {
                JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject packObj = root.has("pack") && root.get("pack").isJsonObject() ? root.getAsJsonObject("pack") : null;
                if (packObj == null || !packObj.has("description")) {
                    return "";
                }
                return flattenDescription(packObj.get("description"));
            }
        } catch (Exception ignored) {
            return "";
        }
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

    private static int syncAllManagedResourcepacks(
            Catalog catalog,
            Path resourcepacksDir,
            Set<String> expectedResourcepacks,
            Map<Path, String> sourceHashCache
    ) throws IOException {
        if (catalog == null || resourcepacksDir == null) {
            return 0;
        }
        int copiedFiles = 0;
        for (OptionalEntry entry : catalog.optionalEntries()) {
            if (entry.type() != EntryType.RESOURCEPACK) {
                continue;
            }
            Path relativeTarget = Path.of("resourcepacks").resolve(entry.fileName());
            Path target = resourcepacksDir.resolve(entry.fileName()).normalize();
            if (!target.startsWith(resourcepacksDir.toAbsolutePath().normalize())) {
                continue;
            }
            if (copyFileIfChanged(entry.sourcePath(), target, sourceHashCache)) {
                copiedFiles++;
            }
            rememberManagedTarget(relativeTarget, Set.of(), expectedResourcepacks);
        }
        return copiedFiles;
    }

    private static int writeSelectedResourcepacks(Catalog catalog, Path gameDir, List<String> orderedSelectedIds) throws IOException {
        Path optionsPath = gameDir.resolve("options.txt");
        List<String> existing = Files.exists(optionsPath)
                ? Files.readAllLines(optionsPath, StandardCharsets.UTF_8)
                : List.of();
        List<String> merged = mergeOptions(existing, List.of(buildResourcepacksLine(catalog, orderedSelectedIds)));
        if (!existing.equals(merged)) {
            Files.write(
                    optionsPath,
                    merged,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            return 1;
        }
        return 0;
    }

    private static String buildResourcepacksLine(Catalog catalog, List<String> orderedSelectedIds) {
        JsonArray selected = new JsonArray();
        selected.add("vanilla");
        selected.add("fabric");
        if (catalog != null && orderedSelectedIds != null) {
            LinkedHashSet<String> used = new LinkedHashSet<>();
            for (String id : orderedSelectedIds) {
                if (id == null || id.isBlank() || !used.add(id)) {
                    continue;
                }
                OptionalEntry entry = catalog.findOptionalById(id);
                if (entry == null || entry.type() != EntryType.RESOURCEPACK || entry.fileName().isBlank()) {
                    continue;
                }
                selected.add("file/" + entry.fileName());
            }
        }
        return "resourcePacks:" + selected;
    }

    private List<String> readSelectedResourcepacksFromOptions(Catalog catalog, Path gameDir) {
        if (catalog == null || gameDir == null) {
            return List.of();
        }
        Path optionsPath = gameDir.resolve("options.txt");
        if (!Files.exists(optionsPath)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(optionsPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (!line.startsWith("resourcePacks:")) {
                    continue;
                }
                int idx = line.indexOf(':');
                if (idx < 0 || idx + 1 >= line.length()) {
                    continue;
                }
                JsonArray array = JsonParser.parseString(line.substring(idx + 1)).getAsJsonArray();
                LinkedHashSet<String> ordered = new LinkedHashSet<>();
                for (JsonElement element : array) {
                    if (element == null || !element.isJsonPrimitive()) {
                        continue;
                    }
                    String value = element.getAsString();
                    if (value == null || !value.startsWith("file/")) {
                        continue;
                    }
                    String fileName = value.substring("file/".length());
                    for (OptionalEntry entry : catalog.optionalEntries()) {
                        if (entry.type() == EntryType.RESOURCEPACK && fileName.equals(entry.fileName())) {
                            ordered.add(entry.id());
                            break;
                        }
                    }
                }
                return List.copyOf(ordered);
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
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

    private Path resourcepackProfilePath() {
        return LauncherStorage.launcherDirectory().resolve(RESOURCEPACK_PROFILE_FILE_NAME);
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

    private static String flattenDescription(JsonElement description) {
        if (description == null || description.isJsonNull()) {
            return "";
        }
        if (description.isJsonPrimitive()) {
            String text = description.getAsString();
            return text == null ? "" : text.trim();
        }
        if (description.isJsonObject()) {
            JsonObject obj = description.getAsJsonObject();
            StringBuilder builder = new StringBuilder();
            appendDescriptionPart(builder, optionalString(obj, "text", ""));
            appendDescriptionPart(builder, optionalString(obj, "translate", ""));
            JsonElement extra = obj.get("extra");
            if (extra != null && extra.isJsonArray()) {
                for (JsonElement item : extra.getAsJsonArray()) {
                    appendDescriptionPart(builder, flattenDescription(item));
                }
            }
            return builder.toString().trim();
        }
        if (description.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonElement item : description.getAsJsonArray()) {
                appendDescriptionPart(builder, flattenDescription(item));
            }
            return builder.toString().trim();
        }
        return "";
    }

    private static void appendDescriptionPart(StringBuilder builder, String text) {
        if (builder == null || text == null || text.isBlank()) {
            return;
        }
        text = text.replaceAll("§.", "").trim();
        if (text.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(text);
    }

    private static String stripExtension(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }
}
