package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class SupabaseDatabaseService {

    private static final int PASSWORD_ITERATIONS = 60_000;
    private static final int PASSWORD_KEY_BITS = 256;
    private static final int VERIFICATION_CODE_TTL_MINUTES = 10;
    private static final SecureRandom RNG = new SecureRandom();
    private static final String PASSWORD_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String REGISTRATION_VERIFICATION_JOB_TYPE = "registration_verification";

    private final SupabaseConfig.RuntimeConfig config = SupabaseConfig.resolve();
    private final SupabaseRestClient supabase = new SupabaseRestClient(
            config.url(),
            config.hasServiceRoleKey() ? config.serviceRoleKey() : config.anonKey()
    );

    record RemoteAccount(String minecraftUsername, String discordUsername, String discordUserId) {
    }

    record AuthResult(boolean success, String message, RemoteAccount account) {
    }

    record RegistrationRequest(String minecraftUsername, String password, String discordUsername) {
    }

    record RegistrationResult(boolean success, String message, RemoteAccount account) {
    }

    record RegistrationStartResult(boolean success, String message, PendingVerification pendingVerification) {
    }

    record PendingVerification(String verificationId, String discordDisplayName, String expiresAt) {
    }

    record VerificationRequest(String verificationId, String verificationCode) {
    }

    record VerificationResult(boolean success, String message, RemoteAccount account) {
    }

    boolean hasWriteToken() {
        return supabase.isConfigured();
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
            JsonObject user = supabase.selectSingle("app_users", Map.of(
                    "minecraft_username_normalized", "eq." + normalizedUser
            ), null);
            if (user == null) {
                return new AuthResult(false, "Usuario o contrasena incorrectos.", null);
            }

            String passwordHash = stringValue(user, "password_hash");
            if (!verifyPassword(password, passwordHash)) {
                return new AuthResult(false, "Usuario o contrasena incorrectos.", null);
            }

            return new AuthResult(
                    true,
                    "Bienvenido de nuevo.",
                    new RemoteAccount(
                            stringValue(user, "minecraft_username"),
                            stringValue(user, "discord_username"),
                            stringValue(user, "discord_user_id")
                    )
            );
        } catch (Exception ex) {
            return new AuthResult(false, friendlyErrorMessage(ex, "No se pudo validar la cuenta en Supabase."), null);
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
            return new RegistrationResult(false, "Falta configurar Supabase en el launcher.", null);
        }

        try {
            JsonObject existing = supabase.selectSingle("app_users", Map.of(
                    "minecraft_username_normalized", "eq." + normalizedUser
            ), null);
            if (existing != null) {
                return new RegistrationResult(false, "Ese nombre de Minecraft ya esta registrado.", null);
            }

            DiscordIdentity discordIdentity = findDiscordIdentity(request.discordUsername());
            if (discordIdentity == null) {
                return new RegistrationResult(false, "Ese usuario de Discord no aparece en el servidor. Revisa que este bien escrito.", null);
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("minecraft_username", request.minecraftUsername().trim());
            payload.addProperty("minecraft_username_normalized", normalizedUser);
            payload.addProperty("password_hash", hashPassword(request.password()));
            payload.addProperty("discord_username", bestDiscordDisplay(discordIdentity));
            payload.addProperty("discord_lookup_normalized", normalizeDiscordLookup(request.discordUsername()));
            payload.addProperty("discord_user_id", discordIdentity.id());
            payload.addProperty("is_registered", true);
            payload.addProperty("created_at", Instant.now().toString());
            payload.addProperty("updated_at", Instant.now().toString());

            JsonObject created = supabase.insert("app_users", payload);
            if (created == null) {
                return new RegistrationResult(false, "No se pudo crear la cuenta.", null);
            }

            return new RegistrationResult(
                    true,
                    "Cuenta creada correctamente.",
                    new RemoteAccount(
                            stringValue(created, "minecraft_username"),
                            stringValue(created, "discord_username"),
                            stringValue(created, "discord_user_id")
                    )
            );
        } catch (Exception ex) {
            return new RegistrationResult(false, friendlyErrorMessage(ex, "No se pudo crear la cuenta en Supabase."), null);
        }
    }

    RegistrationStartResult startRegistration(RegistrationRequest request) {
        Objects.requireNonNull(request, "request");

        String normalizedUser = normalizeMinecraftUsername(request.minecraftUsername());
        if (!isValidMinecraftUsername(normalizedUser)) {
            return new RegistrationStartResult(false, "El nombre de Minecraft debe tener entre 5 y 16 caracteres y solo usar letras, numeros o _.", null);
        }
        if (request.password() == null || request.password().length() < 4) {
            return new RegistrationStartResult(false, "La contrasena debe tener al menos 4 caracteres.", null);
        }
        if (!hasWriteToken()) {
            return new RegistrationStartResult(false, "Falta configurar Supabase en el launcher.", null);
        }

        try {
            JsonObject existing = supabase.selectSingle("app_users", Map.of(
                    "minecraft_username_normalized", "eq." + normalizedUser
            ), null);
            if (existing != null) {
                return new RegistrationStartResult(false, "Ese nombre de Minecraft ya esta registrado.", null);
            }

            DiscordIdentity discordIdentity = findDiscordIdentity(request.discordUsername());
            if (discordIdentity == null) {
                return new RegistrationStartResult(false, "Ese usuario de Discord no aparece en el servidor. Revisa que este bien escrito.", null);
            }

            JsonObject discordExisting = supabase.selectSingle("app_users", Map.of(
                    "discord_user_id", "eq." + discordIdentity.id()
            ), null);
            if (discordExisting != null) {
                return new RegistrationStartResult(false, "Ese usuario de Discord ya esta vinculado a otra cuenta.", null);
            }

            expireActiveVerificationChallenges(normalizedUser, discordIdentity.id());

            String verificationCode = String.format(Locale.ROOT, "%06d", RNG.nextInt(1_000_000));
            Instant expiresAt = Instant.now().plus(VERIFICATION_CODE_TTL_MINUTES, ChronoUnit.MINUTES);

            JsonObject payload = new JsonObject();
            payload.addProperty("minecraft_username", request.minecraftUsername().trim());
            payload.addProperty("minecraft_username_normalized", normalizedUser);
            payload.addProperty("password_hash", hashPassword(request.password()));
            payload.addProperty("discord_username", bestDiscordDisplay(discordIdentity));
            payload.addProperty("discord_lookup_normalized", normalizeDiscordLookup(request.discordUsername()));
            payload.addProperty("discord_user_id", discordIdentity.id());
            payload.addProperty("verification_code", verificationCode);
            payload.addProperty("expires_at", expiresAt.toString());
            payload.addProperty("delivery_status", "pending");

            JsonObject jobPayload = new JsonObject();
            jobPayload.addProperty("job_type", REGISTRATION_VERIFICATION_JOB_TYPE);
            jobPayload.addProperty("status", "pending");
            jobPayload.addProperty("guild_id", "registration");
            jobPayload.add("payload", payload);
            jobPayload.addProperty("created_at", Instant.now().toString());

            JsonObject createdJob = supabase.insert("discord_role_jobs", jobPayload);
            if (createdJob == null) {
                return new RegistrationStartResult(false, "No se pudo iniciar la verificacion por Discord.", null);
            }

            return new RegistrationStartResult(
                    true,
                    "Te enviamos un codigo por MD a Discord.",
                    new PendingVerification(
                            stringValue(createdJob, "id"),
                            bestDiscordDisplay(discordIdentity),
                            expiresAt.toString()
                    )
            );
        } catch (Exception ex) {
            return new RegistrationStartResult(false, friendlyErrorMessage(ex, "No se pudo iniciar la verificacion."), null);
        }
    }

    VerificationResult finishRegistration(VerificationRequest request) {
        Objects.requireNonNull(request, "request");
        String verificationId = safeTrim(request.verificationId());
        String verificationCode = safeTrim(request.verificationCode());
        if (verificationId.isBlank()) {
            return new VerificationResult(false, "No se encontro la verificacion pendiente.", null);
        }
        if (verificationCode.isBlank()) {
            return new VerificationResult(false, "Introduce el codigo de verificacion.", null);
        }

        try {
            JsonObject job = supabase.selectSingle("discord_role_jobs", Map.of(
                    "id", "eq." + verificationId
            ), null);
            if (job == null || !REGISTRATION_VERIFICATION_JOB_TYPE.equalsIgnoreCase(stringValue(job, "job_type"))) {
                return new VerificationResult(false, "No se encontro la verificacion pendiente.", null);
            }

            String status = stringValue(job, "status");
            JsonObject payload = objectValue(job, "payload");
            if (payload == null) {
                return new VerificationResult(false, "La verificacion ya no es valida.", null);
            }

            if ("completed".equalsIgnoreCase(status)) {
                return new VerificationResult(false, "Ese codigo ya fue usado.", null);
            }
            if ("expired".equalsIgnoreCase(status)) {
                return new VerificationResult(false, "Ese codigo ya vencio. Vuelve a registrarte para recibir uno nuevo.", null);
            }

            Instant expiresAt = parseInstant(stringValue(payload, "expires_at"));
            if (expiresAt == null || Instant.now().isAfter(expiresAt)) {
                markVerificationJobStatus(job, payload, "expired", "expired");
                return new VerificationResult(false, "El codigo ya vencio. Vuelve a registrarte para recibir uno nuevo.", null);
            }

            if (!verificationCode.equals(stringValue(payload, "verification_code"))) {
                return new VerificationResult(false, "El codigo es incorrecto.", null);
            }

            String minecraftUsername = stringValue(payload, "minecraft_username");
            String normalizedUser = normalizeMinecraftUsername(stringValue(payload, "minecraft_username_normalized"));
            String discordUsername = stringValue(payload, "discord_username");
            String discordUserId = stringValue(payload, "discord_user_id");

            JsonObject existing = supabase.selectSingle("app_users", Map.of(
                    "minecraft_username_normalized", "eq." + normalizedUser
            ), null);
            if (existing != null) {
                markVerificationJobStatus(job, payload, "expired", "username_taken");
                return new VerificationResult(false, "Ese nombre de Minecraft ya se registro mientras validabas el codigo.", null);
            }

            JsonObject existingDiscord = supabase.selectSingle("app_users", Map.of(
                    "discord_user_id", "eq." + discordUserId
            ), null);
            if (existingDiscord != null) {
                markVerificationJobStatus(job, payload, "expired", "discord_taken");
                return new VerificationResult(false, "Ese usuario de Discord ya esta vinculado a otra cuenta.", null);
            }

            JsonObject userPayload = new JsonObject();
            userPayload.addProperty("minecraft_username", minecraftUsername);
            userPayload.addProperty("minecraft_username_normalized", normalizedUser);
            userPayload.addProperty("password_hash", stringValue(payload, "password_hash"));
            userPayload.addProperty("discord_username", discordUsername);
            userPayload.addProperty("discord_lookup_normalized", stringValue(payload, "discord_lookup_normalized"));
            userPayload.addProperty("discord_user_id", discordUserId);
            userPayload.addProperty("is_registered", true);
            userPayload.addProperty("created_at", Instant.now().toString());
            userPayload.addProperty("updated_at", Instant.now().toString());

            JsonObject created = supabase.insert("app_users", userPayload);
            if (created == null) {
                return new VerificationResult(false, "No se pudo crear la cuenta.", null);
            }

            markVerificationJobStatus(job, payload, "completed", "verified");
            return new VerificationResult(
                    true,
                    "Cuenta creada correctamente.",
                    new RemoteAccount(
                            stringValue(created, "minecraft_username"),
                            stringValue(created, "discord_username"),
                            stringValue(created, "discord_user_id")
                    )
            );
        } catch (Exception ex) {
            return new VerificationResult(false, friendlyErrorMessage(ex, "No se pudo validar el codigo de Discord."), null);
        }
    }

    private DiscordIdentity findDiscordIdentity(String rawUser) throws Exception {
        String lookup = normalizeDiscordLookup(rawUser);
        if (lookup.isBlank()) {
            return null;
        }

        JsonArray users = supabase.select("discord_members_cache", Map.of(), "discord_username.asc", 5000);
        for (JsonElement element : users) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject row = element.getAsJsonObject();
            DiscordIdentity identity = DiscordIdentity.fromJson(row);
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

    private static String hashPassword(String password) throws Exception {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        byte[] hash = derivePassword(password, salt, PASSWORD_ITERATIONS);
        return PASSWORD_ALGORITHM
                + "$" + PASSWORD_ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    private static boolean verifyPassword(String password, String encodedDigest) {
        try {
            if (encodedDigest == null || encodedDigest.isBlank()) {
                return false;
            }
            String[] parts = encodedDigest.split("\\$");
            if (parts.length != 4) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] computed = derivePassword(password, salt, iterations);
            return MessageDigest.isEqual(expected, computed);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static byte[] derivePassword(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PASSWORD_KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PASSWORD_ALGORITHM);
        return factory.generateSecret(spec).getEncoded();
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

    private static String stringValue(JsonObject root, String key) {
        if (root == null || key == null || !root.has(key) || root.get(key).isJsonNull()) {
            return "";
        }
        try {
            return root.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String friendlyErrorMessage(Exception ex, String fallback) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return fallback;
        }
        return ex.getMessage();
    }

    private void expireActiveVerificationChallenges(String normalizedMinecraftUsername, String discordUserId) throws Exception {
        JsonArray jobs = supabase.select("discord_role_jobs", Map.of(
                "job_type", "eq." + REGISTRATION_VERIFICATION_JOB_TYPE
        ), "created_at.desc", 5000);
        for (JsonElement element : jobs) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject job = element.getAsJsonObject();
            String status = stringValue(job, "status");
            if (!"pending".equalsIgnoreCase(status) && !"sent".equalsIgnoreCase(status) && !"delivery_failed".equalsIgnoreCase(status)) {
                continue;
            }
            JsonObject payload = objectValue(job, "payload");
            if (payload == null) {
                continue;
            }
            String jobUser = normalizeMinecraftUsername(stringValue(payload, "minecraft_username_normalized"));
            String jobDiscordId = stringValue(payload, "discord_user_id");
            if (normalizedMinecraftUsername.equals(jobUser) || (!discordUserId.isBlank() && discordUserId.equals(jobDiscordId))) {
                markVerificationJobStatus(job, payload, "expired", "superseded");
            }
        }
    }

    private void markVerificationJobStatus(JsonObject job, JsonObject payload, String status, String deliveryStatus) throws Exception {
        if (job == null) {
            return;
        }
        JsonObject update = new JsonObject();
        update.addProperty("status", status);
        update.addProperty("processed_at", Instant.now().toString());
        JsonObject nextPayload = payload == null ? new JsonObject() : payload.deepCopy();
        if (deliveryStatus != null && !deliveryStatus.isBlank()) {
            nextPayload.addProperty("delivery_status", deliveryStatus);
        }
        update.add("payload", nextPayload);
        supabase.update("discord_role_jobs", update, Map.of("id", "eq." + stringValue(job, "id")));
    }

    private static JsonObject objectValue(JsonObject root, String key) {
        if (root == null || key == null || !root.has(key) || root.get(key).isJsonNull()) {
            return null;
        }
        try {
            return root.getAsJsonObject(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Instant parseInstant(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : Instant.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeTrim(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private record DiscordIdentity(
            String id,
            String username,
            String globalName,
            String displayName,
            java.util.List<String> lookupKeys
    ) {
        static DiscordIdentity fromJson(JsonObject root) {
            if (root == null) {
                return null;
            }
            String id = stringValue(root, "discord_user_id");
            String username = stringValue(root, "discord_username");
            if (id.isBlank() || username.isBlank()) {
                return null;
            }

            java.util.List<String> keys = new java.util.ArrayList<>();
            JsonElement rawLookup = root.get("lookup_keys");
            if (rawLookup != null && rawLookup.isJsonArray()) {
                for (JsonElement element : rawLookup.getAsJsonArray()) {
                    if (element != null && element.isJsonPrimitive()) {
                        String key = element.getAsString();
                        if (key != null && !key.isBlank()) {
                            keys.add(key);
                        }
                    }
                }
            }

            return new DiscordIdentity(
                    id,
                    username,
                    stringValue(root, "discord_global_name"),
                    stringValue(root, "discord_display_name"),
                    java.util.List.copyOf(keys)
            );
        }
    }
}
