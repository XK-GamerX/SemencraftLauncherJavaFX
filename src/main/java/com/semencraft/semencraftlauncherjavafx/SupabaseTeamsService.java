package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SupabaseTeamsService {

    private static final String TEAM_ICON_BUCKET = "team-icons";

    private final SupabaseConfig.RuntimeConfig config = SupabaseConfig.resolve();
    private final SupabaseRestClient supabase = new SupabaseRestClient(
            config.url(),
            config.hasServiceRoleKey() ? config.serviceRoleKey() : config.anonKey()
    );
    private final Map<String, String> iconCache = new ConcurrentHashMap<>();

    record ActionResult(boolean success, String message) {}

    record LoadContextResult(boolean success, String message, TeamContext context) {}

    record NotificationBadgeResult(boolean success, String message, int unreadCount) {}

    record CandidatesResult(boolean success, String message, List<RegisteredPlayer> players) {}

    record CreateTeamRequest(String founderMinecraftUsername, String teamName, String teamColorHex, String teamIconBase64) {}

    record UpdateTeamRequest(String founderMinecraftUsername, String teamId, String teamName, String teamColorHex, String teamIconBase64) {}

    record TeamContext(
            String minecraftUsername,
            String normalizedMinecraftUsername,
            String discordUsername,
            boolean hasTeam,
            boolean founder,
            TeamRecord team,
            List<NotificationItem> notifications,
            List<String> reservedTeamColors
    ) {}

    record TeamRecord(
            String teamId,
            String teamName,
            String teamNameNormalized,
            String representativeColor,
            String teamIconBase64,
            String founderMinecraftUsername,
            boolean archived,
            List<TeamMember> members
    ) {}

    record TeamMember(String minecraftUsername, String normalizedMinecraftUsername, boolean founder, String joinedAt) {}

    record RegisteredPlayer(String minecraftUsername, String normalizedMinecraftUsername, String teamId, String teamName) {}

    /** "online", "idle" (AFK) o "offline". */
    record PresenceResult(boolean success, Map<String, String> statusByNormalizedUsername) {}

    private static final int PRESENCE_ONLINE_WINDOW_SECONDS = 40;

    record NotificationItem(
            String id,
            String type,
            String status,
            boolean read,
            String createdAt,
            String inviterMinecraftUsername,
            String teamId,
            String teamName,
            String teamColorHex,
            String teamIconBase64,
            String message
    ) {}

    boolean hasWriteToken() {
        return supabase.isConfigured();
    }

    LoadContextResult loadContext(String minecraftUsername) {
        String normalizedUser = normalizeMinecraftUsername(minecraftUsername);
        if (!SupabaseDatabaseService.isValidMinecraftUsername(normalizedUser)) {
            return new LoadContextResult(false, "Usuario invalido.", null);
        }
        try {
            return new LoadContextResult(true, "", loadContextOrThrow(normalizedUser));
        } catch (Exception ex) {
            return new LoadContextResult(false, friendlyErrorMessage(ex, "No se pudo cargar el equipo."), null);
        }
    }

    NotificationBadgeResult loadNotificationBadge(String minecraftUsername) {
        String normalizedUser = normalizeMinecraftUsername(minecraftUsername);
        if (!SupabaseDatabaseService.isValidMinecraftUsername(normalizedUser)) {
            return new NotificationBadgeResult(false, "Usuario invalido.", 0);
        }
        try {
            JsonObject user = requireUserByMinecraft(normalizedUser);
            String userId = stringValue(user, "id");
            JsonArray rows = supabase.select("notifications", Map.of("user_id", "eq." + userId), null, 200);
            int unreadCount = 0;
            for (JsonElement element : rows) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject row = element.getAsJsonObject();
                boolean pending = "pending".equalsIgnoreCase(stringValue(row, "status"));
                boolean unread = !booleanValue(row, "read");
                if (pending || unread) {
                    unreadCount++;
                }
            }
            return new NotificationBadgeResult(true, "", unreadCount);
        } catch (Exception ex) {
            return new NotificationBadgeResult(false, friendlyErrorMessage(ex, "No se pudo cargar el contador de notificaciones."), 0);
        }
    }

    /**
     * Envia un "latido" de presencia para el usuario activo. status debe ser
     * uno de: "online", "idle" (AFK) o "playing" (jugando en el server).
     * Es best-effort: si falla (sin conexion, tabla sin columnas de
     * presencia, etc.) no lanza excepcion, ya que no debe interrumpir el uso
     * normal del launcher.
     */
    void sendHeartbeat(String minecraftUsername, String status) {
        String normalized = normalizeMinecraftUsername(minecraftUsername);
        if (normalized.isBlank()) {
            return;
        }
        String normalizedStatus = normalizePresenceStatus(status);
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("last_seen_at", Instant.now().toString());
            payload.addProperty("presence_status", normalizedStatus);
            supabase.update("app_users", payload, Map.of("minecraft_username_normalized", "eq." + normalized));
        } catch (Exception ignored) {
            // Presencia best-effort.
        }
    }

    private static String normalizePresenceStatus(String status) {
        if ("idle".equalsIgnoreCase(status) || "playing".equalsIgnoreCase(status)) {
            return status.toLowerCase(Locale.ROOT);
        }
        return "online";
    }

    /**
     * Consulta el estado de presencia (online/idle/offline) de una lista de
     * jugadores, en base a la ultima vez que enviaron un latido.
     */
    PresenceResult fetchPresence(List<String> minecraftUsernames) {
        List<String> normalized = new ArrayList<>();
        if (minecraftUsernames != null) {
            for (String username : minecraftUsernames) {
                String value = normalizeMinecraftUsername(username);
                if (!value.isBlank() && !normalized.contains(value)) {
                    normalized.add(value);
                }
            }
        }
        if (normalized.isEmpty()) {
            return new PresenceResult(true, Map.of());
        }
        try {
            String inFilter = "in.(" + String.join(",", normalized) + ")";
            JsonArray rows = supabase.select(
                    "app_users",
                    Map.of("minecraft_username_normalized", inFilter),
                    null,
                    normalized.size()
            );
            Map<String, String> statusByUsername = new HashMap<>();
            Instant now = Instant.now();
            for (JsonElement element : rows) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject row = element.getAsJsonObject();
                String username = normalizeMinecraftUsername(stringValue(row, "minecraft_username_normalized"));
                if (username.isBlank()) {
                    continue;
                }
                statusByUsername.put(username, resolvePresenceStatus(row, now));
            }
            for (String username : normalized) {
                statusByUsername.putIfAbsent(username, "offline");
            }
            return new PresenceResult(true, statusByUsername);
        } catch (Exception ex) {
            return new PresenceResult(false, Map.of());
        }
    }

    private static String resolvePresenceStatus(JsonObject row, Instant now) {
        String lastSeenRaw = stringValue(row, "last_seen_at");
        if (lastSeenRaw.isBlank()) {
            return "offline";
        }
        try {
            Instant lastSeen = OffsetDateTime.parse(lastSeenRaw).toInstant();
            long secondsAgo = Duration.between(lastSeen, now).getSeconds();
            if (secondsAgo > PRESENCE_ONLINE_WINDOW_SECONDS) {
                return "offline";
            }
            return normalizePresenceStatus(defaultString(stringValue(row, "presence_status"), "online"));
        } catch (Exception ex) {
            return "offline";
        }
    }

    CandidatesResult listInviteCandidates(String requesterMinecraftUsername) {
        String normalizedUser = normalizeMinecraftUsername(requesterMinecraftUsername);
        try {
            TeamContext context = loadContextOrThrow(normalizedUser);
            if (context == null || !context.hasTeam() || !context.founder() || context.team() == null) {
                return new CandidatesResult(false, "Solo el fundador puede invitar jugadores.", List.of());
            }

            JsonArray rows = supabase.select("app_users", Map.of(), "minecraft_username.asc", 5000);
            List<RegisteredPlayer> players = new ArrayList<>();
            for (JsonElement element : rows) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject user = element.getAsJsonObject();
                String normalizedCandidate = normalizeMinecraftUsername(stringValue(user, "minecraft_username_normalized"));
                if (!SupabaseDatabaseService.isValidMinecraftUsername(normalizedCandidate) || normalizedCandidate.equals(normalizedUser)) {
                    continue;
                }
                if (!stringValue(user, "team_id").isBlank()) {
                    continue;
                }
                players.add(new RegisteredPlayer(
                        defaultString(stringValue(user, "minecraft_username"), normalizedCandidate),
                        normalizedCandidate,
                        "",
                        ""
                ));
            }
            players.sort(Comparator.comparing(player -> player.minecraftUsername().toLowerCase(Locale.ROOT)));
            return new CandidatesResult(true, "", List.copyOf(players));
        } catch (Exception ex) {
            return new CandidatesResult(false, friendlyErrorMessage(ex, "No se pudo cargar la lista de jugadores."), List.of());
        }
    }

    ActionResult createTeam(CreateTeamRequest request) {
        Objects.requireNonNull(request, "request");
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar Supabase en el launcher.");
        }

        String founderNormalized = normalizeMinecraftUsername(request.founderMinecraftUsername());
        String teamName = safeTrim(request.teamName());
        String normalizedTeamName = normalizeTeamName(teamName);
        String colorHex = normalizeHexColor(request.teamColorHex());
        String iconBase64 = safeTrim(request.teamIconBase64());

        if (!SupabaseDatabaseService.isValidMinecraftUsername(founderNormalized)) {
            return new ActionResult(false, "No se pudo identificar al fundador.");
        }
        if (!isValidTeamName(teamName)) {
            return new ActionResult(false, "El nombre del equipo debe tener entre 3 y 24 caracteres y solo usar letras, numeros, espacios, - o _.");
        }
        if (colorHex == null) {
            return new ActionResult(false, "El color representante debe estar en formato HEX.");
        }
        if (iconBase64.isBlank()) {
            return new ActionResult(false, "Sube un icono para tu equipo.");
        }

        try {
            byte[] iconBytes = decodeIcon(iconBase64);
            if (iconBytes.length == 0 || iconBytes.length > 1_500_000) {
                return new ActionResult(false, "El icono del equipo es demasiado pesado o invalido.");
            }

            JsonObject founder = requireUserByMinecraft(founderNormalized);
            if (!stringValue(founder, "team_id").isBlank()) {
                return new ActionResult(false, "Ya estas dentro de un equipo.");
            }

            JsonObject existing = supabase.selectSingle("teams", Map.of(
                    "team_name_normalized", "eq." + normalizedTeamName,
                    "archived", "eq.false"
            ), null);
            if (existing != null) {
                return new ActionResult(false, "Ya existe un equipo con ese nombre.");
            }
            if (isTeamColorInUse(colorHex, null)) {
                return new ActionResult(false, "Ese color ya esta en uso por otro equipo.");
            }

            String teamId = UUID.randomUUID().toString();
            String iconPath = uploadTeamIcon(teamId, iconBytes);

            JsonObject teamPayload = new JsonObject();
            teamPayload.addProperty("id", teamId);
            teamPayload.addProperty("team_name", teamName);
            teamPayload.addProperty("team_name_normalized", normalizedTeamName);
            teamPayload.addProperty("founder_user_id", stringValue(founder, "id"));
            teamPayload.addProperty("representative_color", colorHex);
            teamPayload.addProperty("icon_path", iconPath);
            teamPayload.addProperty("archived", false);
            supabase.insert("teams", teamPayload);

            JsonObject memberPayload = new JsonObject();
            memberPayload.addProperty("team_id", teamId);
            memberPayload.addProperty("user_id", stringValue(founder, "id"));
            memberPayload.addProperty("member_role", "founder");
            memberPayload.addProperty("joined_at", Instant.now().toString());
            supabase.insert("team_members", memberPayload);

            JsonObject userUpdate = new JsonObject();
            userUpdate.addProperty("team_id", teamId);
            supabase.update("app_users", userUpdate, Map.of("id", "eq." + stringValue(founder, "id")));
            return new ActionResult(true, "Equipo creado correctamente.");
        } catch (IllegalArgumentException ex) {
            return new ActionResult(false, "No se pudo leer el icono del equipo.");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo crear el equipo."));
        }
    }

    ActionResult updateTeam(UpdateTeamRequest request) {
        Objects.requireNonNull(request, "request");
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar Supabase en el launcher.");
        }
 
        String founderNormalized = normalizeMinecraftUsername(request.founderMinecraftUsername());
        String teamId = safeTrim(request.teamId());
        String teamName = safeTrim(request.teamName());
        String normalizedTeamName = normalizeTeamName(teamName);
        String colorHex = normalizeHexColor(request.teamColorHex());
        String iconBase64 = safeTrim(request.teamIconBase64());

        if (!SupabaseDatabaseService.isValidMinecraftUsername(founderNormalized)) {
            return new ActionResult(false, "No se pudo identificar al fundador.");
        }
        if (teamId.isBlank()) {
            return new ActionResult(false, "No se encontro el equipo.");
        }
        if (!isValidTeamName(teamName)) {
            return new ActionResult(false, "El nombre del equipo debe tener entre 3 y 24 caracteres y solo usar letras, numeros, espacios, - o _.");
        }
        if (colorHex == null) {
            return new ActionResult(false, "El color representante debe estar en formato HEX.");
        }
        if (iconBase64.isBlank()) {
            return new ActionResult(false, "Sube un icono para tu equipo.");
        }

        try {
            byte[] iconBytes = decodeIcon(iconBase64);
            if (iconBytes.length == 0 || iconBytes.length > 1_500_000) {
                return new ActionResult(false, "El icono del equipo es demasiado pesado o invalido.");
            }

            TeamContext founderContext = loadContextOrThrow(founderNormalized);
            if (founderContext == null || !founderContext.hasTeam() || !founderContext.founder() || founderContext.team() == null) {
                return new ActionResult(false, "Solo el fundador puede editar el equipo.");
            }
            if (!teamId.equals(founderContext.team().teamId())) {
                return new ActionResult(false, "Solo puedes editar tu equipo actual.");
            }

            JsonObject existing = supabase.selectSingle("teams", Map.of(
                    "team_name_normalized", "eq." + normalizedTeamName,
                    "archived", "eq.false"
            ), null);
            if (existing != null && !teamId.equals(stringValue(existing, "id"))) {
                return new ActionResult(false, "Ya existe un equipo con ese nombre.");
            }
            if (isTeamColorInUse(colorHex, teamId)) {
                return new ActionResult(false, "Ese color ya esta en uso por otro equipo.");
            }

            JsonObject update = new JsonObject();
            update.addProperty("team_name", teamName);
            update.addProperty("team_name_normalized", normalizedTeamName);
            update.addProperty("representative_color", colorHex);
            update.addProperty("icon_path", uploadTeamIcon(teamId, iconBytes));
            supabase.update("teams", update, Map.of("id", "eq." + teamId));

            notifyTeamMembers(teamId, stringValue(requireUserByMinecraft(founderNormalized), "id"), "Equipo actualizado",
                    "El equipo '" + teamName + "' fue actualizado.", teamId);
            return new ActionResult(true, "Equipo actualizado correctamente.");
        } catch (IllegalArgumentException ex) {
            return new ActionResult(false, "No se pudo leer el icono del equipo.");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo actualizar el equipo."));
        }
    }

    ActionResult invitePlayer(String founderMinecraftUsername, String targetMinecraftUsername) {
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar Supabase en el launcher.");
        }

        String founderNormalized = normalizeMinecraftUsername(founderMinecraftUsername);
        String targetNormalized = normalizeMinecraftUsername(targetMinecraftUsername);
        if (!SupabaseDatabaseService.isValidMinecraftUsername(founderNormalized)
                || !SupabaseDatabaseService.isValidMinecraftUsername(targetNormalized)) {
            return new ActionResult(false, "Usuario invalido.");
        }
        if (founderNormalized.equals(targetNormalized)) {
            return new ActionResult(false, "No puedes invitarte a ti mismo.");
        }

        try {
            TeamContext founderContext = loadContextOrThrow(founderNormalized);
            if (founderContext == null || !founderContext.hasTeam() || !founderContext.founder() || founderContext.team() == null) {
                return new ActionResult(false, "Solo el fundador puede invitar jugadores.");
            }

            JsonObject founderUser = requireUserByMinecraft(founderNormalized);
            JsonObject targetUser = requireUserByMinecraft(targetNormalized);
            if (!stringValue(targetUser, "team_id").isBlank()) {
                return new ActionResult(false, "Ese jugador ya pertenece a otro equipo.");
            }

            JsonObject pendingInvite = supabase.selectSingle("team_invites", Map.of(
                    "team_id", "eq." + founderContext.team().teamId(),
                    "invited_user_id", "eq." + stringValue(targetUser, "id"),
                    "status", "eq.pending"
            ), null);
            if (pendingInvite != null) {
                return new ActionResult(false, "Ese jugador ya tiene una invitacion pendiente para este equipo.");
            }

            JsonObject invitePayload = new JsonObject();
            invitePayload.addProperty("team_id", founderContext.team().teamId());
            invitePayload.addProperty("invited_user_id", stringValue(targetUser, "id"));
            invitePayload.addProperty("invited_by_user_id", stringValue(founderUser, "id"));
            invitePayload.addProperty("status", "pending");
            JsonObject invite = supabase.insert("team_invites", invitePayload);
            if (invite == null) {
                return new ActionResult(false, "No se pudo crear la invitacion.");
            }

            JsonObject notificationPayload = new JsonObject();
            notificationPayload.addProperty("user_id", stringValue(targetUser, "id"));
            notificationPayload.addProperty("type", "team_invite");
            notificationPayload.addProperty("title", "Invitacion de equipo");
            notificationPayload.addProperty("message", "Has sido invitado al equipo '" + founderContext.team().teamName() + "'.");
            notificationPayload.addProperty("team_id", founderContext.team().teamId());
            notificationPayload.addProperty("invite_id", stringValue(invite, "id"));
            notificationPayload.addProperty("status", "pending");
            notificationPayload.addProperty("read", false);
            supabase.insert("notifications", notificationPayload);
            return new ActionResult(true, "Invitacion enviada.");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo enviar la invitacion."));
        }
    }

    ActionResult respondToInvitation(String minecraftUsername, String notificationId, boolean accept) {
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar Supabase en el launcher.");
        }

        String normalizedUser = normalizeMinecraftUsername(minecraftUsername);
        if (!SupabaseDatabaseService.isValidMinecraftUsername(normalizedUser) || safeTrim(notificationId).isBlank()) {
            return new ActionResult(false, "Notificacion invalida.");
        }

        try {
            JsonObject user = requireUserByMinecraft(normalizedUser);
            String userId = stringValue(user, "id");

            JsonObject notification = supabase.selectSingle("notifications", Map.of(
                    "id", "eq." + safeTrim(notificationId),
                    "user_id", "eq." + userId
            ), null);
            if (notification == null || !"team_invite".equalsIgnoreCase(stringValue(notification, "type"))) {
                return new ActionResult(false, "No se encontro la invitacion.");
            }
            if (!"pending".equalsIgnoreCase(stringValue(notification, "status"))) {
                return new ActionResult(false, "Esa invitacion ya fue respondida.");
            }

            String inviteId = stringValue(notification, "invite_id");
            JsonObject invite = inviteId.isBlank() ? null : supabase.selectSingle("team_invites", Map.of("id", "eq." + inviteId), null);
            if (invite == null || !"pending".equalsIgnoreCase(stringValue(invite, "status"))) {
                updateNotificationStatus(stringValue(notification, "id"), "expired", true);
                return new ActionResult(false, "Esa invitacion ya no esta disponible.");
            }

            String teamId = stringValue(notification, "team_id");
            JsonObject team = safeTrim(teamId).isBlank() ? null : supabase.selectSingle("teams", Map.of("id", "eq." + teamId), null);
            if (team == null || booleanValue(team, "archived")) {
                patchInviteStatus(inviteId, "expired");
                updateNotificationStatus(stringValue(notification, "id"), "expired", true);
                return new ActionResult(false, "Ese equipo ya no esta disponible.");
            }

            if (!accept) {
                patchInviteStatus(inviteId, "rejected");
                updateNotificationStatus(stringValue(notification, "id"), "rejected", true);
                return new ActionResult(true, "Invitacion rechazada.");
            }

            if (!stringValue(user, "team_id").isBlank()) {
                return new ActionResult(false, "Ya perteneces a un equipo. Solo puedes estar en uno a la vez.");
            }

            JsonObject existingMembership = supabase.selectSingle("team_members", Map.of(
                    "team_id", "eq." + teamId,
                    "user_id", "eq." + userId
            ), null);
            if (existingMembership == null) {
                JsonObject memberPayload = new JsonObject();
                memberPayload.addProperty("team_id", teamId);
                memberPayload.addProperty("user_id", userId);
                memberPayload.addProperty("member_role", "member");
                memberPayload.addProperty("joined_at", Instant.now().toString());
                supabase.insert("team_members", memberPayload);
            }

            JsonObject userUpdate = new JsonObject();
            userUpdate.addProperty("team_id", teamId);
            supabase.update("app_users", userUpdate, Map.of("id", "eq." + userId));

            patchInviteStatus(inviteId, "accepted");
            updateNotificationStatus(stringValue(notification, "id"), "accepted", true);
            expireOtherInvites(userId, inviteId);

            String founderUserId = stringValue(team, "founder_user_id");
            if (!founderUserId.isBlank()) {
                createInfoNotification(
                        founderUserId,
                        "Miembro nuevo",
                        defaultString(stringValue(user, "minecraft_username"), normalizedUser)
                                + " se unio al equipo '" + stringValue(team, "team_name") + "'.",
                        teamId
                );
            }
            return new ActionResult(true, "Te uniste al equipo correctamente.");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo responder la invitacion."));
        }
    }

    ActionResult removeMember(String founderMinecraftUsername, String targetMinecraftUsername) {
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar Supabase en el launcher.");
        }

        String founderNormalized = normalizeMinecraftUsername(founderMinecraftUsername);
        String targetNormalized = normalizeMinecraftUsername(targetMinecraftUsername);
        if (!SupabaseDatabaseService.isValidMinecraftUsername(founderNormalized)
                || !SupabaseDatabaseService.isValidMinecraftUsername(targetNormalized)) {
            return new ActionResult(false, "Usuario invalido.");
        }
        if (founderNormalized.equals(targetNormalized)) {
            return new ActionResult(false, "Usa 'Abandonar equipo' para salir de tu propio equipo.");
        }

        try {
            TeamContext founderContext = loadContextOrThrow(founderNormalized);
            if (founderContext == null || !founderContext.hasTeam() || founderContext.team() == null || !founderContext.founder()) {
                return new ActionResult(false, "Solo el fundador puede expulsar miembros.");
            }

            JsonObject targetUser = requireUserByMinecraft(targetNormalized);
            if (!founderContext.team().teamId().equals(stringValue(targetUser, "team_id"))) {
                return new ActionResult(false, "Ese jugador no pertenece a tu equipo.");
            }

            supabase.delete("team_members", Map.of(
                    "team_id", "eq." + founderContext.team().teamId(),
                    "user_id", "eq." + stringValue(targetUser, "id")
            ));

            JsonObject clearUser = new JsonObject();
            clearUser.add("team_id", JsonNull.INSTANCE);
            supabase.update("app_users", clearUser, Map.of("id", "eq." + stringValue(targetUser, "id")));

            createInfoNotification(
                    stringValue(targetUser, "id"),
                    "Expulsion",
                    "Fuiste expulsado del equipo '" + founderContext.team().teamName() + "'.",
                    founderContext.team().teamId()
            );
            return new ActionResult(true, "Miembro expulsado.");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo expulsar al miembro."));
        }
    }

    ActionResult leaveTeam(String minecraftUsername) {
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar Supabase en el launcher.");
        }

        String normalizedUser = normalizeMinecraftUsername(minecraftUsername);
        if (!SupabaseDatabaseService.isValidMinecraftUsername(normalizedUser)) {
            return new ActionResult(false, "Usuario invalido.");
        }

        try {
            TeamContext context = loadContextOrThrow(normalizedUser);
            if (context == null || !context.hasTeam() || context.team() == null) {
                return new ActionResult(false, "No perteneces a ningun equipo.");
            }

            JsonObject user = requireUserByMinecraft(normalizedUser);
            String userId = stringValue(user, "id");
            String teamId = context.team().teamId();
            JsonObject team = supabase.selectSingle("teams", Map.of("id", "eq." + teamId), null);

            JsonObject clearUser = new JsonObject();
            clearUser.add("team_id", JsonNull.INSTANCE);
            supabase.update("app_users", clearUser, Map.of("id", "eq." + userId));
            supabase.delete("team_members", Map.of(
                    "team_id", "eq." + teamId,
                    "user_id", "eq." + userId
            ));

            if (team == null) {
                return new ActionResult(true, "Tu equipo ya no existia. Tu cuenta fue actualizada.");
            }

            if (context.founder()) {
                JsonArray remainingRows = supabase.select("team_members", Map.of("team_id", "eq." + teamId), "joined_at.asc", 128);
                if (remainingRows.isEmpty()) {
                    JsonObject teamUpdate = new JsonObject();
                    teamUpdate.addProperty("archived", true);
                    supabase.update("teams", teamUpdate, Map.of("id", "eq." + teamId));
                    return new ActionResult(true, "Abandonaste el equipo. Como eras el ultimo miembro, el equipo fue archivado.");
                }

                JsonObject newFounderRow = remainingRows.get(0).getAsJsonObject();
                String successorUserId = stringValue(newFounderRow, "user_id");
                JsonObject memberUpdate = new JsonObject();
                memberUpdate.addProperty("member_role", "founder");
                supabase.update("team_members", memberUpdate, Map.of(
                        "team_id", "eq." + teamId,
                        "user_id", "eq." + successorUserId
                ));

                JsonObject teamUpdate = new JsonObject();
                teamUpdate.addProperty("founder_user_id", successorUserId);
                supabase.update("teams", teamUpdate, Map.of("id", "eq." + teamId));

                createInfoNotification(
                        successorUserId,
                        "Nuevo fundador",
                        "Ahora eres el fundador del equipo '" + stringValue(team, "team_name") + "'.",
                        teamId
                );
                return new ActionResult(true, "Abandonaste tu equipo y el liderazgo fue transferido.");
            }

            String founderUserId = stringValue(team, "founder_user_id");
            if (!founderUserId.isBlank()) {
                createInfoNotification(
                        founderUserId,
                        "Miembro salio",
                        defaultString(stringValue(user, "minecraft_username"), normalizedUser)
                                + " abandono el equipo '" + stringValue(team, "team_name") + "'.",
                        teamId
                );
            }
            return new ActionResult(true, "Abandonaste tu equipo.");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo abandonar el equipo."));
        }
    }

    ActionResult markNotificationsViewed(String minecraftUsername) {
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar Supabase en el launcher.");
        }

        String normalizedUser = normalizeMinecraftUsername(minecraftUsername);
        if (!SupabaseDatabaseService.isValidMinecraftUsername(normalizedUser)) {
            return new ActionResult(false, "Usuario invalido.");
        }

        try {
            JsonObject user = requireUserByMinecraft(normalizedUser);
            JsonObject update = new JsonObject();
            update.addProperty("read", true);
            supabase.update("notifications", update, Map.of(
                    "user_id", "eq." + stringValue(user, "id"),
                    "read", "eq.false"
            ));
            return new ActionResult(true, "");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudieron actualizar las notificaciones."));
        }
    }

    private TeamContext loadContextOrThrow(String normalizedUser) throws Exception {
        JsonObject user = requireUserByMinecraft(normalizedUser);
        String userId = stringValue(user, "id");
        Map<String, JsonObject> usersById = loadAllUsersById();
        Map<String, JsonObject> teamsById = loadAllTeamsById();
        List<NotificationItem> notifications = loadNotifications(userId, usersById, teamsById);
        List<String> reservedTeamColors = collectReservedTeamColors(teamsById);

        String teamId = stringValue(user, "team_id");
        if (teamId.isBlank()) {
            return new TeamContext(
                    defaultString(stringValue(user, "minecraft_username"), normalizedUser),
                    normalizedUser,
                    stringValue(user, "discord_username"),
                    false,
                    false,
                    null,
                    notifications,
                    reservedTeamColors
            );
        }

        JsonObject teamRoot = teamsById.get(teamId);
        if (teamRoot == null || booleanValue(teamRoot, "archived")) {
            clearUserTeamMembership(userId);
            return new TeamContext(
                    defaultString(stringValue(user, "minecraft_username"), normalizedUser),
                    normalizedUser,
                    stringValue(user, "discord_username"),
                    false,
                    false,
                    null,
                    notifications,
                    reservedTeamColors
            );
        }

        TeamRecord team = buildTeamRecord(teamRoot, usersById);
        boolean memberOfTeam = team.members().stream()
                .anyMatch(member -> normalizedUser.equals(member.normalizedMinecraftUsername()));
        if (!memberOfTeam) {
            clearUserTeamMembership(userId);
            return new TeamContext(
                    defaultString(stringValue(user, "minecraft_username"), normalizedUser),
                    normalizedUser,
                    stringValue(user, "discord_username"),
                    false,
                    false,
                    null,
                    notifications,
                    reservedTeamColors
            );
        }

        boolean founder = userId.equals(stringValue(teamRoot, "founder_user_id"));
        return new TeamContext(
                defaultString(stringValue(user, "minecraft_username"), normalizedUser),
                normalizedUser,
                stringValue(user, "discord_username"),
                true,
                founder,
                team,
                notifications,
                reservedTeamColors
        );
    }

    private List<String> collectReservedTeamColors(Map<String, JsonObject> teamsById) {
        LinkedHashSet<String> colors = new LinkedHashSet<>();
        for (JsonObject team : teamsById.values()) {
            if (team == null || booleanValue(team, "archived")) {
                continue;
            }
            String color = normalizeHexColor(stringValue(team, "representative_color"));
            if (color != null) {
                colors.add(color);
            }
        }
        return List.copyOf(colors);
    }

    private TeamRecord buildTeamRecord(JsonObject teamRoot, Map<String, JsonObject> usersById) throws Exception {
        String teamId = stringValue(teamRoot, "id");
        JsonArray memberRows = supabase.select("team_members", Map.of("team_id", "eq." + teamId), "joined_at.asc", 128);
        List<TeamMember> members = new ArrayList<>();
        for (JsonElement element : memberRows) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject row = element.getAsJsonObject();
            JsonObject user = usersById.get(stringValue(row, "user_id"));
            String normalized = normalizeMinecraftUsername(user == null ? "" : stringValue(user, "minecraft_username_normalized"));
            String display = user == null
                    ? defaultString(normalized, "Jugador")
                    : defaultString(stringValue(user, "minecraft_username"), normalized);
            if (!SupabaseDatabaseService.isValidMinecraftUsername(normalized)) {
                normalized = normalizeMinecraftUsername(display);
            }
            if (!SupabaseDatabaseService.isValidMinecraftUsername(normalized)) {
                continue;
            }
            members.add(new TeamMember(
                    display,
                    normalized,
                    "founder".equalsIgnoreCase(stringValue(row, "member_role")),
                    stringValue(row, "joined_at")
            ));
        }

        JsonObject founderUser = usersById.get(stringValue(teamRoot, "founder_user_id"));
        String founderMinecraftUsername = founderUser == null
                ? (members.isEmpty() ? "" : members.get(0).minecraftUsername())
                : defaultString(stringValue(founderUser, "minecraft_username"), "");

        return new TeamRecord(
                teamId,
                defaultString(stringValue(teamRoot, "team_name"), "Equipo"),
                defaultString(stringValue(teamRoot, "team_name_normalized"), normalizeTeamName(stringValue(teamRoot, "team_name"))),
                defaultString(normalizeHexColor(stringValue(teamRoot, "representative_color")), "#9EC0FF"),
                loadTeamIconBase64(teamRoot),
                founderMinecraftUsername,
                booleanValue(teamRoot, "archived"),
                List.copyOf(members)
        );
    }

    private List<NotificationItem> loadNotifications(
            String userId,
            Map<String, JsonObject> usersById,
            Map<String, JsonObject> teamsById
    ) throws Exception {
        JsonArray rows = supabase.select("notifications", Map.of("user_id", "eq." + userId), "created_at.desc", 200);
        List<NotificationItem> notifications = new ArrayList<>();
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject row = element.getAsJsonObject();
            String teamId = stringValue(row, "team_id");
            JsonObject team = teamId.isBlank() ? null : teamsById.get(teamId);
            String inviteId = stringValue(row, "invite_id");
            String inviterUsername = "";
            if (!inviteId.isBlank()) {
                JsonObject invite = supabase.selectSingle("team_invites", Map.of("id", "eq." + inviteId), null);
                if (invite != null) {
                    JsonObject inviter = usersById.get(stringValue(invite, "invited_by_user_id"));
                    if (inviter != null) {
                        inviterUsername = defaultString(stringValue(inviter, "minecraft_username"), "");
                    }
                }
            }

            notifications.add(new NotificationItem(
                    stringValue(row, "id"),
                    defaultString(stringValue(row, "type"), "info"),
                    defaultString(stringValue(row, "status"), "pending"),
                    booleanValue(row, "read"),
                    stringValue(row, "created_at"),
                    inviterUsername,
                    teamId,
                    team == null ? "" : defaultString(stringValue(team, "team_name"), ""),
                    team == null ? "#9EC0FF" : defaultString(normalizeHexColor(stringValue(team, "representative_color")), "#9EC0FF"),
                    team == null ? "" : defaultString(loadTeamIconBase64(team), ""),
                    defaultString(stringValue(row, "message"), "")
            ));
        }
        return List.copyOf(notifications);
    }

    private Map<String, JsonObject> loadAllUsersById() throws Exception {
        JsonArray rows = supabase.select("app_users", Map.of(), "minecraft_username.asc", 5000);
        Map<String, JsonObject> usersById = new HashMap<>();
        for (JsonElement element : rows) {
            if (element.isJsonObject()) {
                JsonObject user = element.getAsJsonObject();
                usersById.put(stringValue(user, "id"), user);
            }
        }
        return usersById;
    }

    private Map<String, JsonObject> loadAllTeamsById() throws Exception {
        JsonArray rows = supabase.select("teams", Map.of(), "created_at.asc", 5000);
        Map<String, JsonObject> teamsById = new HashMap<>();
        for (JsonElement element : rows) {
            if (element.isJsonObject()) {
                JsonObject team = element.getAsJsonObject();
                teamsById.put(stringValue(team, "id"), team);
            }
        }
        return teamsById;
    }

    private JsonObject requireUserByMinecraft(String normalizedMinecraftUsername) throws Exception {
        JsonObject user = supabase.selectSingle("app_users", Map.of(
                "minecraft_username_normalized", "eq." + normalizedMinecraftUsername
        ), null);
        if (user == null) {
            throw new IllegalStateException("No se encontro la cuenta de " + normalizedMinecraftUsername + ".");
        }
        return user;
    }

    private boolean isTeamColorInUse(String colorHex, String excludedTeamId) throws Exception {
        JsonArray rows = supabase.select("teams", Map.of(
                "representative_color", "eq." + colorHex,
                "archived", "eq.false"
        ), "created_at.asc", 32);
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject team = element.getAsJsonObject();
            if (excludedTeamId == null || excludedTeamId.isBlank() || !excludedTeamId.equals(stringValue(team, "id"))) {
                return true;
            }
        }
        return false;
    }

    private void clearUserTeamMembership(String userId) throws Exception {
        if (userId == null || userId.isBlank()) {
            return;
        }
        JsonObject update = new JsonObject();
        update.add("team_id", JsonNull.INSTANCE);
        supabase.update("app_users", update, Map.of("id", "eq." + userId));
    }

    private void expireOtherInvites(String userId, String acceptedInviteId) throws Exception {
        JsonObject inviteUpdate = new JsonObject();
        inviteUpdate.addProperty("status", "expired");
        inviteUpdate.addProperty("responded_at", Instant.now().toString());
        supabase.update("team_invites", inviteUpdate, Map.of(
                "invited_user_id", "eq." + userId,
                "status", "eq.pending",
                "id", "neq." + acceptedInviteId
        ));

        JsonObject notificationUpdate = new JsonObject();
        notificationUpdate.addProperty("status", "expired");
        notificationUpdate.addProperty("read", true);
        supabase.update("notifications", notificationUpdate, Map.of(
                "user_id", "eq." + userId,
                "type", "eq.team_invite",
                "status", "eq.pending",
                "invite_id", "neq." + acceptedInviteId
        ));
    }

    private void patchInviteStatus(String inviteId, String status) throws Exception {
        if (inviteId == null || inviteId.isBlank()) {
            return;
        }
        JsonObject inviteUpdate = new JsonObject();
        inviteUpdate.addProperty("status", status);
        inviteUpdate.addProperty("responded_at", Instant.now().toString());
        supabase.update("team_invites", inviteUpdate, Map.of("id", "eq." + inviteId));
    }

    private void updateNotificationStatus(String notificationId, String status, boolean read) throws Exception {
        JsonObject notificationUpdate = new JsonObject();
        notificationUpdate.addProperty("status", status);
        notificationUpdate.addProperty("read", read);
        supabase.update("notifications", notificationUpdate, Map.of("id", "eq." + notificationId));
    }

    private void notifyTeamMembers(String teamId, String excludedUserId, String title, String message, String relatedTeamId) throws Exception {
        JsonArray memberRows = supabase.select("team_members", Map.of("team_id", "eq." + teamId), null, 128);
        for (JsonElement element : memberRows) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject member = element.getAsJsonObject();
            String userId = stringValue(member, "user_id");
            if (userId.isBlank() || userId.equals(excludedUserId)) {
                continue;
            }
            createInfoNotification(userId, title, message, relatedTeamId);
        }
    }

    private void createInfoNotification(String userId, String title, String message, String teamId) throws Exception {
        if (userId == null || userId.isBlank()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("user_id", userId);
        payload.addProperty("type", "info");
        payload.addProperty("title", defaultString(title, "Actualizacion"));
        payload.addProperty("message", defaultString(message, ""));
        if (teamId == null || teamId.isBlank()) {
            payload.add("team_id", JsonNull.INSTANCE);
        } else {
            payload.addProperty("team_id", teamId);
        }
        payload.add("invite_id", JsonNull.INSTANCE);
        payload.addProperty("status", "info");
        payload.addProperty("read", false);
        supabase.insert("notifications", payload);
    }

    private String uploadTeamIcon(String teamId, byte[] iconBytes) throws Exception {
        String objectPath = teamId + "/icon.png";
        supabase.uploadPublicObject(TEAM_ICON_BUCKET, objectPath, iconBytes, "image/png");
        iconCache.entrySet().removeIf(entry -> entry.getKey().startsWith(objectPath + "|"));
        return objectPath;
    }

    private String loadTeamIconBase64(JsonObject teamRoot) throws Exception {
        String iconPath = stringValue(teamRoot, "icon_path");
        if (iconPath.isBlank()) {
            return "";
        }
        String cacheKey = iconPath + "|" + stringValue(teamRoot, "updated_at");
        String cached = iconCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        byte[] bytes = supabase.downloadPublicObject(TEAM_ICON_BUCKET, iconPath);
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String encoded = Base64.getEncoder().encodeToString(bytes);
        iconCache.put(cacheKey, encoded);
        return encoded;
    }

    private static byte[] decodeIcon(String iconBase64) {
        return Base64.getDecoder().decode(iconBase64);
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeMinecraftUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeTeamName(String raw) {
        String trimmed = safeTrim(raw).replaceAll("\\s+", " ");
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static boolean isValidTeamName(String raw) {
        return raw != null && raw.matches("[A-Za-z0-9 _-]{3,24}");
    }

    private static String normalizeHexColor(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toUpperCase(Locale.ROOT);
        if (!text.startsWith("#")) {
            text = "#" + text;
        }
        return text.matches("^#[0-9A-F]{6}$") ? text : null;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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

    private static boolean booleanValue(JsonObject root, String key) {
        if (root == null || key == null || !root.has(key) || root.get(key).isJsonNull()) {
            return false;
        }
        try {
            return root.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String friendlyErrorMessage(Exception ex, String fallback) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return fallback;
        }
        return ex.getMessage();
    }
}
