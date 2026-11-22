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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final String INSTALL_STATE_FILE_NAME = "install-state.json";
    private static final String OPTIONS_PATCH_STATE_FILE_NAME = "options-patch-state.json";
    private static final String OPTIONS_PATCH_FILE = "OptionsPatch/options.txt";
    private static final String GITHUB_OWNER = "XK-GamerX";
    private static final String GITHUB_REPO = "SemencraftModpacks";
    private static final String GITHUB_BRANCH = "main";
    private static final String GITHUB_API_COMMIT_URL = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/commits/" + GITHUB_BRANCH;
    private static final String GITHUB_API_TREE_URL = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/git/trees/%s?recursive=1";
    private static final String GITHUB_RAW_BASE_URL = "https://raw.githubusercontent.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/";
    private static final String GITHUB_ZIP_URL = "https://codeload.github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/zip/refs/heads/" + GITHUB_BRANCH;
    private static final String GITHUB_LFS_MEDIA_BASE_URL = "https://media.githubusercontent.com/media/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/";
    private static final String GIT_LFS_POINTER_HEADER = "version https://git-lfs.github.com/spec/v1";
    private static final Pattern GIT_LFS_OID = Pattern.compile("(?m)^oid\\s+sha256:([0-9a-fA-F]{64})\\s*$");
    private static final Pattern GIT_LFS_SIZE = Pattern.compile("(?m)^size\\s+(\\d+)\\s*$");
    private static final long MAX_LFS_POINTER_BYTES = 8_192L;
    private static final String GITHUB_CACHE_DIR_NAME = "modpacks-github-cache";
    private static final String GITHUB_STATE_FILE = "github-state.json";
    private static final String CATALOG_MANIFEST_FILE = "catalog-manifest.json";
    private static final long GITHUB_SYNC_MIN_INTERVAL_MS = 120_000L;
    private static final int DOWNLOAD_THREADS = 6;
    private static final int DOWNLOAD_ATTEMPTS = 3;
    private static final Pattern MODS_TOML_NAME = Pattern.compile("displayName\\s*=\\s*\"([^\"]+)\"");

    // El controlador y FabricMinecraftService crean cada uno su propio ModpackManager:
    // el estado de sincronizacion se comparte para no consultar/descargar dos veces.
    private static final Object GITHUB_SYNC_LOCK = new Object();
    private static volatile long lastGithubSyncAttemptMs;
    private static final AtomicReference<CatalogChanges> PENDING_CHANGES = new AtomicReference<>();
    private static final Map<String, JarMeta> JAR_META_CACHE = new ConcurrentHashMap<>();
    private static final Object INSTALL_STATE_LOCK = new Object();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private volatile SyncProgressListener syncProgressListener;

    enum EntryType {
        MOD,
        RESOURCEPACK
    }

    interface SyncProgressListener {
        void onProgress(String status, double fraction);
    }

    record OptionalEntry(
            String id,
            EntryType type,
            String section,
            String displayName,
            String description,
            String fileName,
            Path sourcePath,
            byte[] iconBytes,
            String stableKey
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
            stableKey = stableKey == null || stableKey.isBlank()
                    ? fileName.toLowerCase(Locale.ROOT)
                    : stableKey.toLowerCase(Locale.ROOT);
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

    record SyncResult(int copiedFiles, int removedMods, int removedResourcepacks, int unchangedFiles) {
    }

    /**
     * Resumen de lo que cambio en el repo de modpacks entre dos commits:
     * mods agregados, actualizados (mismo id de mod, otro archivo/version)
     * y eliminados, mas packs de textura y archivos de configuracion.
     */
    record CatalogChanges(
            String fromSha,
            String toSha,
            List<String> addedMods,
            List<String> updatedMods,
            List<String> removedMods,
            List<String> changedResourcepacks,
            int otherFilesChanged,
            long downloadedBytes
    ) {
        CatalogChanges {
            addedMods = addedMods == null ? List.of() : List.copyOf(addedMods);
            updatedMods = updatedMods == null ? List.of() : List.copyOf(updatedMods);
            removedMods = removedMods == null ? List.of() : List.copyOf(removedMods);
            changedResourcepacks = changedResourcepacks == null ? List.of() : List.copyOf(changedResourcepacks);
        }

        boolean isEmpty() {
            return addedMods.isEmpty() && updatedMods.isEmpty() && removedMods.isEmpty()
                    && changedResourcepacks.isEmpty() && otherFilesChanged == 0;
        }

        String summary() {
            List<String> parts = new ArrayList<>();
            if (!addedMods.isEmpty()) {
                parts.add(addedMods.size() + (addedMods.size() == 1 ? " mod nuevo" : " mods nuevos"));
            }
            if (!updatedMods.isEmpty()) {
                parts.add(updatedMods.size() + (updatedMods.size() == 1 ? " actualizado" : " actualizados"));
            }
            if (!removedMods.isEmpty()) {
                parts.add(removedMods.size() + (removedMods.size() == 1 ? " eliminado" : " eliminados"));
            }
            if (!changedResourcepacks.isEmpty()) {
                parts.add(changedResourcepacks.size() + (changedResourcepacks.size() == 1 ? " pack de textura" : " packs de textura"));
            }
            if (otherFilesChanged > 0) {
                parts.add(otherFilesChanged + (otherFilesChanged == 1 ? " ajuste de configuración" : " ajustes de configuración"));
            }
            return parts.isEmpty() ? "Sin cambios" : String.join(", ", parts);
        }

        String details() {
            StringBuilder out = new StringBuilder();
            appendSection(out, "Nuevos", addedMods);
            appendSection(out, "Actualizados", updatedMods);
            appendSection(out, "Eliminados", removedMods);
            appendSection(out, "Packs de textura", changedResourcepacks);
            return out.toString().trim();
        }

        private static void appendSection(StringBuilder out, String title, List<String> items) {
            if (items.isEmpty()) {
                return;
            }
            out.append(title).append(":\n");
            int shown = 0;
            for (String item : items) {
                if (shown == 6) {
                    out.append("  ... y ").append(items.size() - shown).append(" más\n");
                    break;
                }
                out.append("  - ").append(item).append('\n');
                shown++;
            }
        }
    }

    private record RemoteFile(String path, String blob, long size) {
    }

    private record ManifestEntry(String blob, long size, boolean lfs) {
    }

    private record JarMeta(String id, String name, String version, byte[] icon) {
    }

    void setSyncProgressListener(SyncProgressListener listener) {
        this.syncProgressListener = listener;
    }

    /** Devuelve (y limpia) los ultimos cambios detectados en el repo de modpacks. */
    static CatalogChanges consumeCatalogChanges() {
        return PENDING_CHANGES.getAndSet(null);
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
                                        .forEach(modFile -> {
                                            JarMeta meta = readJarMeta(modFile);
                                            optionalEntries.add(new OptionalEntry(
                                                    buildEntryId(root, EntryType.MOD, modFile),
                                                    EntryType.MOD,
                                                    sectionName,
                                                    meta.name(),
                                                    "",
                                                    modFile.getFileName().toString(),
                                                    modFile,
                                                    meta.icon(),
                                                    meta.id().isBlank() ? null : "mod:" + meta.id()
                                            ));
                                        });
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
                                readResourcepackIcon(packFile),
                                "pack:" + packFile.getFileName()
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

    // ------------------------------------------------------------------
    // Sincronizacion del repo de modpacks (GitHub -> cache local)
    // ------------------------------------------------------------------

    private void syncGithubCatalogIfNeeded(boolean force) throws IOException {
        synchronized (GITHUB_SYNC_LOCK) {
            Path contentPath = githubCatalogContentPath();
            boolean cacheValid = isValidCatalogRoot(contentPath);
            long now = System.currentTimeMillis();
            if (!force && cacheValid && (now - lastGithubSyncAttemptMs) < GITHUB_SYNC_MIN_INTERVAL_MS) {
                return;
            }
            lastGithubSyncAttemptMs = now;

            String remoteSha = fetchLatestGithubSha();
            if (remoteSha == null || remoteSha.isBlank()) {
                return;
            }
            String localSha = readCachedGithubSha();
            boolean manifestPresent = Files.isRegularFile(catalogManifestPath());
            if (!force && remoteSha.equalsIgnoreCase(localSha) && cacheValid && manifestPresent) {
                return;
            }

            CatalogChanges changes;
            try {
                changes = syncGithubIncrementally(localSha, remoteSha, contentPath);
            } catch (Exception incrementalFailed) {
                // Sin API de arboles (rate limit, red cortada a medias, etc.):
                // volvemos al metodo clasico de bajar el ZIP completo.
                changes = syncGithubFromArchive(localSha, remoteSha, contentPath);
            }
            writeGithubState(remoteSha);
            cleanupStaleExtracts();
            boolean realUpdate = localSha != null && !localSha.isBlank() && !remoteSha.equalsIgnoreCase(localSha);
            if (realUpdate && changes != null && !changes.isEmpty()) {
                PENDING_CHANGES.set(changes);
            }
        }
    }

    /**
     * Compara el arbol remoto (blob SHA por archivo) contra el manifiesto local y
     * descarga SOLO los archivos agregados o modificados; los eliminados se borran.
     * Si no hay manifiesto (primera vez con este launcher) se reconstruye
     * calculando el blob SHA de lo que ya esta en disco, sin volver a descargarlo.
     */
    private CatalogChanges syncGithubIncrementally(String localSha, String remoteSha, Path contentPath) throws IOException {
        reportSync("Buscando cambios en el modpack...", 0.0);
        List<RemoteFile> remoteFiles = fetchGithubTree(remoteSha);
        Map<String, ManifestEntry> manifest = readCatalogManifest();
        Path root = contentPath.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (manifest.isEmpty()) {
            manifest = manifestFromCachedCommit(localSha, remoteSha, remoteFiles, root);
        }

        Map<String, RemoteFile> remoteByPath = new LinkedHashMap<>();
        Set<String> remoteLowerPaths = new HashSet<>();
        for (RemoteFile file : remoteFiles) {
            Path resolved = root.resolve(file.path()).normalize();
            if (!resolved.startsWith(root)) {
                continue;
            }
            remoteByPath.put(file.path(), file);
            remoteLowerPaths.add(file.path().toLowerCase(Locale.ROOT));
        }

        Map<String, ManifestEntry> nextManifest = new LinkedHashMap<>();
        List<RemoteFile> toDownload = new ArrayList<>();
        int checked = 0;
        for (RemoteFile file : remoteByPath.values()) {
            checked++;
            if (checked % 20 == 0) {
                reportSync("Verificando archivos del modpack...", 0.05 * checked / Math.max(1, remoteByPath.size()));
            }
            Path local = root.resolve(file.path());
            ManifestEntry known = manifest.get(file.path());
            if (known != null && known.blob().equalsIgnoreCase(file.blob()) && Files.isRegularFile(local)
                    && sizeOf(local) == known.size()) {
                nextManifest.put(file.path(), known);
                continue;
            }
            ManifestEntry verified = known == null && Files.isRegularFile(local) ? verifyLocalAgainstRemote(remoteSha, local, file) : null;
            if (verified != null) {
                nextManifest.put(file.path(), verified);
                continue;
            }
            toDownload.add(file);
        }

        List<String> localPaths = listRelativeFiles(root);
        List<String> removedPaths = new ArrayList<>();
        for (String relative : localPaths) {
            if (!remoteLowerPaths.contains(relative.toLowerCase(Locale.ROOT))) {
                removedPaths.add(relative);
            }
        }

        Path staging = root.resolveSibling(root.getFileName() + ".staging");
        deleteDirectoryRecursive(staging);
        Map<String, Path> downloaded = new LinkedHashMap<>();
        Map<String, Boolean> downloadedLfs = new ConcurrentHashMap<>();
        long totalBytes = 0L;
        for (RemoteFile file : toDownload) {
            totalBytes += Math.max(1L, file.size());
            downloaded.put(file.path(), staging.resolve(file.path()).normalize());
        }

        if (!toDownload.isEmpty()) {
            downloadAll(remoteSha, toDownload, downloaded, downloadedLfs, totalBytes);
        }

        Set<String> localLower = new HashSet<>();
        for (String relative : localPaths) {
            localLower.add(relative.toLowerCase(Locale.ROOT));
        }
        List<String> addedPaths = new ArrayList<>();
        List<String> modifiedPaths = new ArrayList<>();
        for (RemoteFile file : toDownload) {
            if (localLower.contains(file.path().toLowerCase(Locale.ROOT))) {
                modifiedPaths.add(file.path());
            } else {
                addedPaths.add(file.path());
            }
        }
        long downloadedBytes = 0L;
        for (Path staged : downloaded.values()) {
            downloadedBytes += sizeOf(staged);
        }
        CatalogChanges changes = describeChanges(
                localSha, remoteSha, addedPaths, modifiedPaths, removedPaths,
                root::resolve, path -> downloaded.getOrDefault(path, root.resolve(path)), downloadedBytes
        );

        reportSync("Aplicando cambios del modpack...", 0.97);
        // Si algo falla a mitad, sin manifiesto la proxima sincronizacion re-verifica todo.
        Files.deleteIfExists(catalogManifestPath());
        for (String relative : removedPaths) {
            Path target = root.resolve(relative).normalize();
            if (target.startsWith(root)) {
                clearReadOnly(target);
                Files.deleteIfExists(target);
            }
        }
        for (RemoteFile file : toDownload) {
            Path source = downloaded.get(file.path());
            Path target = root.resolve(file.path()).normalize();
            Files.createDirectories(target.getParent());
            clearReadOnly(target);
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicMoveFailed) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            nextManifest.put(file.path(), new ManifestEntry(
                    file.blob(), sizeOf(target), Boolean.TRUE.equals(downloadedLfs.get(file.path()))
            ));
        }
        deleteEmptyDirectories(root);
        deleteDirectoryRecursive(staging);
        writeCatalogManifest(remoteSha, nextManifest);
        reportSync("Modpack actualizado.", 1.0);
        return changes;
    }

    private void downloadAll(
            String commitSha,
            List<RemoteFile> files,
            Map<String, Path> destinations,
            Map<String, Boolean> lfsFlags,
            long totalBytes
    ) throws IOException {
        AtomicLong doneBytes = new AtomicLong();
        AtomicInteger doneFiles = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(DOWNLOAD_THREADS, files.size()), runnable -> {
            Thread thread = new Thread(runnable, "modpack-download");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (RemoteFile file : files) {
                futures.add(pool.submit(() -> {
                    Path destination = destinations.get(file.path());
                    boolean lfs = downloadRemoteFileWithRetry(commitSha, file, destination);
                    lfsFlags.put(file.path(), lfs);
                    long bytes = doneBytes.addAndGet(Math.max(1L, file.size()));
                    int count = doneFiles.incrementAndGet();
                    reportSync(
                            "Descargando cambios del modpack (" + count + "/" + files.size() + ")...",
                            0.05 + 0.90 * (bytes / (double) Math.max(1L, totalBytes))
                    );
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Descarga del modpack interrumpida.", ex);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    throw cause instanceof IOException io ? io : new IOException(cause.getMessage(), cause);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean downloadRemoteFileWithRetry(String commitSha, RemoteFile file, Path destination) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                return downloadRemoteFile(commitSha, file, destination);
            } catch (IOException ex) {
                last = ex;
                try {
                    Thread.sleep(600L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Descarga interrumpida: " + file.path(), interrupted);
                }
            }
        }
        throw last == null ? new IOException("No se pudo descargar " + file.path()) : last;
    }

    /** Baja un archivo del commit exacto y valida su blob SHA; resuelve punteros Git LFS. */
    private boolean downloadRemoteFile(String commitSha, RemoteFile file, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        URI uri = URI.create(GITHUB_RAW_BASE_URL + commitSha + "/" + encodeGithubPath(file.path()));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", "SemencraftLauncher")
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream ignored = response.body()) {
                    // drenar
                }
                throw new IOException("GitHub devolvio HTTP " + response.statusCode() + " para " + file.path());
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(("blob " + file.size() + "\0").getBytes(StandardCharsets.US_ASCII));
            long written = 0L;
            try (InputStream input = response.body();
                 OutputStream output = Files.newOutputStream(destination,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    written += read;
                }
            }
            if (written != file.size() || !toHex(digest.digest()).equalsIgnoreCase(file.blob())) {
                throw new IOException("El archivo descargado no coincide con GitHub: " + file.path());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Descarga interrumpida: " + file.path(), ex);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("No se pudo descargar " + file.path() + ": " + ex.getMessage(), ex);
        }

        if (file.size() <= MAX_LFS_POINTER_BYTES) {
            LfsPointer pointer = readGitLfsPointer(destination);
            if (pointer != null) {
                downloadGithubLfsObject(commitSha, Path.of(file.path()), destination, pointer);
                return true;
            }
        }
        return false;
    }

    /**
     * Migracion desde el cache viejo (sin manifiesto): ese cache se armo desde el
     * commit guardado en github-state.json, asi que el arbol de ESE commit describe
     * lo que hay en disco. Se confirma solo por tamano (o que el LFS ya este
     * hidratado), sin hashear cientos de MB. Si algo no cuadra, ese archivo se
     * verifica o descarga en el paso normal.
     */
    private Map<String, ManifestEntry> manifestFromCachedCommit(
            String localSha,
            String remoteSha,
            List<RemoteFile> remoteFiles,
            Path root
    ) {
        Map<String, ManifestEntry> manifest = new LinkedHashMap<>();
        if (localSha == null || localSha.isBlank() || !isValidCatalogRoot(root)) {
            return manifest;
        }
        List<RemoteFile> baseline;
        try {
            baseline = localSha.equalsIgnoreCase(remoteSha) ? remoteFiles : fetchGithubTree(localSha);
        } catch (Exception ignored) {
            return manifest;
        }
        for (RemoteFile file : baseline) {
            Path local = root.resolve(file.path()).normalize();
            if (!local.startsWith(root) || !Files.isRegularFile(local)) {
                continue;
            }
            long localSize = sizeOf(local);
            if (localSize <= MAX_LFS_POINTER_BYTES && readGitLfsPointer(local) != null) {
                continue;
            }
            if (localSize == file.size()) {
                manifest.put(file.path(), new ManifestEntry(file.blob(), localSize, false));
            } else if (file.size() <= MAX_LFS_POINTER_BYTES) {
                // Solo es un LFS ya hidratado si el blob remoto es un puntero con ese tamano;
                // un archivo chico que simplemente cambio se verifica/descarga en el paso normal.
                LfsPointer pointer = fetchRemoteLfsPointer(localSha, file);
                if (pointer != null && pointer.size() == localSize) {
                    manifest.put(file.path(), new ManifestEntry(file.blob(), localSize, true));
                }
            }
        }
        return manifest;
    }

    /**
     * Para reconstruir el manifiesto sin descargar: compara el archivo local con
     * el blob remoto (o con el puntero LFS si el archivo esta en LFS).
     */
    private ManifestEntry verifyLocalAgainstRemote(String commitSha, Path local, RemoteFile remote) {
        try {
            long localSize = Files.size(local);
            if (localSize == remote.size()) {
                if (readGitLfsPointer(local) != null) {
                    return null;
                }
                return gitBlobSha1(local).equalsIgnoreCase(remote.blob())
                        ? new ManifestEntry(remote.blob(), localSize, false)
                        : null;
            }
            if (remote.size() > MAX_LFS_POINTER_BYTES) {
                return null;
            }
            LfsPointer pointer = fetchRemoteLfsPointer(commitSha, remote);
            if (pointer == null || pointer.size() != localSize) {
                return null;
            }
            return sha256Hex(local).equalsIgnoreCase(pointer.sha256())
                    ? new ManifestEntry(remote.blob(), localSize, true)
                    : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private LfsPointer fetchRemoteLfsPointer(String commitSha, RemoteFile remote) {
        URI uri = URI.create(GITHUB_RAW_BASE_URL + commitSha + "/" + encodeGithubPath(remote.path()));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", "SemencraftLauncher")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return parseLfsPointer(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<RemoteFile> fetchGithubTree(String commitSha) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(Locale.ROOT, GITHUB_API_TREE_URL, commitSha)))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SemencraftLauncher")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GitHub API devolvio HTTP " + response.statusCode() + " al leer el arbol del repo.");
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.has("truncated") && json.get("truncated").getAsBoolean()) {
                throw new IOException("El arbol del repo de modpacks es demasiado grande para la API.");
            }
            List<RemoteFile> files = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("tree")) {
                JsonObject entry = element.getAsJsonObject();
                if (!"blob".equals(optionalString(entry, "type", ""))) {
                    continue;
                }
                String path = optionalString(entry, "path", "");
                String sha = optionalString(entry, "sha", "");
                if (path.isBlank() || sha.isBlank()) {
                    continue;
                }
                long size = entry.has("size") ? entry.get("size").getAsLong() : 0L;
                files.add(new RemoteFile(path, sha, size));
            }
            if (files.isEmpty()) {
                throw new IOException("El arbol del repo de modpacks vino vacio.");
            }
            return files;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Consulta de GitHub interrumpida.", ex);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("No se pudo leer el arbol del repo de modpacks: " + ex.getMessage(), ex);
        }
    }

    /** Metodo de respaldo: ZIP completo del repo. Tambien deja el manifiesto listo. */
    private CatalogChanges syncGithubFromArchive(String localSha, String remoteSha, Path contentPath) throws IOException {
        reportSync("Descargando modpack completo...", 0.1);
        Map<String, ManifestEntry> previous = readCatalogManifest();
        Path extracted = downloadAndExtractGithubCatalog();
        if (!isValidCatalogRoot(extracted)) {
            deleteDirectoryRecursive(extracted);
            throw new IOException("El repo descargado no contiene la estructura esperada de SemencraftModpacks.");
        }
        Map<String, ManifestEntry> next = buildLocalManifest(extracted, remoteSha);

        Path root = contentPath.toAbsolutePath().normalize();
        Map<String, String> oldBlobs = new LinkedHashMap<>();
        if (!previous.isEmpty()) {
            previous.forEach((path, entry) -> oldBlobs.put(path, entry.blob()));
        } else if (isValidCatalogRoot(root)) {
            buildLocalManifest(root, null).forEach((path, entry) -> oldBlobs.put(path, entry.blob()));
        }
        List<String> added = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, ManifestEntry> entry : next.entrySet()) {
            String old = oldBlobs.get(entry.getKey());
            if (old == null) {
                added.add(entry.getKey());
            } else if (!old.equalsIgnoreCase(entry.getValue().blob())) {
                modified.add(entry.getKey());
            }
        }
        for (String path : oldBlobs.keySet()) {
            if (!next.containsKey(path)) {
                removed.add(path);
            }
        }
        CatalogChanges changes = describeChanges(
                localSha, remoteSha, added, modified, removed, root::resolve, extracted::resolve, 0L
        );
        Files.deleteIfExists(catalogManifestPath());
        replaceDirectory(extracted, contentPath);
        writeCatalogManifest(remoteSha, next);
        reportSync("Modpack actualizado.", 1.0);
        return changes;
    }

    /** Calcula blob SHA de cada archivo; los punteros LFS se hidratan en el camino. */
    private Map<String, ManifestEntry> buildLocalManifest(Path catalogRoot, String commitSha) throws IOException {
        Map<String, ManifestEntry> manifest = new LinkedHashMap<>();
        for (String relative : listRelativeFiles(catalogRoot)) {
            Path file = catalogRoot.resolve(relative);
            LfsPointer pointer = readGitLfsPointer(file);
            if (pointer != null) {
                String pointerBlob = gitBlobSha1(file);
                if (commitSha != null) {
                    downloadGithubLfsObject(commitSha, Path.of(relative), file, pointer);
                }
                manifest.put(relative, new ManifestEntry(pointerBlob, sizeOf(file), true));
            } else {
                manifest.put(relative, new ManifestEntry(gitBlobSha1(file), sizeOf(file), false));
            }
        }
        return manifest;
    }

    private interface PathResolver {
        Path resolve(String relativePath);
    }

    private static CatalogChanges describeChanges(
            String fromSha,
            String toSha,
            List<String> addedPaths,
            List<String> modifiedPaths,
            List<String> removedPaths,
            PathResolver oldFiles,
            PathResolver newFiles,
            long downloadedBytes
    ) {
        List<String> addedMods = new ArrayList<>();
        List<String> updatedMods = new ArrayList<>();
        List<String> removedMods = new ArrayList<>();
        List<String> packs = new ArrayList<>();
        int other = 0;

        Map<String, JarMeta> removedById = new LinkedHashMap<>();
        Map<String, String> removedNoId = new LinkedHashMap<>();
        for (String path : removedPaths) {
            if (isModPath(path)) {
                JarMeta meta = readJarMetaUncached(oldFiles.resolve(path), false);
                if (!meta.id().isBlank()) {
                    removedById.put(meta.id(), meta);
                } else {
                    removedNoId.put(path, meta.name());
                }
            } else if (isResourcepackPath(path)) {
                packs.add(stripExtension(fileNameOf(path)) + " (eliminado)");
            } else {
                other++;
            }
        }
        for (String path : addedPaths) {
            if (isModPath(path)) {
                JarMeta meta = readJarMetaUncached(newFiles.resolve(path), false);
                JarMeta previous = meta.id().isBlank() ? null : removedById.remove(meta.id());
                if (previous != null) {
                    updatedMods.add(describeUpdate(previous, meta));
                } else {
                    addedMods.add(describeMod(meta));
                }
            } else if (isResourcepackPath(path)) {
                packs.add(stripExtension(fileNameOf(path)) + " (nuevo)");
            } else {
                other++;
            }
        }
        for (String path : modifiedPaths) {
            if (isModPath(path)) {
                JarMeta before = readJarMetaUncached(oldFiles.resolve(path), false);
                JarMeta after = readJarMetaUncached(newFiles.resolve(path), false);
                updatedMods.add(describeUpdate(before, after));
            } else if (isResourcepackPath(path)) {
                packs.add(stripExtension(fileNameOf(path)) + " (actualizado)");
            } else {
                other++;
            }
        }
        for (JarMeta meta : removedById.values()) {
            removedMods.add(describeMod(meta));
        }
        removedMods.addAll(removedNoId.values());
        return new CatalogChanges(fromSha, toSha, addedMods, updatedMods, removedMods, packs, other, downloadedBytes);
    }

    private static String describeMod(JarMeta meta) {
        return meta.version().isBlank() ? meta.name() : meta.name() + " " + meta.version();
    }

    private static String describeUpdate(JarMeta before, JarMeta after) {
        String from = before.version();
        String to = after.version();
        if (!from.isBlank() && !to.isBlank() && !from.equals(to)) {
            return after.name() + " " + from + " -> " + to;
        }
        return describeMod(after);
    }

    private static boolean isModPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") && (lower.startsWith("normal/modtypes/") || lower.startsWith("required/mods/"));
    }

    private static boolean isResourcepackPath(String path) {
        return path.toLowerCase(Locale.ROOT).startsWith("normal/profile/resourcepacks/");
    }

    private static String fileNameOf(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private void reportSync(String status, double fraction) {
        SyncProgressListener listener = syncProgressListener;
        if (listener != null) {
            try {
                listener.onProgress(status, Math.max(0.0, Math.min(1.0, fraction)));
            } catch (Exception ignored) {
            }
        }
    }

    private void cleanupStaleExtracts() {
        Path cacheRoot = githubCacheRootPath();
        if (!Files.isDirectory(cacheRoot)) {
            return;
        }
        try (Stream<Path> entries = Files.list(cacheRoot)) {
            for (Path path : entries.toList()) {
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                if (Files.isDirectory(path) && (name.startsWith("extract-") || name.endsWith(".staging")
                        || name.endsWith(".tmp") || name.endsWith(".bak"))) {
                    deleteDirectoryRecursive(path);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Map<String, ManifestEntry> readCatalogManifest() {
        Path path = catalogManifestPath();
        if (!Files.isRegularFile(path)) {
            return new LinkedHashMap<>();
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject files = root.getAsJsonObject("files");
            Map<String, ManifestEntry> manifest = new LinkedHashMap<>();
            if (files != null) {
                for (Map.Entry<String, JsonElement> entry : files.entrySet()) {
                    JsonObject value = entry.getValue().getAsJsonObject();
                    manifest.put(entry.getKey(), new ManifestEntry(
                            optionalString(value, "blob", ""),
                            value.has("size") ? value.get("size").getAsLong() : -1L,
                            value.has("lfs") && value.get("lfs").getAsBoolean()
                    ));
                }
            }
            return manifest;
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private void writeCatalogManifest(String sha, Map<String, ManifestEntry> manifest) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("sha", sha == null ? "" : sha);
        JsonObject files = new JsonObject();
        manifest.forEach((path, entry) -> {
            JsonObject value = new JsonObject();
            value.addProperty("blob", entry.blob());
            value.addProperty("size", entry.size());
            if (entry.lfs()) {
                value.addProperty("lfs", true);
            }
            files.add(path, value);
        });
        root.add("files", files);
        writeJsonAtomically(catalogManifestPath(), root);
    }

    /** blob SHA por ruta relativa (con '/') del catalogo, si el catalogo es el cache de GitHub. */
    private Map<String, String> catalogBlobIndex(Catalog catalog) {
        Map<String, String> index = new LinkedHashMap<>();
        if (catalog == null) {
            return index;
        }
        Path cache = githubCatalogContentPath().toAbsolutePath().normalize();
        if (!catalog.rootPath().toAbsolutePath().normalize().equals(cache)) {
            return index;
        }
        readCatalogManifest().forEach((path, entry) -> index.put(path.toLowerCase(Locale.ROOT), entry.blob()));
        return index;
    }

    private String fetchLatestGithubSha() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GITHUB_API_COMMIT_URL))
                .header("Accept", "application/vnd.github.sha")
                .header("User-Agent", "SemencraftLauncher")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GitHub API devolvio HTTP " + response.statusCode() + " al consultar commits.");
            }
            String body = response.body() == null ? "" : response.body().trim();
            if (body.startsWith("{")) {
                return optionalString(JsonParser.parseString(body).getAsJsonObject(), "sha", "");
            }
            return body.matches("[0-9a-fA-F]{40}") ? body : "";
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

    private static LfsPointer readGitLfsPointer(Path file) {
        try {
            long fileSize = Files.size(file);
            if (fileSize <= 0L || fileSize > MAX_LFS_POINTER_BYTES) {
                return null;
            }
            return parseLfsPointer(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LfsPointer parseLfsPointer(String pointerText) {
        if (pointerText == null || !pointerText.startsWith(GIT_LFS_POINTER_HEADER)) {
            return null;
        }
        Matcher oidMatcher = GIT_LFS_OID.matcher(pointerText);
        Matcher sizeMatcher = GIT_LFS_SIZE.matcher(pointerText);
        if (!oidMatcher.find() || !sizeMatcher.find()) {
            return null;
        }
        try {
            long expectedSize = Long.parseLong(sizeMatcher.group(1));
            return expectedSize > 0L
                    ? new LfsPointer(oidMatcher.group(1).toLowerCase(Locale.ROOT), expectedSize)
                    : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void downloadGithubLfsObject(String commitSha, Path relativePath, Path destination, LfsPointer pointer) throws IOException {
        String ref = commitSha == null || commitSha.isBlank() ? GITHUB_BRANCH : commitSha;
        URI uri = URI.create(GITHUB_LFS_MEDIA_BASE_URL + ref + "/" + encodeGithubPath(relativePath));
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

    private static String encodeGithubPath(String path) {
        StringBuilder encoded = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (!encoded.isEmpty()) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
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
        if (readGitLfsPointer(jarPath) != null) {
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
        Path backup = targetDir.resolveSibling(targetDir.getFileName().toString() + ".bak");
        deleteDirectoryRecursive(backup);
        try {
            if (Files.exists(targetDir)) {
                moveDirectoryWithFallback(targetDir, backup);
                if (Files.exists(targetDir)) {
                    deleteDirectoryRecursive(targetDir);
                }
            }
            moveDirectoryWithFallback(sourceDir, targetDir);
            if (Files.exists(sourceDir)) {
                deleteDirectoryRecursive(sourceDir);
            }
            deleteDirectoryRecursive(backup);
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

    // ------------------------------------------------------------------
    // Seleccion de mods (plantillas y perfil custom)
    // ------------------------------------------------------------------

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
            if (isDefaultEnabled(entry, normalized)) {
                enabled.add(entry.id());
            }
        }
        return enabled;
    }

    private static boolean isDefaultEnabled(OptionalEntry entry, String normalizedBaseTemplate) {
        if (entry.type() == EntryType.RESOURCEPACK) {
            return false;
        }
        return !(TEMPLATE_OPTIMIZED.equals(normalizedBaseTemplate) && isOptimizedDisabledSection(entry.section()));
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

    /**
     * Carga el perfil custom. Si un mod cambio de archivo (actualizacion) se conserva
     * su estado por id de mod; los mods realmente nuevos usan el valor por defecto
     * de la plantilla base en vez de quedar desactivados.
     */
    Selection loadSavedCustomSelection(Catalog catalog) {
        Path profilePath = profilePath();
        if (!Files.exists(profilePath)) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(profilePath, StandardCharsets.UTF_8)).getAsJsonObject();
            String baseTemplate = normalizeBaseTemplate(optionalString(root, "baseTemplate", TEMPLATE_NORMAL));
            Set<String> enabledIds = readStringSet(root, "enabled");
            Set<String> enabledKeys = readStringSet(root, "enabledKeys");
            Set<String> knownIds = readStringSet(root, "known");
            Set<String> knownKeys = readStringSet(root, "knownKeys");
            boolean trackedProfile = root.has("known");

            LinkedHashSet<String> enabled = new LinkedHashSet<>();
            if (catalog == null) {
                enabled.addAll(enabledIds);
            } else {
                for (OptionalEntry entry : catalog.optionalEntries()) {
                    if (entry.type() != EntryType.MOD) {
                        continue;
                    }
                    if (enabledIds.contains(entry.id())) {
                        enabled.add(entry.id());
                    } else if (trackedProfile && !knownIds.contains(entry.id())) {
                        if (enabledKeys.contains(entry.stableKey())) {
                            enabled.add(entry.id());
                        } else if (!knownKeys.contains(entry.stableKey()) && isDefaultEnabled(entry, baseTemplate)) {
                            enabled.add(entry.id());
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

    void saveCustomSelection(Catalog catalog, Selection selection) {
        if (selection == null) {
            return;
        }
        try {
            Files.createDirectories(LauncherStorage.launcherDirectory());
            JsonObject json = new JsonObject();
            json.addProperty("baseTemplate", normalizeBaseTemplate(selection.baseTemplate()));
            JsonArray enabled = new JsonArray();
            JsonArray enabledKeys = new JsonArray();
            for (String id : selection.enabledOptionalIds()) {
                enabled.add(id);
                OptionalEntry entry = catalog == null ? null : catalog.findOptionalById(id);
                if (entry != null) {
                    enabledKeys.add(entry.stableKey());
                }
            }
            json.add("enabled", enabled);
            json.add("enabledKeys", enabledKeys);
            if (catalog != null) {
                JsonArray known = new JsonArray();
                JsonArray knownKeys = new JsonArray();
                for (OptionalEntry entry : catalog.optionalEntries()) {
                    if (entry.type() == EntryType.MOD) {
                        known.add(entry.id());
                        knownKeys.add(entry.stableKey());
                    }
                }
                json.add("known", known);
                json.add("knownKeys", knownKeys);
            }
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

    private static Set<String> readStringSet(JsonObject root, String key) {
        Set<String> values = new LinkedHashSet<>();
        JsonArray array = root.has(key) && root.get(key).isJsonArray() ? root.getAsJsonArray(key) : null;
        if (array == null) {
            return values;
        }
        for (JsonElement element : array) {
            if (element != null && element.isJsonPrimitive()) {
                values.add(element.getAsString());
            }
        }
        return values;
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

    // ------------------------------------------------------------------
    // Sincronizacion cache -> carpeta del juego
    // ------------------------------------------------------------------

    void applyResourcepackSelectionToGame(Catalog catalog, Path gameDir, List<String> orderedSelectedIds) throws IOException {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(gameDir, "gameDir");

        Files.createDirectories(gameDir);
        Path resourcepacksDir = gameDir.resolve("resourcepacks");
        Files.createDirectories(resourcepacksDir);

        synchronized (INSTALL_STATE_LOCK) {
            InstallState state = InstallState.load(installStatePath(), gameDir);
            Map<String, String> blobIndex = catalogBlobIndex(catalog);
            Set<String> expectedResourcepacks = new LinkedHashSet<>();
            int[] unchanged = {0};
            syncAllManagedResourcepacks(catalog, gameDir, expectedResourcepacks, state, blobIndex, unchanged);
            removeUnexpectedFiles(resourcepacksDir, expectedResourcepacks);
            applyLockHints(resourcepacksDir, expectedResourcepacks);
            state.save();
        }
        writeSelectedResourcepacks(catalog, gameDir, orderedSelectedIds);
    }

    /**
     * Deja la carpeta del juego igual al modpack seleccionado tocando solo lo que
     * cambio. Un registro de instalacion (archivo de origen + tamano + fecha) evita
     * volver a hashear o copiar los mods que ya estan bien en cada inicio.
     */
    SyncResult synchronizeForLaunch(Catalog catalog, Selection selection, Path gameDir, String playerName) throws IOException {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(gameDir, "gameDir");

        if (!Files.isDirectory(catalog.rootPath())) {
            throw new IOException("No se encontro la carpeta SemencraftModpacks en: " + catalog.rootPath());
        }

        Path absGameDir = gameDir.toAbsolutePath().normalize();
        Files.createDirectories(absGameDir);
        Path modsDir = absGameDir.resolve("mods");
        Path resourcepacksDir = absGameDir.resolve("resourcepacks");
        Files.createDirectories(modsDir);
        Files.createDirectories(resourcepacksDir);

        int copiedFiles = 0;
        int[] unchanged = {0};
        int removedMods;
        int removedResourcepacks;
        Set<String> expectedMods = new LinkedHashSet<>();
        Set<String> expectedResourcepacks = new LinkedHashSet<>();
        Set<String> expectedAll = new LinkedHashSet<>();

        synchronized (INSTALL_STATE_LOCK) {
            InstallState state = InstallState.load(installStatePath(), absGameDir);
            Map<String, String> blobIndex = catalogBlobIndex(catalog);

            for (RequiredEntry required : catalog.requiredEntries()) {
                Path relative = required.targetRelativePath().normalize();
                Path target = absGameDir.resolve(relative).normalize();
                if (!target.startsWith(absGameDir)) {
                    continue;
                }
                String key = normalizeRelativeKey(relative);
                boolean enforce = isEnforcedTarget(relative);
                String sourceId = sourceIdFor(catalog, required.sourcePath(), blobIndex);
                if (syncManagedFile(required.sourcePath(), target, key, sourceId, enforce, state)) {
                    copiedFiles++;
                } else {
                    unchanged[0]++;
                }
                expectedAll.add(key);
                rememberManagedTarget(relative, expectedMods, expectedResourcepacks);
            }

            for (String enabledId : selection.enabledOptionalIds()) {
                OptionalEntry entry = catalog.findOptionalById(enabledId);
                if (entry == null || entry.type() != EntryType.MOD) {
                    continue;
                }
                Path relativeTarget = Path.of("mods").resolve(entry.fileName());
                Path target = absGameDir.resolve(relativeTarget).normalize();
                if (!target.startsWith(absGameDir)) {
                    continue;
                }
                String key = normalizeRelativeKey(relativeTarget);
                String sourceId = sourceIdFor(catalog, entry.sourcePath(), blobIndex);
                if (syncManagedFile(entry.sourcePath(), target, key, sourceId, true, state)) {
                    copiedFiles++;
                } else {
                    unchanged[0]++;
                }
                expectedAll.add(key);
                rememberManagedTarget(relativeTarget, expectedMods, expectedResourcepacks);
            }

            copiedFiles += syncAllManagedResourcepacks(catalog, absGameDir, expectedResourcepacks, state, blobIndex, unchanged);
            for (String pack : expectedResourcepacks) {
                expectedAll.add("resourcepacks/" + pack);
            }

            removedMods = removeUnexpectedFiles(modsDir, expectedMods);
            removedResourcepacks = removeUnexpectedFiles(resourcepacksDir, expectedResourcepacks);
            removeStaleManagedFiles(state, expectedAll, absGameDir);
            state.retainOnly(expectedAll);
            state.save();
        }

        copiedFiles += applyOptionsPatchOncePerPlayer(catalog, selection, absGameDir, playerName);
        copiedFiles += writeSelectedResourcepacks(catalog, absGameDir, loadAppliedResourcepackSelection(catalog, absGameDir));
        applyLockHints(modsDir, expectedMods);
        applyLockHints(resourcepacksDir, expectedResourcepacks);

        return new SyncResult(copiedFiles, removedMods, removedResourcepacks, unchanged[0]);
    }

    private static boolean isEnforcedTarget(Path relative) {
        if (relative == null || relative.getNameCount() < 2) {
            return false;
        }
        String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        return "mods".equals(first) || "resourcepacks".equals(first);
    }

    private static String sourceIdFor(Catalog catalog, Path source, Map<String, String> blobIndex) {
        if (!blobIndex.isEmpty()) {
            try {
                String relative = catalog.rootPath().relativize(source).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                String blob = blobIndex.get(relative);
                if (blob != null && !blob.isBlank()) {
                    return "git:" + blob;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            return "fs:" + Files.size(source) + ":" + Files.getLastModifiedTime(source).toMillis();
        } catch (Exception ignored) {
            return "fs:unknown";
        }
    }

    /**
     * Copia un archivo del modpack solo si hace falta.
     * enforce=true (mods/resourcepacks): se repara si alguien lo toco.
     * enforce=false (configs): si el repo no lo cambio se respetan los cambios del jugador.
     */
    private static boolean syncManagedFile(
            Path source,
            Path target,
            String key,
            String sourceId,
            boolean enforce,
            InstallState state
    ) throws IOException {
        if (!Files.isRegularFile(source)) {
            return false;
        }
        InstallState.Entry record = state.get(key);
        boolean exists = Files.isRegularFile(target);
        if (exists && record != null && record.source().equals(sourceId)) {
            if (!enforce || record.matches(target)) {
                return false;
            }
        }
        // Las copias del launcher conservan la fecha del archivo de origen: mismo tamano y
        // misma fecha = mismo archivo. Solo si no coinciden se compara el contenido.
        if (exists && Files.size(target) == Files.size(source)
                && (sameModifiedTime(source, target) || sha256Hex(source).equalsIgnoreCase(sha256Hex(target)))) {
            state.put(key, sourceId, target);
            return false;
        }
        if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            validateJarFile(source);
        }
        Files.createDirectories(target.getParent());
        clearReadOnly(target);
        Path temp = target.resolveSibling(target.getFileName() + ".sc-" + UUID.randomUUID() + ".tmp");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.setLastModifiedTime(temp, Files.getLastModifiedTime(source));
            } catch (Exception ignored) {
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicMoveFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        state.put(key, sourceId, target);
        return true;
    }

    private static boolean sameModifiedTime(Path a, Path b) {
        try {
            return Files.getLastModifiedTime(a).toMillis() == Files.getLastModifiedTime(b).toMillis();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void removeStaleManagedFiles(InstallState state, Set<String> expectedKeys, Path gameDir) {
        for (String key : state.keys()) {
            if (expectedKeys.contains(key)) {
                continue;
            }
            Path target = gameDir.resolve(key).normalize();
            if (!target.startsWith(gameDir)) {
                continue;
            }
            try {
                clearReadOnly(target);
                Files.deleteIfExists(target);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Registro de lo que el launcher instalo en la carpeta del juego.
     */
    private static final class InstallState {
        record Entry(String source, long size, long mtime) {
            boolean matches(Path target) {
                try {
                    return Files.size(target) == size && Files.getLastModifiedTime(target).toMillis() == mtime;
                } catch (Exception ignored) {
                    return false;
                }
            }
        }

        private final Path file;
        private final String gameDir;
        private final Map<String, Entry> entries = new LinkedHashMap<>();

        private InstallState(Path file, String gameDir) {
            this.file = file;
            this.gameDir = gameDir;
        }

        static InstallState load(Path file, Path gameDir) {
            String normalizedGameDir = gameDir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            InstallState state = new InstallState(file, normalizedGameDir);
            if (!Files.isRegularFile(file)) {
                return state;
            }
            try {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!normalizedGameDir.equals(optionalString(root, "gameDir", ""))) {
                    return state;
                }
                JsonObject files = root.getAsJsonObject("files");
                if (files != null) {
                    for (Map.Entry<String, JsonElement> entry : files.entrySet()) {
                        JsonObject value = entry.getValue().getAsJsonObject();
                        state.entries.put(entry.getKey(), new Entry(
                                optionalString(value, "source", ""),
                                value.has("size") ? value.get("size").getAsLong() : -1L,
                                value.has("mtime") ? value.get("mtime").getAsLong() : -1L
                        ));
                    }
                }
            } catch (Exception ignored) {
                state.entries.clear();
            }
            return state;
        }

        Entry get(String key) {
            return entries.get(key);
        }

        void put(String key, String source, Path target) {
            try {
                entries.put(key, new Entry(source, Files.size(target), Files.getLastModifiedTime(target).toMillis()));
            } catch (Exception ignored) {
                entries.remove(key);
            }
        }

        List<String> keys() {
            return new ArrayList<>(entries.keySet());
        }

        void retainOnly(Set<String> keys) {
            entries.keySet().retainAll(keys);
        }

        void save() {
            try {
                JsonObject root = new JsonObject();
                root.addProperty("gameDir", gameDir);
                JsonObject files = new JsonObject();
                entries.forEach((key, entry) -> {
                    JsonObject value = new JsonObject();
                    value.addProperty("source", entry.source());
                    value.addProperty("size", entry.size());
                    value.addProperty("mtime", entry.mtime());
                    files.add(key, value);
                });
                root.add("files", files);
                writeJsonAtomically(file, root);
            } catch (Exception ignored) {
            }
        }
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

    // ------------------------------------------------------------------
    // Metadata de .jar / resourcepacks
    // ------------------------------------------------------------------

    /** Nombre, id, version e icono con una sola apertura del .jar (cacheado por tamano+fecha). */
    private static JarMeta readJarMeta(Path jarFile) {
        String cacheKey;
        try {
            cacheKey = jarFile.toAbsolutePath().normalize() + "|" + Files.size(jarFile) + "|"
                    + Files.getLastModifiedTime(jarFile).toMillis();
        } catch (Exception ignored) {
            return readJarMetaUncached(jarFile, true);
        }
        return JAR_META_CACHE.computeIfAbsent(cacheKey, ignored -> readJarMetaUncached(jarFile, true));
    }

    private static JarMeta readJarMetaUncached(Path jarFile, boolean withIcon) {
        String fallback = jarFile == null || jarFile.getFileName() == null ? "" : stripExtension(jarFile.getFileName().toString());
        String id = "";
        String name = fallback;
        String version = "";
        byte[] icon = null;
        if (jarFile == null || !Files.isRegularFile(jarFile)) {
            return new JarMeta(id, name, version, null);
        }
        try (ZipFile zip = new ZipFile(jarFile.toFile())) {
            String iconPath = null;
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric != null) {
                try (InputStream in = zip.getInputStream(fabric)) {
                    JsonObject obj = JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    id = optionalString(obj, "id", "").trim();
                    String declaredName = optionalString(obj, "name", "");
                    if (declaredName != null && !declaredName.isBlank()) {
                        name = declaredName.trim();
                    } else if (!id.isBlank()) {
                        name = id;
                    }
                    version = optionalString(obj, "version", "").trim();
                    if (version.startsWith("${")) {
                        version = "";
                    }
                    iconPath = preferredFabricIconPath(obj);
                }
            } else {
                ZipEntry modsToml = zip.getEntry("META-INF/mods.toml");
                if (modsToml != null) {
                    try (InputStream in = zip.getInputStream(modsToml)) {
                        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        Matcher matcher = MODS_TOML_NAME.matcher(text);
                        if (matcher.find()) {
                            name = matcher.group(1).trim();
                        }
                    }
                }
            }
            if (withIcon) {
                if (iconPath != null && !iconPath.isBlank()) {
                    icon = readZipEntryBytes(zip, iconPath, 2_000_000);
                }
                if (icon == null || icon.length == 0) {
                    icon = findAnyPngIcon(zip);
                }
            }
        } catch (Exception ignored) {
        }
        return new JarMeta(id == null ? "" : id, name, version == null ? "" : version, icon);
    }

    private static byte[] findAnyPngIcon(ZipFile zip) {
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
            if (!lower.endsWith(".png") || lower.startsWith("meta-inf/")) {
                continue;
            }
            byte[] bytes = readZipEntryBytes(zip, name, 2_000_000);
            if (bytes != null && bytes.length > 0) {
                return bytes;
            }
        }
        return null;
    }

    private static String preferredFabricIconPath(JsonObject fabricJson) {
        JsonElement icon = fabricJson.get("icon");
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

    // ------------------------------------------------------------------
    // options.txt
    // ------------------------------------------------------------------

    /**
     * El parche de opciones se aplica UNA sola vez por jugador. Despues de eso
     * el jugador es libre de cambiar sus opciones sin que el launcher las pise.
     */
    private int applyOptionsPatchOncePerPlayer(Catalog catalog, Selection selection, Path gameDir, String playerName) throws IOException {
        if (!shouldApplyOptionsPatch(selection)) {
            return 0;
        }
        Path patchFile = catalog.rootPath().resolve(OPTIONS_PATCH_FILE);
        if (!Files.exists(patchFile)) {
            return 0;
        }
        String playerKey = playerName == null || playerName.isBlank()
                ? "_default"
                : playerName.trim().toLowerCase(Locale.ROOT);

        Path statePath = optionsPatchStatePath();
        JsonObject state = new JsonObject();
        if (Files.isRegularFile(statePath)) {
            try {
                state = JsonParser.parseString(Files.readString(statePath, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ignored) {
                state = new JsonObject();
            }
        }
        JsonObject players = state.has("players") && state.get("players").isJsonObject()
                ? state.getAsJsonObject("players")
                : new JsonObject();
        if (players.has(playerKey)) {
            return 0;
        }

        List<String> patchLines = Files.readAllLines(patchFile, StandardCharsets.UTF_8);
        int written = 0;
        if (!patchLines.isEmpty()) {
            Path targetOptions = gameDir.resolve("options.txt");
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
                written = 1;
            }
        }

        JsonObject record = new JsonObject();
        record.addProperty("appliedAt", System.currentTimeMillis());
        record.addProperty("patchSha256", sha256Hex(patchFile));
        players.add(playerKey, record);
        state.add("players", players);
        writeJsonAtomically(statePath, state);
        return written;
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
            Path gameDir,
            Set<String> expectedResourcepacks,
            InstallState state,
            Map<String, String> blobIndex,
            int[] unchanged
    ) throws IOException {
        if (catalog == null || gameDir == null) {
            return 0;
        }
        Path absGameDir = gameDir.toAbsolutePath().normalize();
        Path resourcepacksDir = absGameDir.resolve("resourcepacks");
        int copiedFiles = 0;
        for (OptionalEntry entry : catalog.optionalEntries()) {
            if (entry.type() != EntryType.RESOURCEPACK) {
                continue;
            }
            Path relativeTarget = Path.of("resourcepacks").resolve(entry.fileName());
            Path target = resourcepacksDir.resolve(entry.fileName()).normalize();
            if (!target.startsWith(resourcepacksDir)) {
                continue;
            }
            String key = normalizeRelativeKey(relativeTarget);
            if (syncManagedFile(entry.sourcePath(), target, key, sourceIdFor(catalog, entry.sourcePath(), blobIndex), true, state)) {
                copiedFiles++;
            } else {
                unchanged[0]++;
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

    // ------------------------------------------------------------------
    // Utilidades de archivos
    // ------------------------------------------------------------------

    private static List<String> listRelativeFiles(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static void writeJsonAtomically(Path target, JsonObject json) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, json.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicMoveFailed) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
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

    private static void applyLockHints(Path managedRoot, Set<String> expectedRelativeKeys) {
        if (managedRoot == null || expectedRelativeKeys == null || expectedRelativeKeys.isEmpty()) {
            return;
        }
        try (Stream<Path> walk = Files.walk(managedRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(Files::isWritable)
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
        return digestHex(path, "SHA-256", null);
    }

    /** SHA-1 de git para un blob: sha1("blob <size>\0" + contenido). */
    private static String gitBlobSha1(Path path) throws IOException {
        return digestHex(path, "SHA-1", ("blob " + Files.size(path) + "\0").getBytes(StandardCharsets.US_ASCII));
    }

    private static String digestHex(Path path, String algorithm, byte[] prefix) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (Exception ex) {
            throw new IOException("No se pudo inicializar " + algorithm + ".", ex);
        }
        if (prefix != null) {
            digest.update(prefix);
        }
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[128 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) {
                    continue;
                }
                digest.update(buffer, 0, n);
            }
        }
        return toHex(digest.digest());
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

    private Path installStatePath() {
        return LauncherStorage.launcherDirectory().resolve(INSTALL_STATE_FILE_NAME);
    }

    private Path optionsPatchStatePath() {
        return LauncherStorage.launcherDirectory().resolve(OPTIONS_PATCH_STATE_FILE_NAME);
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

    private Path catalogManifestPath() {
        return githubCacheRootPath().resolve(CATALOG_MANIFEST_FILE);
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
