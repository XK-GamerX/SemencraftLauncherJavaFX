package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class SupabaseConfig {

    static final String URL_ENV = "SEMENCRAFT_SUPABASE_URL";
    static final String ANON_KEY_ENV = "SEMENCRAFT_SUPABASE_ANON_KEY";
    static final String SERVICE_ROLE_KEY_ENV = "SEMENCRAFT_SUPABASE_SERVICE_ROLE_KEY";

    private static final String PRIVATE_RESOURCE_PATH = "private/database-private.json";
    private static final String LOCAL_PRIVATE_FILE = "database-private.json";

    private SupabaseConfig() {
    }

    static RuntimeConfig resolve() {
        JsonObject diskConfig = readPrivateJsonFromDisk();
        return new RuntimeConfig(
                firstNonBlank(System.getenv(URL_ENV), stringValue(diskConfig, "supabaseUrl")),
                firstNonBlank(System.getenv(ANON_KEY_ENV), stringValue(diskConfig, "supabaseAnonKey")),
                firstNonBlank(System.getenv(SERVICE_ROLE_KEY_ENV), stringValue(diskConfig, "supabaseServiceRoleKey"))
        );
    }

    private static JsonObject readPrivateJsonFromDisk() {
        try {
            Path runtimeConfig = LauncherStorage.launcherDirectory().resolve(LOCAL_PRIVATE_FILE);
            if (Files.exists(runtimeConfig)) {
                return JsonParser.parseString(Files.readString(runtimeConfig, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception ignored) {
        }

        try (InputStream in = SupabaseConfig.class.getResourceAsStream(PRIVATE_RESOURCE_PATH)) {
            if (in != null) {
                return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception ignored) {
        }

        return new JsonObject();
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    record RuntimeConfig(String url, String anonKey, String serviceRoleKey) {
        boolean hasUrl() {
            return url != null && !url.isBlank();
        }

        boolean hasAnonKey() {
            return anonKey != null && !anonKey.isBlank();
        }

        boolean hasServiceRoleKey() {
            return serviceRoleKey != null && !serviceRoleKey.isBlank();
        }
    }
}
