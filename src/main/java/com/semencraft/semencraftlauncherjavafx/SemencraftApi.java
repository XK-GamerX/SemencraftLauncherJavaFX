package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cliente de la funcion sc-api de Supabase: login, registro, sesion y subida de archivos.
 * Guarda el token de la sesion del jugador, que tambien manda SupabaseRestClient en cada consulta.
 */
final class SemencraftApi {

    static final String WEB_URL = "https://xk-gamerx.github.io/semencraft/";
    private static final String USER_AGENT = "SemencraftLauncher/1.3";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private static volatile String sessionToken = "";
    /** Acceso de la cuenta al launcher (granted, pending o revoked) segun la ultima respuesta del servidor. */
    private static volatile String accessStatus = "granted";

    private SemencraftApi() {
    }

    /** Respuesta de la API. {@code code} distingue errores: no_session, banned, access_pending, network... */
    record Result(boolean ok, String error, String code, JsonObject body) {
        boolean networkError() {
            return "network".equals(code);
        }

        /** La sesion ya no sirve: hay que volver a iniciar sesion. */
        boolean sessionRejected() {
            return code != null && (code.equals("no_session") || code.equals("banned") || code.equals("expired"));
        }
    }

    static String sessionToken() {
        return sessionToken;
    }

    static void setSessionToken(String token) {
        sessionToken = token == null ? "" : token.trim();
    }

    static String accessStatus() {
        return accessStatus;
    }

    static boolean hasLauncherAccess() {
        return "granted".equals(accessStatus);
    }

    /** Guarda el acceso que trae el perfil de una respuesta (login o me). */
    static void rememberAccess(JsonObject body) {
        if (body != null && body.has("profile") && body.get("profile").isJsonObject()) {
            String access = string(body.getAsJsonObject("profile"), "access");
            if (!access.isBlank()) {
                accessStatus = access;
            }
        }
    }

    static boolean hasSession() {
        return !sessionToken.isBlank();
    }

    static Result call(String action, JsonObject body) {
        SupabaseConfig.RuntimeConfig config = SupabaseConfig.resolve();
        JsonObject payload = body == null ? new JsonObject() : body.deepCopy();
        payload.addProperty("action", action);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(config.url() + "/functions/v1/sc-api"))
                .timeout(Duration.ofSeconds(40))
                .header("apikey", config.anonKey())
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8));
        if (hasSession()) {
            request.header("x-sc-session", sessionToken);
        }
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject json = parse(response.body());
            boolean ok = json.has("ok") && json.get("ok").getAsBoolean() && response.statusCode() / 100 == 2;
            if (ok) {
                return new Result(true, "", "", json);
            }
            String error = string(json, "error");
            return new Result(false, error.isBlank() ? "El servidor respondio con un error (HTTP " + response.statusCode() + ")." : error,
                    string(json, "code").isBlank() ? "error" : string(json, "code"), json);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(false, "Se cancelo la conexion.", "network", new JsonObject());
        } catch (Exception ex) {
            return new Result(false, "No hay conexion con el servidor de Semencraft. Revisa tu internet.", "network", new JsonObject());
        }
    }

    /** Perfil de la sesion guardada. Falla si la cuenta esta baneada o la sesion se cerro; el acceso queda en accessStatus. */
    static Result validateLauncherSession() {
        JsonObject body = new JsonObject();
        body.addProperty("client", "launcher");
        body.addProperty("limited", true); // sabe limitarse si la cuenta no tiene acceso
        Result result = call("me", body);
        if (result.ok()) {
            rememberAccess(result.body());
        }
        return result;
    }

    static Result logout() {
        Result result = call("logout", new JsonObject());
        setSessionToken("");
        return result;
    }

    static String string(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return "";
        }
        JsonElement value = json.get(key);
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    private static JsonObject parse(String raw) {
        try {
            JsonElement element = JsonParser.parseString(raw == null ? "" : raw);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception ex) {
            return new JsonObject();
        }
    }
}
