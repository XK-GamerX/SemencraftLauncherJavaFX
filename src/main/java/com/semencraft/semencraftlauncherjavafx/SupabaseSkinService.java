package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

final class SupabaseSkinService {

    static final String SKIN_BUCKET = "player-skins";
    private static final int TEAM_CHAT_CACHE_TTL_SECONDS = 20;
    private static final String MODEL_WIDE = "wide";
    private static final String MODEL_SLIM = "slim";
    private static final int CACHE_TTL_SECONDS = 30;

    private final SupabaseConfig.RuntimeConfig config = SupabaseConfig.resolve();
    private final SupabaseRestClient supabase = new SupabaseRestClient(
            config.url(),
            config.hasServiceRoleKey() ? config.serviceRoleKey() : config.anonKey()
    );

    record LoadResult(boolean success, String message, SkinContext context) {
    }

    record ActionResult(boolean success, String message, SkinContext context) {
    }

    record SkinContext(
            String minecraftUsername,
            String normalizedMinecraftUsername,
            boolean hasCustomSkin,
            String skinBase64,
            String modelId,
            String updatedAt,
            String textureUrl,
            String contentSha1
    ) {
    }

    boolean isConfigured() {
        return config.hasUrl() && supabase.isConfigured();
    }

    LoadResult loadSkinContext(String minecraftUsername) {
        String normalizedUser = normalizeMinecraftUsername(minecraftUsername);
        if (!SupabaseDatabaseService.isValidMinecraftUsername(normalizedUser)) {
            return new LoadResult(false, "Usuario invalido.", null);
        }

        try {
            SkinContext context = loadSkinContextOrThrow(defaultDisplayMinecraft(minecraftUsername), normalizedUser);
            return new LoadResult(true, "", context);
        } catch (Exception ex) {
            return new LoadResult(false, friendlyErrorMessage(ex, "No se pudo cargar la skin."), null);
        }
    }

    ActionResult saveSkin(String minecraftUsername, String modelId, String skinBase64) {
        String normalizedUser = normalizeMinecraftUsername(minecraftUsername);
        if (!SupabaseDatabaseService.isValidMinecraftUsername(normalizedUser)) {
            return new ActionResult(false, "Usuario invalido.", null);
        }
        if (!isConfigured()) {
            return new ActionResult(false, "Falta configurar Supabase en el launcher.", null);
        }

        String normalizedModel = normalizeModel(modelId);
        if (skinBase64 == null || skinBase64.isBlank()) {
            return new ActionResult(false, "Primero debes subir una skin PNG valida.", null);
        }

        try {
            byte[] skinBytes = Base64.getDecoder().decode(skinBase64);
            BufferedImage skin = readSkinImage(skinBytes);
            validateSkinDimensions(skin);

            String displayUsername = defaultDisplayMinecraft(minecraftUsername);
            String skinObjectPath = normalizedUser + "/skin.png";
            String metaObjectPath = normalizedUser + "/meta.json";
            String updatedAt = Instant.now().toString();
            String contentSha1 = sha1Hex(skinBytes);
            String textureUrl = supabase.publicObjectUrl(SKIN_BUCKET, skinObjectPath);
            boolean legacySkin = isClassicLegacySkin(skin);
            SkinContext existingContext = null;
            try {
                existingContext = loadSkinContextOrThrow(displayUsername, normalizedUser);
            } catch (Exception ignored) {
            }

            boolean mustUploadSkinBinary = existingContext == null
                    || !existingContext.hasCustomSkin()
                    || !contentSha1.equals(defaultString(existingContext.contentSha1(), ""));
            if (mustUploadSkinBinary) {
                supabase.uploadPublicObject(SKIN_BUCKET, skinObjectPath, skinBytes, "image/png");
            }

            JsonObject meta = new JsonObject();
            meta.addProperty("schemaVersion", 1);
            meta.addProperty("minecraftUsername", displayUsername);
            meta.addProperty("normalizedMinecraftUsername", normalizedUser);
            meta.addProperty("model", normalizedModel);
            meta.addProperty("updatedAt", updatedAt);
            meta.addProperty("contentSha1", contentSha1);
            meta.addProperty("legacySkin", legacySkin);
            meta.addProperty("skinPath", skinObjectPath);
            meta.addProperty("textureUrl", textureUrl);
            supabase.uploadPublicObject(
                    SKIN_BUCKET,
                    metaObjectPath,
                    meta.toString().getBytes(StandardCharsets.UTF_8),
                    "application/json"
            );

            return new ActionResult(
                    true,
                    "Tu skin se guardo correctamente.",
                    new SkinContext(
                            displayUsername,
                            normalizedUser,
                            true,
                            skinBase64,
                            normalizedModel,
                            updatedAt,
                            textureUrl,
                            contentSha1
                    )
            );
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo guardar la skin."), null);
        }
    }

    static void writeRuntimeConfig(Path gameDir, String minecraftUsername) throws IOException {
        SupabaseConfig.RuntimeConfig runtimeConfig = SupabaseConfig.resolve();
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("enabled", runtimeConfig.hasUrl());
        root.addProperty("supabaseUrl", runtimeConfig.url());
        root.addProperty("supabaseAnonKey", runtimeConfig.anonKey());
        root.addProperty("bucket", SKIN_BUCKET);
        root.addProperty("cacheTtlSeconds", CACHE_TTL_SECONDS);
        root.addProperty("teamCacheTtlSeconds", TEAM_CHAT_CACHE_TTL_SECONDS);
        root.addProperty("launcherUsername", defaultDisplayMinecraft(minecraftUsername));

        Path configPath = gameDir.resolve("config").resolve("semencraft-skins.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(
                configPath,
                root.toString(),
                StandardCharsets.UTF_8
        );
    }

    private SkinContext loadSkinContextOrThrow(String displayUsername, String normalizedUser) throws Exception {
        byte[] metaBytes = supabase.downloadPublicObject(SKIN_BUCKET, normalizedUser + "/meta.json");
        if (metaBytes == null || metaBytes.length == 0) {
            return new SkinContext(displayUsername, normalizedUser, false, "", MODEL_WIDE, "", "", "");
        }

        JsonObject meta = JsonParser.parseString(new String(metaBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        String skinPath = defaultString(stringValue(meta, "skinPath"), normalizedUser + "/skin.png");
        byte[] skinBytes = supabase.downloadPublicObject(SKIN_BUCKET, skinPath);
        if (skinBytes == null || skinBytes.length == 0) {
            return new SkinContext(displayUsername, normalizedUser, false, "", MODEL_WIDE, "", "", "");
        }

        BufferedImage image = readSkinImage(skinBytes);
        validateSkinDimensions(image);

        String resolvedDisplay = defaultString(stringValue(meta, "minecraftUsername"), displayUsername);
        String resolvedModel = normalizeModel(stringValue(meta, "model"));
        String updatedAt = stringValue(meta, "updatedAt");
        String textureUrl = defaultString(stringValue(meta, "textureUrl"), supabase.publicObjectUrl(SKIN_BUCKET, skinPath));
        String contentSha1 = defaultString(stringValue(meta, "contentSha1"), sha1Hex(skinBytes));

        return new SkinContext(
                resolvedDisplay,
                normalizedUser,
                true,
                Base64.getEncoder().encodeToString(skinBytes),
                resolvedModel,
                updatedAt,
                textureUrl,
                contentSha1
        );
    }

    static String normalizeModel(String rawModel) {
        if (rawModel == null) {
            return MODEL_WIDE;
        }
        String normalized = rawModel.trim().toLowerCase(Locale.ROOT);
        return MODEL_SLIM.equals(normalized) ? MODEL_SLIM : MODEL_WIDE;
    }

    static BufferedImage decodeSkinBase64(String skinBase64) throws Exception {
        if (skinBase64 == null || skinBase64.isBlank()) {
            throw new IOException("Skin vacia.");
        }
        byte[] bytes = Base64.getDecoder().decode(skinBase64);
        BufferedImage image = readSkinImage(bytes);
        validateSkinDimensions(image);
        return image;
    }

    static BufferedImage readSkinFile(Path file) throws Exception {
        if (file == null || !Files.exists(file)) {
            throw new IOException("No se encontro la imagen.");
        }
        BufferedImage image = ImageIO.read(file.toFile());
        validateSkinDimensions(image);
        return image;
    }

    static boolean isClassicLegacySkin(BufferedImage image) {
        return image != null && image.getWidth() == 64 && image.getHeight() == 32;
    }

    private static BufferedImage readSkinImage(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("Skin vacia.");
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("El archivo no es una imagen PNG valida.");
            }
            return image;
        }
    }

    private static void validateSkinDimensions(BufferedImage image) throws Exception {
        if (image == null) {
            throw new IOException("Imagen invalida.");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        boolean modern = width == 64 && height == 64;
        boolean legacy = width == 64 && height == 32;
        if (!modern && !legacy) {
            throw new IOException("La skin debe ser un PNG de 64x64 o 64x32.");
        }
    }

    private static String sha1Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(data);
        StringBuilder out = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            out.append(String.format(Locale.ROOT, "%02x", b));
        }
        return out.toString();
    }

    private static String normalizeMinecraftUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String defaultDisplayMinecraft(String raw) {
        return raw == null ? "" : raw.trim();
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

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String friendlyErrorMessage(Exception ex, String fallback) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return fallback;
        }
        return ex.getMessage();
    }
}
