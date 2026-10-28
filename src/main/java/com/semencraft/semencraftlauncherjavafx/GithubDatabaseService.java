package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class GithubDatabaseService {

    private static final String GITHUB_OWNER = "XK-GamerX";
    private static final String GITHUB_REPO = "SemencraftDatabase";
    private static final String GITHUB_BRANCH = "main";
    private static final String GITHUB_API_BASE = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/contents/";
    private static final String USERS_DIR = "users";
    private static final String ROLE_REQUESTS_DIR = "role_requests";
    private static final String DISCORD_USERS_PATH = "discord/discord_users.json";
    private static final String PRIVATE_RESOURCE_PATH = "private/database-private.json";
    private static final String LOCAL_PRIVATE_FILE = "database-private.json";
    private static final String TOKEN_ENV_VAR = "SEMENCRAFT_GITHUB_TOKEN";
    private static final String USER_AGENT = "SemencraftLauncher/1.0";
    private static final int PASSWORD_ITERATIONS = 60_000;
    private static final int PASSWORD_KEY_BITS = 256;
    private static final SecureRandom RNG = new SecureRandom();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String githubToken = resolveGithubToken();

    record RemoteAccount(String minecraftUsername, String discordUsername, String discordUserId) {
    }

    record AuthResult(boolean success, String message, RemoteAccount account) {
    }

    record RegistrationRequest(String minecraftUsername, String password, String discordUsername) {
    }

    record RegistrationResult(boolean success, String message, RemoteAccount account) {
    }

    static String tokenEnvironmentVariable() {
        return TOKEN_ENV_VAR;
    }

    static String privateResourcePath() {
        return PRIVATE_RESOURCE_PATH;
    }

    boolean hasWriteToken() {
        return githubToken != null && !githubToken.isBlank();
    }

    AuthResult authenticate(String username, String password) {
        String normalizedUser = normalizeMinecraftUsername(username);
        if (!isValidMinecraftUsername(normalizedUser)) {
            return new AuthResult(false, "Usuario o contrasena incorrectos.", null);
        }
        if (password == null || password.isBlank()) {
            return new AuthResult(false, "Introduce tu contrasena.", null);
        }

        try {
            RepoFile repoFile = readFile(credentialsPath(normalizedUser));
            if (repoFile == null) {
                return new AuthResult(false, "Usuario o contrasena incorrectos.", null);
            }

            CredentialRecord credentials = CredentialRecord.fromJson(repoFile.content());
            if (credentials == null || !verifyPassword(password, credentials.passwordSaltBase64(), credentials.passwordHashBase64(), credentials.passwordIterations())) {
                return new AuthResult(false, "Usuario o contrasena incorrectos.", null);
            }

            return new AuthResult(
                    true,
                    "Bienvenido de nuevo.",
                    new RemoteAccount(credentials.minecraftUsername(), credentials.discordUsername(), credentials.discordUserId())
            );
        } catch (Exception ex) {
            return new AuthResult(false, friendlyErrorMessage(ex, "No se pudo validar la cuenta en GitHub."), null);
        }
    }

    RegistrationResult register(RegistrationRequest request) {
        Objects.requireNonNull(request, "request");

        String normalizedUser = normalizeMinecraftUsername(request.minecraftUsername());
        if (!isValidMinecraftUsername(normalizedUser)) {
            return new RegistrationResult(false, "El nombre de Minecraft debe tener entre 5 y 16 caracteres y solo usar letras, numeros o _.", null);
        }
        if (request.password() == null || request.password().length() < 4) {
            return new RegistrationResult(false, "La contrasena debe tener al menos 4 caracteres.", null);
        }
        if (!hasWriteToken()) {
            return new RegistrationResult(
                    false,
                    "Falta configurar el token de GitHub para registrar cuentas. Usa " + TOKEN_ENV_VAR + " o el archivo privado del launcher.",
                    null
            );
        }

        try {
            if (readFile(credentialsPath(normalizedUser)) != null) {
                return new RegistrationResult(false, "Ese nombre de Minecraft ya esta registrado.", null);
            }

            DiscordIdentity discordIdentity = findDiscordIdentity(request.discordUsername());
            if (discordIdentity == null) {
                return new RegistrationResult(false, "Ese usuario de Discord no aparece en el servidor. Revisa que este bien escrito.", null);
            }

            PasswordDigest digest = hashPassword(request.password());
            String displayMinecraftName = request.minecraftUsername().trim();
            String displayDiscordName = bestDiscordDisplay(discordIdentity);

            JsonObject credentialJson = new JsonObject();
            credentialJson.addProperty("schemaVersion", 1);
            credentialJson.addProperty("minecraftUsername", displayMinecraftName);
            credentialJson.addProperty("minecraftUsernameNormalized", normalizedUser);
            credentialJson.addProperty("registeredAt", Instant.now().toString());
            credentialJson.addProperty("accountStatus", "active");

            JsonObject passwordJson = new JsonObject();
            passwordJson.addProperty("algorithm", "PBKDF2WithHmacSHA256");
            passwordJson.addProperty("iterations", digest.iterations());
            passwordJson.addProperty("saltBase64", digest.saltBase64());
            passwordJson.addProperty("hashBase64", digest.hashBase64());
            credentialJson.add("password", passwordJson);

            JsonObject discordJson = new JsonObject();
            discordJson.addProperty("id", discordIdentity.id());
            discordJson.addProperty("username", discordIdentity.username());
            discordJson.addProperty("globalName", nullSafe(discordIdentity.globalName()));
            discordJson.addProperty("displayName", nullSafe(discordIdentity.displayName()));
            discordJson.addProperty("submittedValue", request.discordUsername().trim());
            credentialJson.add("discord", discordJson);

            createFile(
                    credentialsPath(normalizedUser),
                    credentialJson,
                    "Registrar cuenta " + displayMinecraftName
            );

            JsonObject roleRequestJson = new JsonObject();
            roleRequestJson.addProperty("schemaVersion", 1);
            roleRequestJson.addProperty("minecraftUsername", displayMinecraftName);
            roleRequestJson.addProperty("minecraftUsernameNormalized", normalizedUser);
            roleRequestJson.addProperty("discordUserId", discordIdentity.id());
            roleRequestJson.addProperty("discordUsername", displayDiscordName);
            roleRequestJson.addProperty("roleName", "Registrado");
            roleRequestJson.addProperty("status", "pending");
            roleRequestJson.addProperty("requestedAt", Instant.now().toString());

            upsertFile(
                    roleRequestPath(normalizedUser),
                    roleRequestJson,
                    "Solicitar rol Registrado para " + displayMinecraftName
            );

            return new RegistrationResult(
                    true,
                    "Cuenta creada correctamente.",
                    new RemoteAccount(displayMinecraftName, displayDiscordName, discordIdentity.id())
            );
        } catch (Exception ex) {
            return new RegistrationResult(false, friendlyErrorMessage(ex, "No se pudo crear la cuenta en GitHub."), null);
        }
    }

    private DiscordIdentity findDiscordIdentity(String rawUser) throws Exception {
        String lookup = normalizeDiscordLookup(rawUser);
        if (lookup.isBlank()) {
            return null;
        }
        RepoFile repoFile = readFile(DISCORD_USERS_PATH);
        if (repoFile == null) {
            throw new IOException("No existe discord/discord_users.json en la base de datos.");
        }

        JsonObject root = JsonParser.parseString(repoFile.content()).getAsJsonObject();
        JsonArray users = root.has("users") && root.get("users").isJsonArray()
                ? root.getAsJsonArray("users")
                : new JsonArray();

        for (JsonElement element : users) {
            if (!element.isJsonObject()) {
                continue;
            }
            DiscordIdentity identity = DiscordIdentity.fromJson(element.getAsJsonObject());
            if (identity == null) {
                continue;
            }
            for (String key : identity.lookupKeys()) {
                if (lookup.equals(normalizeDiscordLookup(key))) {
                    return identity;
                }
            }
            if (lookup.equals(normalizeDiscordLookup(identity.username()))
                    || lookup.equals(normalizeDiscordLookup(identity.globalName()))
                    || lookup.equals(normalizeDiscordLookup(identity.displayName()))) {
                return identity;
            }
        }
        return null;
    }

    private RepoFile readFile(String relativePath) throws Exception {
        URI uri = URI.create(GITHUB_API_BASE + encodePath(relativePath) + "?ref=" + GITHUB_BRANCH);
        HttpRequest request = requestBuilder(uri)
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub devolvio HTTP " + response.statusCode() + " al leer " + relativePath);
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        String sha = optionalString(root, "sha");
        String content = optionalString(root, "content");
        if (content == null) {
            throw new IOException("GitHub no devolvio contenido para " + relativePath);
        }

        String normalizedContent = content.replace("\n", "").replace("\r", "");
        byte[] decoded = Base64.getDecoder().decode(normalizedContent);
        return new RepoFile(relativePath, sha, new String(decoded, StandardCharsets.UTF_8));
    }

    private void createFile(String relativePath, JsonObject content, String message) throws Exception {
        writeFile(relativePath, content, message, null);
    }

    private void upsertFile(String relativePath, JsonObject content, String message) throws Exception {
        RepoFile existing = readFile(relativePath);
        writeFile(relativePath, content, message, existing == null ? null : existing.sha());
    }

    private void writeFile(String relativePath, JsonObject content, String message, String sha) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("message", message);
        body.addProperty("branch", GITHUB_BRANCH);
        body.addProperty("content", Base64.getEncoder().encodeToString(gson.toJson(content).getBytes(StandardCharsets.UTF_8)));
        if (sha != null && !sha.isBlank()) {
            body.addProperty("sha", sha);
        }

        URI uri = URI.create(GITHUB_API_BASE + encodePath(relativePath));
        HttpRequest request = requestBuilder(uri)
                .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = response.statusCode();
        if (code == 201 || code == 200) {
            return;
        }
        if (code == 422) {
            throw new IOException("GitHub rechazo la escritura porque el archivo ya existe o el contenido es invalido.");
        }
        throw new IOException("GitHub devolvio HTTP " + code + " al escribir " + relativePath);
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30));
        if (hasWriteToken()) {
            builder.header("Authorization", "Bearer " + githubToken);
        }
        return builder;
    }

    private static PasswordDigest hashPassword(String password) throws Exception {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        byte[] hash = derivePassword(password, salt, PASSWORD_ITERATIONS);
        return new PasswordDigest(
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash),
                PASSWORD_ITERATIONS
        );
    }

    private static boolean verifyPassword(String password, String saltBase64, String hashBase64, int iterations) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            byte[] expected = Base64.getDecoder().decode(hashBase64);
            byte[] computed = derivePassword(password, salt, iterations <= 0 ? PASSWORD_ITERATIONS : iterations);
            return MessageDigest.isEqual(expected, computed);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static byte[] derivePassword(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PASSWORD_KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    private static String encodePath(String relativePath) {
        String[] parts = relativePath.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(URI.create("https://placeholder/" + parts[i]).getRawPath().substring(1));
        }
        return out.toString();
    }

    private static String normalizeMinecraftUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isValidMinecraftUsername(String raw) {
        if (raw == null) {
            return false;
        }
        return raw.matches("[A-Za-z0-9_]{5,16}");
    }

    private static String normalizeDiscordLookup(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String bestDiscordDisplay(DiscordIdentity identity) {
        if (identity == null) {
            return "";
        }
        if (identity.globalName() != null && !identity.globalName().isBlank()) {
            return identity.globalName();
        }
        if (identity.displayName() != null && !identity.displayName().isBlank()) {
            return identity.displayName();
        }
        return identity.username();
    }

    private static String optionalString(JsonObject root, String key) {
        if (root == null || key == null || !root.has(key) || root.get(key).isJsonNull()) {
            return null;
        }
        try {
            return root.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String friendlyErrorMessage(Exception ex, String fallback) {
        if (ex == null) {
            return fallback;
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }

    private static String credentialsPath(String normalizedMinecraftUser) {
        return USERS_DIR + "/" + normalizedMinecraftUser + "_credentials.json";
    }

    private static String roleRequestPath(String normalizedMinecraftUser) {
        return ROLE_REQUESTS_DIR + "/" + normalizedMinecraftUser + ".json";
    }

    private static String resolveGithubToken() {
        String fromEnvironment = System.getenv(TOKEN_ENV_VAR);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }

        JsonObject localConfig = readPrivateJsonFromDisk();
        String fromDisk = optionalString(localConfig, "githubToken");
        if (fromDisk != null && !fromDisk.isBlank()) {
            return fromDisk.trim();
        }

        JsonObject resourceConfig = readPrivateJsonFromResources();
        String fromResource = optionalString(resourceConfig, "githubToken");
        return fromResource == null ? "" : fromResource.trim();
    }

    private static JsonObject readPrivateJsonFromDisk() {
        try {
            var localPath = LauncherStorage.launcherDirectory().resolve(LOCAL_PRIVATE_FILE);
            if (!Files.exists(localPath)) {
                return null;
            }
            return JsonParser.parseString(Files.readString(localPath, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JsonObject readPrivateJsonFromResources() {
        try (InputStream in = GithubDatabaseService.class.getResourceAsStream(PRIVATE_RESOURCE_PATH)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private record RepoFile(String path, String sha, String content) {
    }

    private record PasswordDigest(String saltBase64, String hashBase64, int iterations) {
    }

    private record CredentialRecord(
            String minecraftUsername,
            String discordUsername,
            String discordUserId,
            String passwordSaltBase64,
            String passwordHashBase64,
            int passwordIterations
    ) {
        static CredentialRecord fromJson(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonObject password = root.has("password") && root.get("password").isJsonObject()
                        ? root.getAsJsonObject("password")
                        : null;
                JsonObject discord = root.has("discord") && root.get("discord").isJsonObject()
                        ? root.getAsJsonObject("discord")
                        : null;
                if (password == null || discord == null) {
                    return null;
                }
                return new CredentialRecord(
                        optionalString(root, "minecraftUsername"),
                        optionalString(discord, "username"),
                        optionalString(discord, "id"),
                        optionalString(password, "saltBase64"),
                        optionalString(password, "hashBase64"),
                        parseInt(password, "iterations", PASSWORD_ITERATIONS)
                );
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private record DiscordIdentity(
            String id,
            String username,
            String globalName,
            String displayName,
            List<String> lookupKeys
    ) {
        static DiscordIdentity fromJson(JsonObject json) {
            if (json == null) {
                return null;
            }

            String id = optionalString(json, "id");
            String username = optionalString(json, "username");
            if (id == null || id.isBlank() || username == null || username.isBlank()) {
                return null;
            }

            LinkedHashSet<String> lookup = new LinkedHashSet<>();
            JsonArray keys = json.has("lookupKeys") && json.get("lookupKeys").isJsonArray()
                    ? json.getAsJsonArray("lookupKeys")
                    : null;
            if (keys != null) {
                for (JsonElement element : keys) {
                    if (element != null && element.isJsonPrimitive()) {
                        String value = element.getAsString();
                        if (value != null && !value.isBlank()) {
                            lookup.add(value);
                        }
                    }
                }
            }

            String globalName = optionalString(json, "globalName");
            String displayName = optionalString(json, "displayName");
            if (lookup.isEmpty()) {
                lookup.add(username);
                if (globalName != null && !globalName.isBlank()) {
                    lookup.add(globalName);
                }
                if (displayName != null && !displayName.isBlank()) {
                    lookup.add(displayName);
                }
            }

            return new DiscordIdentity(id, username, globalName, displayName, new ArrayList<>(lookup));
        }
    }

    private static int parseInt(JsonObject root, String key, int fallback) {
        if (root == null || key == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return root.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
