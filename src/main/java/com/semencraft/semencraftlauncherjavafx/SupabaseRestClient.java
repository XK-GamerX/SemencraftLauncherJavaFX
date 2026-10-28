package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class SupabaseRestClient {

    private static final String USER_AGENT = "SemencraftLauncher/1.0";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String baseUrl;
    private final String apiKey;

    SupabaseRestClient(String baseUrl, String apiKey) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    boolean isConfigured() {
        return !baseUrl.isBlank() && !apiKey.isBlank();
    }

    JsonArray select(String table, Map<String, String> filters, String order, Integer limit) throws Exception {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("select", "*");
        if (filters != null) {
            query.putAll(filters);
        }
        if (order != null && !order.isBlank()) {
            query.put("order", order);
        }
        if (limit != null && limit > 0) {
            query.put("limit", String.valueOf(limit));
        }
        JsonElement body = requestJson("GET", "/rest/v1/" + table, query, null, preferRepresentationHeader(false), "application/json");
        return body != null && body.isJsonArray() ? body.getAsJsonArray() : new JsonArray();
    }

    JsonObject selectSingle(String table, Map<String, String> filters, String order) throws Exception {
        JsonArray rows = select(table, filters, order, 1);
        return rows.isEmpty() || !rows.get(0).isJsonObject() ? null : rows.get(0).getAsJsonObject();
    }

    JsonObject insert(String table, JsonObject payload) throws Exception {
        JsonElement body = requestJson("POST", "/rest/v1/" + table, Map.of(), payload, preferRepresentationHeader(true), "application/json");
        if (body != null && body.isJsonArray() && !body.getAsJsonArray().isEmpty() && body.getAsJsonArray().get(0).isJsonObject()) {
            return body.getAsJsonArray().get(0).getAsJsonObject();
        }
        return null;
    }

    JsonObject upsert(String table, JsonObject payload, String onConflict) throws Exception {
        Map<String, String> query = new LinkedHashMap<>();
        if (onConflict != null && !onConflict.isBlank()) {
            query.put("on_conflict", onConflict);
        }
        JsonElement body = requestJson(
                "POST",
                "/rest/v1/" + table,
                query,
                payload,
                "return=representation,resolution=merge-duplicates",
                "application/json"
        );
        if (body != null && body.isJsonArray() && !body.getAsJsonArray().isEmpty() && body.getAsJsonArray().get(0).isJsonObject()) {
            return body.getAsJsonArray().get(0).getAsJsonObject();
        }
        return null;
    }

    JsonObject update(String table, JsonObject payload, Map<String, String> filters) throws Exception {
        JsonElement body = requestJson("PATCH", "/rest/v1/" + table, filters, payload, preferRepresentationHeader(true), "application/json");
        if (body != null && body.isJsonArray() && !body.getAsJsonArray().isEmpty() && body.getAsJsonArray().get(0).isJsonObject()) {
            return body.getAsJsonArray().get(0).getAsJsonObject();
        }
        return null;
    }

    void delete(String table, Map<String, String> filters) throws Exception {
        requestJson("DELETE", "/rest/v1/" + table, filters, null, "return=minimal", "application/json");
    }

    JsonElement rpc(String functionName, JsonObject payload) throws Exception {
        return requestJson("POST", "/rest/v1/rpc/" + functionName, Map.of(), payload, preferRepresentationHeader(false), "application/json");
    }

    String uploadPublicObject(String bucket, String objectPath, byte[] content, String contentType) throws Exception {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("upsert", "true");
        requestBytes(
                "POST",
                "/storage/v1/object/" + bucket + "/" + encodePathSegment(objectPath),
                query,
                content,
                "return=minimal",
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                Map.of("x-upsert", "true")
        );
        return objectPath;
    }

    byte[] downloadPublicObject(String bucket, String objectPath) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        publicObjectUrl(bucket, objectPath)))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 400 || response.statusCode() == 404) {
            return new byte[0];
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Supabase devolvio HTTP " + response.statusCode() + " al descargar " + objectPath);
        }
        return response.body();
    }

    String publicObjectUrl(String bucket, String objectPath) {
        return baseUrl + "/storage/v1/object/public/" + bucket + "/" + encodePathSegment(objectPath);
    }

    private JsonElement requestJson(
            String method,
            String path,
            Map<String, String> query,
            JsonElement payload,
            String prefer,
            String contentType
    ) throws Exception {
        byte[] bytes = payload == null ? null : payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = requestText(method, path, query, bytes, prefer, contentType);
        String body = response.body() == null ? "" : response.body().trim();
        if (body.isBlank()) {
            return null;
        }
        return JsonParser.parseString(body);
    }

    private void requestBytes(
            String method,
            String path,
            Map<String, String> query,
            byte[] payload,
            String prefer,
            String contentType,
            Map<String, String> extraHeaders
    ) throws Exception {
        HttpRequest.Builder builder = requestBuilder(method, path, query, prefer, contentType, extraHeaders);
        if (payload != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(payload));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        ensureSuccess(response.statusCode(), response.body() == null ? "" : new String(response.body(), StandardCharsets.UTF_8), path);
    }

    private HttpResponse<String> requestText(
            String method,
            String path,
            Map<String, String> query,
            byte[] payload,
            String prefer,
            String contentType
    ) throws Exception {
        HttpRequest.Builder builder = requestBuilder(method, path, query, prefer, contentType, Map.of());
        if (payload != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(payload));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response.statusCode(), response.body(), path);
        return response;
    }

    private HttpRequest.Builder requestBuilder(
            String method,
            String path,
            Map<String, String> query,
            String prefer,
            String contentType,
            Map<String, String> extraHeaders
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(buildUri(path, query))
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30));
        if (prefer != null && !prefer.isBlank()) {
            builder.header("Prefer", prefer);
        }
        if (contentType != null && !contentType.isBlank()) {
            builder.header("Content-Type", contentType);
        }
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        return builder;
    }

    private URI buildUri(String path, Map<String, String> query) {
        StringBuilder out = new StringBuilder();
        out.append(baseUrl).append(path);
        if (query != null && !query.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                out.append(first ? '?' : '&');
                first = false;
                out.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                out.append('=');
                out.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        return URI.create(out.toString());
    }

    private static void ensureSuccess(int code, String body, String path) throws IOException {
        if (code >= 200 && code < 300) {
            return;
        }
        String message = body == null || body.isBlank() ? "" : body;
        throw new IOException("Supabase devolvio HTTP " + code + " en " + path + (message.isBlank() ? "" : ": " + message));
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String preferRepresentationHeader(boolean representation) {
        return representation ? "return=representation" : "return=minimal";
    }

    private static String encodePathSegment(String path) {
        String[] parts = path.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return out.toString();
    }
}
