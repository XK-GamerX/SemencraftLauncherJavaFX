package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * Cuentas del launcher: login y registro con verificacion por Discord. Todo pasa por sc-api
 * (SemencraftApi); el launcher ya no lee ni escribe la tabla de cuentas directamente.
 */
final class SupabaseDatabaseService {

    record RemoteAccount(String minecraftUsername, String discordUsername, String discordUserId) {
    }

    /** {@code code} viene de la API (access_pending, banned...) para que la pantalla de login reaccione. */
    record AuthResult(boolean success, String message, RemoteAccount account, String code) {
    }

    record RegistrationRequest(String minecraftUsername, String password, String discordUsername) {
    }

    record RegistrationStartResult(boolean success, String message, PendingVerification pendingVerification) {
    }

    record PendingVerification(String verificationId, String discordDisplayName, String expiresAt) {
    }

    record VerificationRequest(String verificationId, String verificationCode) {
    }

    /** La cuenta queda creada pero sin acceso hasta que se pague: no inicia sesion. */
    record VerificationResult(boolean success, String message) {
    }

    /** Si sale bien, deja guardado el token de la sesion en SemencraftApi. */
    AuthResult authenticate(String username, String password) {
        if (!isValidMinecraftUsername(normalizeMinecraftUsername(username))) {
            return new AuthResult(false, "Usuario o contrasena incorrectos.", null, "bad_credentials");
        }
        if (password == null || password.isBlank()) {
            return new AuthResult(false, "Introduce tu contrasena.", null, "bad_credentials");
        }
        JsonObject body = new JsonObject();
        body.addProperty("username", username.trim());
        body.addProperty("password", password);
        body.addProperty("client", "launcher");
        body.addProperty("limited", true); // sin acceso se entra igual, solo para cambiar la skin
        SemencraftApi.Result result = SemencraftApi.call("login", body);
        if (!result.ok()) {
            return new AuthResult(false, result.error(), null, result.code());
        }
        SemencraftApi.setSessionToken(SemencraftApi.string(result.body(), "token"));
        SemencraftApi.rememberAccess(result.body());
        JsonObject profile = result.body().has("profile") ? result.body().getAsJsonObject("profile") : new JsonObject();
        return new AuthResult(true, "Bienvenido de nuevo.", new RemoteAccount(
                SemencraftApi.string(profile, "username"),
                SemencraftApi.string(profile, "discord"),
                ""
        ), "");
    }

    RegistrationStartResult startRegistration(RegistrationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!isValidMinecraftUsername(normalizeMinecraftUsername(request.minecraftUsername()))) {
            return new RegistrationStartResult(false, "El nombre de Minecraft debe tener entre 5 y 16 caracteres y solo usar letras, numeros o _.", null);
        }
        JsonObject body = new JsonObject();
        body.addProperty("username", request.minecraftUsername().trim());
        body.addProperty("password", request.password() == null ? "" : request.password());
        body.addProperty("discordUsername", request.discordUsername() == null ? "" : request.discordUsername());
        SemencraftApi.Result result = SemencraftApi.call("discordStart", body);
        if (!result.ok()) {
            return new RegistrationStartResult(false, result.error(), null);
        }
        JsonObject data = result.body();
        return new RegistrationStartResult(true, "Te enviamos un codigo por MD a Discord.", new PendingVerification(
                SemencraftApi.string(data, "verificationId"),
                SemencraftApi.string(data, "discordDisplayName"),
                SemencraftApi.string(data, "expiresAt")
        ));
    }

    VerificationResult finishRegistration(VerificationRequest request) {
        Objects.requireNonNull(request, "request");
        String code = request.verificationCode() == null ? "" : request.verificationCode().trim();
        if (code.isBlank()) {
            return new VerificationResult(false, "Introduce el codigo de verificacion.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("verificationId", request.verificationId() == null ? "" : request.verificationId());
        body.addProperty("code", code);
        SemencraftApi.Result result = SemencraftApi.call("discordFinish", body);
        if (!result.ok()) {
            return new VerificationResult(false, result.error());
        }
        return new VerificationResult(true, SemencraftApi.string(result.body(), "message"));
    }

    private static String normalizeMinecraftUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    static boolean isValidMinecraftUsername(String raw) {
        return raw != null && raw.matches("[A-Za-z0-9_]{5,16}");
    }
}
