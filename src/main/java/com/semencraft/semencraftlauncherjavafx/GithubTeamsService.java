package com.semencraft.semencraftlauncherjavafx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class GithubTeamsService {

    private static final String GITHUB_OWNER = "XK-GamerX";
    private static final String GITHUB_REPO = "SemencraftDatabase";
    private static final String GITHUB_BRANCH = "main";
    private static final String GITHUB_API_BASE = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/contents/";
    private static final String USERS_DIR = "users";
    private static final String TEAMS_DIR = "teams";
    private static final String NOTIFICATIONS_DIR = "notifications";
    private static final String PRIVATE_RESOURCE_PATH = "private/database-private.json";
    private static final String LOCAL_PRIVATE_FILE = "database-private.json";
    private static final String TOKEN_ENV_VAR = "SEMENCRAFT_GITHUB_TOKEN";
    private static final String USER_AGENT = "SemencraftLauncher/1.0";
    private static final SecureRandom RNG = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String githubToken = resolveGithubToken();

    record ActionResult(boolean success, String message) {
    }

    record LoadContextResult(boolean success, String message, TeamContext context) {
    }

    record CandidatesResult(boolean success, String message, List<RegisteredPlayer> players) {
    }

    record CreateTeamRequest(String founderMinecraftUsername, String teamName, String teamColorHex, String teamIconBase64) {
    }

    record UpdateTeamRequest(String founderMinecraftUsername, String teamId, String teamName, String teamColorHex, String teamIconBase64) {
    }

    record TeamContext(
            String minecraftUsername,
            String normalizedMinecraftUsername,
            String discordUsername,
            boolean hasTeam,
            boolean founder,
            TeamRecord team,
            List<NotificationItem> notifications
    ) {
    }

    record TeamRecord(
            String teamId,
            String teamName,
            String teamNameNormalized,
            String representativeColor,
            String teamIconBase64,
            String founderMinecraftUsername,
            boolean archived,
            List<TeamMember> members
    ) {
    }

    record TeamMember(
            String minecraftUsername,
            String normalizedMinecraftUsername,
            boolean founder,
            String joinedAt
    ) {
    }

    record RegisteredPlayer(
            String minecraftUsername,
            String normalizedMinecraftUsername,
            String teamId,
            String teamName
    ) {
    }

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
            String message
    ) {
    }

    boolean hasWriteToken() {
        return githubToken != null && !githubToken.isBlank();
    }

    LoadContextResult loadContext(String minecraftUsername) {
        String normalizedUser = normalizeMinecraftUsername(minecraftUsername);
        if (!GithubDatabaseService.isValidMinecraftUsername(normalizedUser)) {
            return new LoadContextResult(false, "Usuario invalido.", null);
        }
        try {
            TeamContext context = loadContextOrThrow(normalizedUser);
            return new LoadContextResult(true, "", context);
        } catch (Exception ex) {
            return new LoadContextResult(false, friendlyErrorMessage(ex, "No se pudo cargar el equipo."), null);
        }
    }

    CandidatesResult listInviteCandidates(String requesterMinecraftUsername) {
        String normalizedUser = normalizeMinecraftUsername(requesterMinecraftUsername);
        try {
            TeamContext context = loadContextOrThrow(normalizedUser);
            if (context == null || !context.hasTeam() || !context.founder() || context.team() == null) {
                return new CandidatesResult(false, "Solo el fundador puede invitar jugadores.", List.of());
            }

            List<RegisteredPlayer> players = new ArrayList<>();
            for (RepoEntry entry : listDirectory(USERS_DIR)) {
                if (!entry.path().endsWith("_credentials.json")) {
                    continue;
                }
                RepoFile repoFile = readFile(entry.path());
                if (repoFile == null) {
                    continue;
                }
                JsonObject root = parseObject(repoFile.content());
                String candidateUsername = stringValue(root, "minecraftUsername");
                String candidateNormalized = normalizeMinecraftUsername(candidateUsername);
                if (!GithubDatabaseService.isValidMinecraftUsername(candidateNormalized)) {
                    continue;
                }
                if (candidateNormalized.equals(normalizedUser)) {
                    continue;
                }

                TeamMembership membership = parseTeamMembership(root);
                if (membership != null && membership.teamId() != null && !membership.teamId().isBlank()) {
                    continue;
                }

                players.add(new RegisteredPlayer(
                        candidateUsername,
                        candidateNormalized,
                        membership == null ? "" : membership.teamId(),
                        membership == null ? "" : membership.teamName()
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
            return new ActionResult(false, "Falta configurar el token de GitHub.");
        }

        String founderNormalized = normalizeMinecraftUsername(request.founderMinecraftUsername());
        String teamName = request.teamName() == null ? "" : request.teamName().trim();
        String normalizedTeamName = normalizeTeamName(teamName);
        String colorHex = normalizeHexColor(request.teamColorHex());
        String iconBase64 = request.teamIconBase64() == null ? "" : request.teamIconBase64().trim();

        if (!GithubDatabaseService.isValidMinecraftUsername(founderNormalized)) {
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
            byte[] iconBytes = Base64.getDecoder().decode(iconBase64);
            if (iconBytes.length == 0 || iconBytes.length > 1_500_000) {
                return new ActionResult(false, "El icono del equipo es demasiado pesado o invalido.");
            }

            RepoFile founderFile = requireUserFile(founderNormalized);
            JsonObject founderRoot = parseObject(founderFile.content());
            TeamMembership founderMembership = parseTeamMembership(founderRoot);
            if (founderMembership != null && founderMembership.teamId() != null && !founderMembership.teamId().isBlank()) {
                return new ActionResult(false, "Ya estas dentro de un equipo.");
            }

            for (RepoEntry entry : listDirectory(TEAMS_DIR)) {
                if (!entry.path().endsWith(".json") || entry.path().endsWith("_placeholder.json")) {
                    continue;
                }
                RepoFile teamFile = readFile(entry.path());
                if (teamFile == null) {
                    continue;
                }
                TeamRecord existingTeam = parseTeamRecord(parseObject(teamFile.content()));
                if (existingTeam == null || existingTeam.archived()) {
                    continue;
                }
                if (normalizedTeamName.equals(existingTeam.teamNameNormalized())) {
                    return new ActionResult(false, "Ya existe un equipo con ese nombre.");
                }
            }

            String founderDisplay = fallbackDisplayName(founderRoot, founderNormalized);
            String now = Instant.now().toString();
            String teamId = generateTeamId(normalizedTeamName);
            JsonObject teamRoot = new JsonObject();
            teamRoot.addProperty("schemaVersion", 1);
            teamRoot.addProperty("teamId", teamId);
            teamRoot.addProperty("teamName", teamName);
            teamRoot.addProperty("teamNameNormalized", normalizedTeamName);
            teamRoot.addProperty("representativeColor", colorHex);
            teamRoot.addProperty("teamIconBase64", iconBase64);
            teamRoot.addProperty("founderMinecraftUsername", founderDisplay);
            teamRoot.addProperty("createdAt", now);
            teamRoot.addProperty("status", "active");

            JsonArray members = new JsonArray();
            JsonObject founderMember = new JsonObject();
            founderMember.addProperty("minecraftUsername", founderDisplay);
            founderMember.addProperty("minecraftUsernameNormalized", founderNormalized);
            founderMember.addProperty("role", "founder");
            founderMember.addProperty("joinedAt", now);
            members.add(founderMember);
            teamRoot.add("members", members);

            setTeamMembership(founderRoot, new TeamMembership(teamId, teamName, "founder", now));

            writeJson(teamPath(teamId), teamRoot, "Crear equipo " + teamName, null);
            writeJson(credentialsPath(founderNormalized), founderRoot, "Asignar equipo a " + founderDisplay, founderFile.sha());
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
            return new ActionResult(false, "Falta configurar el token de GitHub.");
        }

        String founderNormalized = normalizeMinecraftUsername(request.founderMinecraftUsername());
        String teamId = request.teamId() == null ? "" : request.teamId().trim();
        String teamName = request.teamName() == null ? "" : request.teamName().trim();
        String normalizedTeamName = normalizeTeamName(teamName);
        String colorHex = normalizeHexColor(request.teamColorHex());
        String iconBase64 = request.teamIconBase64() == null ? "" : request.teamIconBase64().trim();

        if (!GithubDatabaseService.isValidMinecraftUsername(founderNormalized)) {
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
            byte[] iconBytes = Base64.getDecoder().decode(iconBase64);
            if (iconBytes.length == 0 || iconBytes.length > 1_500_000) {
                return new ActionResult(false, "El icono del equipo es demasiado pesado o invalido.");
            }

            TeamContext founderContext = loadContextOrThrow(founderNormalized);
            if (founderContext == null || !founderContext.hasTeam() || founderContext.team() == null || !founderContext.founder()) {
                return new ActionResult(false, "Solo el fundador puede editar el equipo.");
            }
            if (!teamId.equals(founderContext.team().teamId())) {
                return new ActionResult(false, "Solo puedes editar tu equipo actual.");
            }

            RepoFile teamFile = readFile(teamPath(teamId));
            if (teamFile == null) {
                return new ActionResult(false, "No se encontro el equipo.");
            }

            JsonObject teamRoot = parseObject(teamFile.content());
            TeamRecord currentTeam = parseTeamRecord(teamRoot);
            if (currentTeam == null || currentTeam.archived()) {
                return new ActionResult(false, "Ese equipo ya no esta disponible.");
            }

            for (RepoEntry entry : listDirectory(TEAMS_DIR)) {
                if (!entry.path().endsWith(".json") || entry.path().endsWith("_placeholder.json") || teamPath(teamId).equals(entry.path())) {
                    continue;
                }
                RepoFile existingFile = readFile(entry.path());
                if (existingFile == null) {
                    continue;
                }
                TeamRecord existingTeam = parseTeamRecord(parseObject(existingFile.content()));
                if (existingTeam == null || existingTeam.archived()) {
                    continue;
                }
                if (normalizedTeamName.equals(existingTeam.teamNameNormalized())) {
                    return new ActionResult(false, "Ya existe un equipo con ese nombre.");
                }
            }

            teamRoot.addProperty("teamName", teamName);
            teamRoot.addProperty("teamNameNormalized", normalizedTeamName);
            teamRoot.addProperty("representativeColor", colorHex);
            teamRoot.addProperty("teamIconBase64", iconBase64);
            teamRoot.addProperty("updatedAt", Instant.now().toString());
            writeJson(teamPath(teamId), teamRoot, "Actualizar equipo " + teamName, teamFile.sha());

            synchronizeTeamMembershipReferences(teamId, teamName, currentTeam.members());
            synchronizeTeamNotifications(teamId, teamName, colorHex);

            return new ActionResult(true, "Equipo actualizado correctamente.");
        } catch (IllegalArgumentException ex) {
            return new ActionResult(false, "No se pudo leer el icono del equipo.");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo actualizar el equipo."));
        }
    }

    ActionResult invitePlayer(String founderMinecraftUsername, String targetMinecraftUsername) {
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar el token de GitHub.");
        }

        String founderNormalized = normalizeMinecraftUsername(founderMinecraftUsername);
        String targetNormalized = normalizeMinecraftUsername(targetMinecraftUsername);
        if (!GithubDatabaseService.isValidMinecraftUsername(founderNormalized) || !GithubDatabaseService.isValidMinecraftUsername(targetNormalized)) {
            return new ActionResult(false, "Usuario invalido.");
        }
        if (founderNormalized.equals(targetNormalized)) {
            return new ActionResult(false, "No puedes invitarte a ti mismo.");
        }

        try {
            TeamContext founderContext = loadContextOrThrow(founderNormalized);
            if (founderContext == null || !founderContext.hasTeam() || founderContext.team() == null || !founderContext.founder()) {
                return new ActionResult(false, "Solo el fundador puede invitar jugadores.");
            }

            RepoFile targetFile = requireUserFile(targetNormalized);
            JsonObject targetRoot = parseObject(targetFile.content());
            TeamMembership targetMembership = parseTeamMembership(targetRoot);
            if (targetMembership != null && targetMembership.teamId() != null && !targetMembership.teamId().isBlank()) {
                return new ActionResult(false, "Ese jugador ya pertenece a otro equipo.");
            }

            RepoFile notificationsFile = readFile(notificationsPath(targetNormalized));
            JsonObject notificationsRoot = notificationsFile == null
                    ? createNotificationsRoot(targetRoot, targetNormalized)
                    : parseObject(notificationsFile.content());
            JsonArray notificationsArray = ensureArray(notificationsRoot, "notifications");
            String teamId = founderContext.team().teamId();

            for (JsonElement element : notificationsArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject notification = element.getAsJsonObject();
                if (!"team_invite".equals(stringValue(notification, "type"))) {
                    continue;
                }
                if (!"pending".equals(stringValue(notification, "status"))) {
                    continue;
                }
                if (teamId.equals(stringValue(notification, "teamId"))) {
                    return new ActionResult(false, "Ese jugador ya tiene una invitacion pendiente para este equipo.");
                }
            }

            JsonObject invite = new JsonObject();
            invite.addProperty("id", "invite_" + teamId + "_" + randomHex(4));
            invite.addProperty("type", "team_invite");
            invite.addProperty("status", "pending");
            invite.addProperty("read", false);
            invite.addProperty("createdAt", Instant.now().toString());
            invite.addProperty("teamId", founderContext.team().teamId());
            invite.addProperty("teamName", founderContext.team().teamName());
            invite.addProperty("teamColorHex", founderContext.team().representativeColor());
            invite.addProperty("inviterMinecraftUsername", founderContext.minecraftUsername());
            notificationsArray.add(invite);
            notificationsRoot.addProperty("updatedAt", Instant.now().toString());

            writeJson(
                    notificationsPath(targetNormalized),
                    notificationsRoot,
                    "Invitar a " + fallbackDisplayName(targetRoot, targetNormalized) + " al equipo " + founderContext.team().teamName(),
                    notificationsFile == null ? null : notificationsFile.sha()
            );
            return new ActionResult(true, "Invitacion enviada.");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo enviar la invitacion."));
        }
    }

    ActionResult respondToInvitation(String minecraftUsername, String notificationId, boolean accept) {
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar el token de GitHub.");
        }

        String normalizedUser = normalizeMinecraftUsername(minecraftUsername);
        if (!GithubDatabaseService.isValidMinecraftUsername(normalizedUser) || notificationId == null || notificationId.isBlank()) {
            return new ActionResult(false, "Notificacion invalida.");
        }

        try {
            RepoFile userFile = requireUserFile(normalizedUser);
            JsonObject userRoot = parseObject(userFile.content());
            TeamMembership existingMembership = parseTeamMembership(userRoot);

            RepoFile notificationsFile = readFile(notificationsPath(normalizedUser));
            if (notificationsFile == null) {
                return new ActionResult(false, "No se encontro la invitacion.");
            }

            JsonObject notificationsRoot = parseObject(notificationsFile.content());
            JsonArray notificationsArray = ensureArray(notificationsRoot, "notifications");
            JsonObject invite = null;
            for (JsonElement element : notificationsArray) {
                if (element.isJsonObject() && notificationId.equals(stringValue(element.getAsJsonObject(), "id"))) {
                    invite = element.getAsJsonObject();
                    break;
                }
            }

            if (invite == null || !"team_invite".equals(stringValue(invite, "type"))) {
                return new ActionResult(false, "No se encontro la invitacion.");
            }
            if (!"pending".equals(stringValue(invite, "status"))) {
                return new ActionResult(false, "Esa invitacion ya fue respondida.");
            }

            if (!accept) {
                invite.addProperty("status", "rejected");
                invite.addProperty("read", true);
                invite.addProperty("resolvedAt", Instant.now().toString());
                notificationsRoot.addProperty("updatedAt", Instant.now().toString());
                writeJson(notificationsPath(normalizedUser), notificationsRoot, "Rechazar invitacion de equipo", notificationsFile.sha());
                return new ActionResult(true, "Invitacion rechazada.");
            }

            if (existingMembership != null && existingMembership.teamId() != null && !existingMembership.teamId().isBlank()) {
                return new ActionResult(false, "Ya perteneces a un equipo. Solo puedes estar en uno a la vez.");
            }

            String teamId = stringValue(invite, "teamId");
            RepoFile teamFile = readFile(teamPath(teamId));
            if (teamFile == null) {
                invite.addProperty("status", "expired");
                invite.addProperty("read", true);
                invite.addProperty("resolvedAt", Instant.now().toString());
                notificationsRoot.addProperty("updatedAt", Instant.now().toString());
                writeJson(notificationsPath(normalizedUser), notificationsRoot, "Marcar invitacion expirada", notificationsFile.sha());
                return new ActionResult(false, "Ese equipo ya no existe.");
            }

            JsonObject teamRoot = parseObject(teamFile.content());
            TeamRecord team = parseTeamRecord(teamRoot);
            if (team == null || team.archived()) {
                invite.addProperty("status", "expired");
                invite.addProperty("read", true);
                invite.addProperty("resolvedAt", Instant.now().toString());
                notificationsRoot.addProperty("updatedAt", Instant.now().toString());
                writeJson(notificationsPath(normalizedUser), notificationsRoot, "Marcar invitacion archivada", notificationsFile.sha());
                return new ActionResult(false, "Ese equipo ya no esta disponible.");
            }

            JsonArray members = ensureArray(teamRoot, "members");
            for (JsonElement element : members) {
                if (element.isJsonObject() && normalizedUser.equals(stringValue(element.getAsJsonObject(), "minecraftUsernameNormalized"))) {
                    invite.addProperty("status", "accepted");
                    invite.addProperty("read", true);
                    invite.addProperty("resolvedAt", Instant.now().toString());
                    notificationsRoot.addProperty("updatedAt", Instant.now().toString());
                    writeJson(notificationsPath(normalizedUser), notificationsRoot, "Aceptar invitacion ya aplicada", notificationsFile.sha());
                    return new ActionResult(true, "Ya formas parte de ese equipo.");
                }
            }

            String now = Instant.now().toString();
            JsonObject newMember = new JsonObject();
            newMember.addProperty("minecraftUsername", fallbackDisplayName(userRoot, normalizedUser));
            newMember.addProperty("minecraftUsernameNormalized", normalizedUser);
            newMember.addProperty("role", "member");
            newMember.addProperty("joinedAt", now);
            members.add(newMember);

            setTeamMembership(userRoot, new TeamMembership(team.teamId(), team.teamName(), "member", now));

            invite.addProperty("status", "accepted");
            invite.addProperty("read", true);
            invite.addProperty("resolvedAt", now);
            notificationsRoot.addProperty("updatedAt", now);

            writeJson(teamPath(team.teamId()), teamRoot, "Aceptar invitacion para " + team.teamName(), teamFile.sha());
            writeJson(credentialsPath(normalizedUser), userRoot, "Asignar equipo a " + fallbackDisplayName(userRoot, normalizedUser), userFile.sha());
            writeJson(notificationsPath(normalizedUser), notificationsRoot, "Aceptar invitacion de equipo", notificationsFile.sha());

            addInfoNotification(
                    normalizeMinecraftUsername(team.founderMinecraftUsername()),
                    "Miembro nuevo",
                    fallbackDisplayName(userRoot, normalizedUser) + " se unio al equipo '" + team.teamName() + "'.",
                    team.teamId(),
                    team.teamName(),
                    team.representativeColor()
            );
            return new ActionResult(true, "Te uniste al equipo correctamente.");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo responder la invitacion."));
        }
    }

    ActionResult removeMember(String founderMinecraftUsername, String targetMinecraftUsername) {
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar el token de GitHub.");
        }

        String founderNormalized = normalizeMinecraftUsername(founderMinecraftUsername);
        String targetNormalized = normalizeMinecraftUsername(targetMinecraftUsername);
        if (!GithubDatabaseService.isValidMinecraftUsername(founderNormalized) || !GithubDatabaseService.isValidMinecraftUsername(targetNormalized)) {
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

            RepoFile teamFile = readFile(teamPath(founderContext.team().teamId()));
            if (teamFile == null) {
                return new ActionResult(false, "No se encontro el equipo.");
            }
            JsonObject teamRoot = parseObject(teamFile.content());
            JsonArray members = ensureArray(teamRoot, "members");
            JsonArray keptMembers = new JsonArray();
            boolean removed = false;
            for (JsonElement element : members) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject member = element.getAsJsonObject();
                String memberNormalized = stringValue(member, "minecraftUsernameNormalized");
                String role = stringValue(member, "role");
                if (targetNormalized.equals(memberNormalized) && !"founder".equals(role)) {
                    removed = true;
                    continue;
                }
                keptMembers.add(member);
            }

            if (!removed) {
                return new ActionResult(false, "Ese jugador no pertenece a tu equipo.");
            }

            teamRoot.add("members", keptMembers);
            RepoFile targetFile = requireUserFile(targetNormalized);
            JsonObject targetRoot = parseObject(targetFile.content());
            clearTeamMembership(targetRoot);

            writeJson(teamPath(founderContext.team().teamId()), teamRoot, "Expulsar miembro del equipo", teamFile.sha());
            writeJson(credentialsPath(targetNormalized), targetRoot, "Quitar equipo a " + fallbackDisplayName(targetRoot, targetNormalized), targetFile.sha());
            addInfoNotification(
                    targetNormalized,
                    "Expulsion",
                    "Fuiste expulsado del equipo '" + founderContext.team().teamName() + "'.",
                    founderContext.team().teamId(),
                    founderContext.team().teamName(),
                    founderContext.team().representativeColor()
            );
            return new ActionResult(true, "Miembro expulsado.");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo expulsar al miembro."));
        }
    }

    ActionResult leaveTeam(String minecraftUsername) {
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar el token de GitHub.");
        }

        String normalizedUser = normalizeMinecraftUsername(minecraftUsername);
        if (!GithubDatabaseService.isValidMinecraftUsername(normalizedUser)) {
            return new ActionResult(false, "Usuario invalido.");
        }

        try {
            TeamContext context = loadContextOrThrow(normalizedUser);
            if (context == null || !context.hasTeam() || context.team() == null) {
                return new ActionResult(false, "No perteneces a ningun equipo.");
            }

            RepoFile teamFile = readFile(teamPath(context.team().teamId()));
            RepoFile userFile = requireUserFile(normalizedUser);
            if (teamFile == null) {
                JsonObject userRoot = parseObject(userFile.content());
                clearTeamMembership(userRoot);
                writeJson(credentialsPath(normalizedUser), userRoot, "Limpiar equipo faltante", userFile.sha());
                return new ActionResult(true, "Tu equipo ya no existia. Tu cuenta fue actualizada.");
            }

            JsonObject teamRoot = parseObject(teamFile.content());
            JsonArray members = ensureArray(teamRoot, "members");
            JsonObject userRoot = parseObject(userFile.content());

            if (context.founder()) {
                List<JsonObject> remainingMembers = new ArrayList<>();
                for (JsonElement element : members) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject member = element.getAsJsonObject();
                    if (!normalizedUser.equals(stringValue(member, "minecraftUsernameNormalized"))) {
                        remainingMembers.add(member.deepCopy());
                    }
                }

                clearTeamMembership(userRoot);
                writeJson(credentialsPath(normalizedUser), userRoot, "Abandonar equipo", userFile.sha());

                if (remainingMembers.isEmpty()) {
                    teamRoot.addProperty("status", "archived");
                    teamRoot.addProperty("archivedAt", Instant.now().toString());
                    teamRoot.add("members", new JsonArray());
                    writeJson(teamPath(context.team().teamId()), teamRoot, "Archivar equipo vacio", teamFile.sha());
                    return new ActionResult(true, "Abandonaste el equipo. Como eras el ultimo miembro, el equipo fue archivado.");
                }

                remainingMembers.sort(Comparator.comparing(member -> stringValue(member, "joinedAt")));
                JsonObject newFounder = remainingMembers.get(0);
                newFounder.addProperty("role", "founder");
                teamRoot.addProperty("founderMinecraftUsername", stringValue(newFounder, "minecraftUsername"));

                JsonArray updatedMembers = new JsonArray();
                for (JsonObject member : remainingMembers) {
                    updatedMembers.add(member);
                }
                teamRoot.add("members", updatedMembers);
                writeJson(teamPath(context.team().teamId()), teamRoot, "Transferir liderazgo del equipo", teamFile.sha());

                String successorNormalized = stringValue(newFounder, "minecraftUsernameNormalized");
                RepoFile successorFile = requireUserFile(successorNormalized);
                JsonObject successorRoot = parseObject(successorFile.content());
                TeamMembership successorMembership = parseTeamMembership(successorRoot);
                String joinedAt = successorMembership == null || successorMembership.joinedAt() == null || successorMembership.joinedAt().isBlank()
                        ? stringValue(newFounder, "joinedAt")
                        : successorMembership.joinedAt();
                setTeamMembership(successorRoot, new TeamMembership(context.team().teamId(), context.team().teamName(), "founder", joinedAt));
                writeJson(credentialsPath(successorNormalized), successorRoot, "Promover fundador de equipo", successorFile.sha());

                addInfoNotification(
                        successorNormalized,
                        "Nuevo fundador",
                        "Ahora eres el fundador del equipo '" + context.team().teamName() + "'.",
                        context.team().teamId(),
                        context.team().teamName(),
                        context.team().representativeColor()
                );
                return new ActionResult(true, "Abandonaste tu equipo y el liderazgo fue transferido.");
            }

            JsonArray keptMembers = new JsonArray();
            for (JsonElement element : members) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject member = element.getAsJsonObject();
                if (!normalizedUser.equals(stringValue(member, "minecraftUsernameNormalized"))) {
                    keptMembers.add(member);
                }
            }
            teamRoot.add("members", keptMembers);
            clearTeamMembership(userRoot);

            writeJson(teamPath(context.team().teamId()), teamRoot, "Abandonar equipo", teamFile.sha());
            writeJson(credentialsPath(normalizedUser), userRoot, "Quitar equipo a " + context.minecraftUsername(), userFile.sha());
            addInfoNotification(
                    normalizeMinecraftUsername(context.team().founderMinecraftUsername()),
                    "Miembro salio",
                    context.minecraftUsername() + " abandono el equipo '" + context.team().teamName() + "'.",
                    context.team().teamId(),
                    context.team().teamName(),
                    context.team().representativeColor()
            );
            return new ActionResult(true, "Abandonaste tu equipo.");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudo abandonar el equipo."));
        }
    }

    ActionResult markNotificationsViewed(String minecraftUsername) {
        if (!hasWriteToken()) {
            return new ActionResult(false, "Falta configurar el token de GitHub.");
        }

        String normalizedUser = normalizeMinecraftUsername(minecraftUsername);
        if (!GithubDatabaseService.isValidMinecraftUsername(normalizedUser)) {
            return new ActionResult(false, "Usuario invalido.");
        }

        try {
            RepoFile notificationsFile = readFile(notificationsPath(normalizedUser));
            if (notificationsFile == null) {
                return new ActionResult(true, "");
            }

            JsonObject notificationsRoot = parseObject(notificationsFile.content());
            JsonArray notificationsArray = ensureArray(notificationsRoot, "notifications");
            boolean changed = false;
            for (JsonElement element : notificationsArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject notification = element.getAsJsonObject();
                if (!booleanValue(notification, "read", false)) {
                    notification.addProperty("read", true);
                    changed = true;
                }
            }

            if (!changed) {
                return new ActionResult(true, "");
            }

            notificationsRoot.addProperty("updatedAt", Instant.now().toString());
            writeJson(notificationsPath(normalizedUser), notificationsRoot, "Marcar notificaciones vistas", notificationsFile.sha());
            return new ActionResult(true, "");
        } catch (Exception ex) {
            return new ActionResult(false, friendlyErrorMessage(ex, "No se pudieron actualizar las notificaciones."));
        }
    }

    private void synchronizeTeamMembershipReferences(String teamId, String teamName, List<TeamMember> members) throws Exception {
        if (teamId == null || teamId.isBlank() || members == null) {
            return;
        }

        for (TeamMember member : members) {
            if (member == null || member.normalizedMinecraftUsername() == null || member.normalizedMinecraftUsername().isBlank()) {
                continue;
            }
            RepoFile userFile = readFile(credentialsPath(member.normalizedMinecraftUsername()));
            if (userFile == null) {
                continue;
            }

            JsonObject userRoot = parseObject(userFile.content());
            TeamMembership membership = parseTeamMembership(userRoot);
            if (membership == null || !teamId.equals(membership.teamId())) {
                continue;
            }

            setTeamMembership(userRoot, new TeamMembership(teamId, teamName, membership.role(), membership.joinedAt()));
            writeJson(
                    credentialsPath(member.normalizedMinecraftUsername()),
                    userRoot,
                    "Actualizar membresia de equipo " + teamName,
                    userFile.sha()
            );
        }
    }

    private void synchronizeTeamNotifications(String teamId, String teamName, String colorHex) throws Exception {
        if (teamId == null || teamId.isBlank()) {
            return;
        }

        for (RepoEntry entry : listDirectory(NOTIFICATIONS_DIR)) {
            if (!entry.path().endsWith(".json") || entry.path().endsWith("_placeholder.json")) {
                continue;
            }
            RepoFile notificationsFile = readFile(entry.path());
            if (notificationsFile == null) {
                continue;
            }

            JsonObject notificationsRoot = parseObject(notificationsFile.content());
            JsonArray notificationsArray = ensureArray(notificationsRoot, "notifications");
            boolean changed = false;
            for (JsonElement element : notificationsArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject notification = element.getAsJsonObject();
                if (!teamId.equals(stringValue(notification, "teamId"))) {
                    continue;
                }
                notification.addProperty("teamName", teamName);
                notification.addProperty("teamColorHex", colorHex);
                changed = true;
            }

            if (!changed) {
                continue;
            }

            notificationsRoot.addProperty("updatedAt", Instant.now().toString());
            writeJson(entry.path(), notificationsRoot, "Actualizar referencias de equipo " + teamName, notificationsFile.sha());
        }
    }

    private TeamContext loadContextOrThrow(String normalizedMinecraftUsername) throws Exception {
        RepoFile userFile = requireUserFile(normalizedMinecraftUsername);
        JsonObject userRoot = parseObject(userFile.content());
        String displayMinecraftName = fallbackDisplayName(userRoot, normalizedMinecraftUsername);
        String discordUsername = resolveDiscordUsername(userRoot);

        TeamMembership membership = parseTeamMembership(userRoot);
        TeamRecord team = null;
        boolean founder = false;
        if (membership != null && membership.teamId() != null && !membership.teamId().isBlank()) {
            RepoFile teamFile = readFile(teamPath(membership.teamId()));
            if (teamFile != null) {
                team = parseTeamRecord(parseObject(teamFile.content()));
            }
            if (team == null || team.archived() || team.members().stream().noneMatch(member -> normalizedMinecraftUsername.equals(member.normalizedMinecraftUsername()))) {
                clearTeamMembership(userRoot);
                writeJson(credentialsPath(normalizedMinecraftUsername), userRoot, "Limpiar membresia de equipo", userFile.sha());
                membership = null;
                team = null;
            } else {
                founder = team.members().stream().anyMatch(member -> member.founder() && normalizedMinecraftUsername.equals(member.normalizedMinecraftUsername()));
            }
        }

        List<NotificationItem> notifications = loadNotifications(normalizedMinecraftUsername, userRoot);
        return new TeamContext(
                displayMinecraftName,
                normalizedMinecraftUsername,
                discordUsername,
                team != null,
                founder,
                team,
                notifications
        );
    }

    private List<NotificationItem> loadNotifications(String normalizedMinecraftUsername, JsonObject userRoot) throws Exception {
        RepoFile notificationsFile = readFile(notificationsPath(normalizedMinecraftUsername));
        JsonObject notificationsRoot = notificationsFile == null
                ? createNotificationsRoot(userRoot, normalizedMinecraftUsername)
                : parseObject(notificationsFile.content());
        JsonArray notificationsArray = ensureArray(notificationsRoot, "notifications");
        List<NotificationItem> items = new ArrayList<>();
        for (JsonElement element : notificationsArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            NotificationItem item = parseNotification(element.getAsJsonObject());
            if (item != null) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparing(NotificationItem::createdAt, Comparator.nullsLast(String::compareTo)).reversed());
        return List.copyOf(items);
    }

    private NotificationItem parseNotification(JsonObject json) {
        if (json == null) {
            return null;
        }
        String id = stringValue(json, "id");
        String type = stringValue(json, "type");
        if (id == null || id.isBlank() || type == null || type.isBlank()) {
            return null;
        }

        String teamName = stringValue(json, "teamName");
        String message = stringValue(json, "message");
        if ((message == null || message.isBlank()) && "team_invite".equals(type)) {
            message = "Has sido invitado al equipo '" + teamName + "'.";
        }

        return new NotificationItem(
                id,
                type,
                defaultString(stringValue(json, "status"), "pending"),
                booleanValue(json, "read", false),
                stringValue(json, "createdAt"),
                stringValue(json, "inviterMinecraftUsername"),
                stringValue(json, "teamId"),
                teamName,
                stringValue(json, "teamColorHex"),
                defaultString(message, "Notificacion")
        );
    }

    private void addInfoNotification(String normalizedMinecraftUsername, String title, String message, String teamId, String teamName, String colorHex) throws Exception {
        if (normalizedMinecraftUsername == null || normalizedMinecraftUsername.isBlank()) {
            return;
        }

        RepoFile userFile = requireUserFile(normalizedMinecraftUsername);
        JsonObject userRoot = parseObject(userFile.content());
        RepoFile notificationsFile = readFile(notificationsPath(normalizedMinecraftUsername));
        JsonObject notificationsRoot = notificationsFile == null
                ? createNotificationsRoot(userRoot, normalizedMinecraftUsername)
                : parseObject(notificationsFile.content());
        JsonArray notificationsArray = ensureArray(notificationsRoot, "notifications");

        JsonObject info = new JsonObject();
        info.addProperty("id", "info_" + randomHex(5));
        info.addProperty("type", "team_info");
        info.addProperty("status", "info");
        info.addProperty("read", false);
        info.addProperty("createdAt", Instant.now().toString());
        info.addProperty("message", title == null || title.isBlank() ? message : title + ": " + message);
        info.addProperty("teamId", defaultString(teamId, ""));
        info.addProperty("teamName", defaultString(teamName, ""));
        info.addProperty("teamColorHex", defaultString(colorHex, ""));
        notificationsArray.add(info);
        notificationsRoot.addProperty("updatedAt", Instant.now().toString());

        writeJson(
                notificationsPath(normalizedMinecraftUsername),
                notificationsRoot,
                "Agregar notificacion para " + fallbackDisplayName(userRoot, normalizedMinecraftUsername),
                notificationsFile == null ? null : notificationsFile.sha()
        );
    }

    private JsonObject createNotificationsRoot(JsonObject userRoot, String normalizedMinecraftUsername) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("minecraftUsername", fallbackDisplayName(userRoot, normalizedMinecraftUsername));
        root.addProperty("minecraftUsernameNormalized", normalizedMinecraftUsername);
        root.addProperty("updatedAt", Instant.now().toString());
        root.add("notifications", new JsonArray());
        return root;
    }

    private TeamRecord parseTeamRecord(JsonObject root) {
        if (root == null) {
            return null;
        }

        String teamId = stringValue(root, "teamId");
        String teamName = stringValue(root, "teamName");
        if (teamId == null || teamId.isBlank() || teamName == null || teamName.isBlank()) {
            return null;
        }

        JsonArray membersArray = ensureArray(root, "members");
        List<TeamMember> members = new ArrayList<>();
        for (JsonElement element : membersArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject member = element.getAsJsonObject();
            String username = stringValue(member, "minecraftUsername");
            String normalized = normalizeMinecraftUsername(stringValue(member, "minecraftUsernameNormalized"));
            if (!GithubDatabaseService.isValidMinecraftUsername(normalized)) {
                normalized = normalizeMinecraftUsername(username);
            }
            if (!GithubDatabaseService.isValidMinecraftUsername(normalized)) {
                continue;
            }
            members.add(new TeamMember(
                    defaultString(username, normalized),
                    normalized,
                    "founder".equalsIgnoreCase(stringValue(member, "role")),
                    stringValue(member, "joinedAt")
            ));
        }

        return new TeamRecord(
                teamId,
                teamName,
                defaultString(stringValue(root, "teamNameNormalized"), normalizeTeamName(teamName)),
                defaultString(stringValue(root, "representativeColor"), "#9EC0FF"),
                defaultString(stringValue(root, "teamIconBase64"), ""),
                defaultString(stringValue(root, "founderMinecraftUsername"), members.isEmpty() ? "" : members.get(0).minecraftUsername()),
                "archived".equalsIgnoreCase(stringValue(root, "status")),
                List.copyOf(members)
        );
    }

    private TeamMembership parseTeamMembership(JsonObject root) {
        if (root == null || !root.has("teamMembership") || !root.get("teamMembership").isJsonObject()) {
            return null;
        }
        JsonObject membership = root.getAsJsonObject("teamMembership");
        String teamId = stringValue(membership, "teamId");
        if (teamId == null || teamId.isBlank()) {
            return null;
        }
        return new TeamMembership(
                teamId,
                defaultString(stringValue(membership, "teamName"), ""),
                defaultString(stringValue(membership, "role"), "member"),
                defaultString(stringValue(membership, "joinedAt"), "")
        );
    }

    private void setTeamMembership(JsonObject root, TeamMembership membership) {
        JsonObject membershipJson = new JsonObject();
        membershipJson.addProperty("teamId", membership.teamId());
        membershipJson.addProperty("teamName", membership.teamName());
        membershipJson.addProperty("role", membership.role());
        membershipJson.addProperty("joinedAt", membership.joinedAt());
        root.add("teamMembership", membershipJson);
    }

    private void clearTeamMembership(JsonObject root) {
        root.remove("teamMembership");
    }

    private RepoFile requireUserFile(String normalizedMinecraftUsername) throws Exception {
        RepoFile userFile = readFile(credentialsPath(normalizedMinecraftUsername));
        if (userFile == null) {
            throw new IOException("No se encontro la cuenta de " + normalizedMinecraftUsername + ".");
        }
        return userFile;
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
        String sha = stringValue(root, "sha");
        String content = stringValue(root, "content");
        if (content == null) {
            throw new IOException("GitHub no devolvio contenido para " + relativePath);
        }

        byte[] decoded = Base64.getDecoder().decode(content.replace("\n", "").replace("\r", ""));
        return new RepoFile(relativePath, sha, new String(decoded, StandardCharsets.UTF_8));
    }

    private List<RepoEntry> listDirectory(String relativePath) throws Exception {
        URI uri = URI.create(GITHUB_API_BASE + encodePath(relativePath) + "?ref=" + GITHUB_BRANCH);
        HttpRequest request = requestBuilder(uri)
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) {
            return List.of();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub devolvio HTTP " + response.statusCode() + " al listar " + relativePath);
        }

        JsonElement root = JsonParser.parseString(response.body());
        if (!root.isJsonArray()) {
            return List.of();
        }

        List<RepoEntry> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String path = stringValue(object, "path");
            String name = stringValue(object, "name");
            if (path != null && name != null) {
                entries.add(new RepoEntry(path, name));
            }
        }
        return List.copyOf(entries);
    }

    private void writeJson(String relativePath, JsonObject content, String message, String sha) throws Exception {
        ensureWriteToken();
        String workingSha = sha;
        for (int attempt = 0; attempt < 2; attempt++) {
            JsonObject body = new JsonObject();
            body.addProperty("message", message);
            body.addProperty("branch", GITHUB_BRANCH);
            body.addProperty("content", Base64.getEncoder().encodeToString(gson.toJson(content).getBytes(StandardCharsets.UTF_8)));
            if (workingSha != null && !workingSha.isBlank()) {
                body.addProperty("sha", workingSha);
            }

            URI uri = URI.create(GITHUB_API_BASE + encodePath(relativePath));
            HttpRequest request = requestBuilder(uri)
                    .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = response.statusCode();
            if (code == 200 || code == 201) {
                return;
            }
            if (code == 409 && attempt == 0) {
                RepoFile latest = readFile(relativePath);
                workingSha = latest == null ? null : latest.sha();
                continue;
            }
            if (code == 404) {
                throw new IOException("GitHub no encontro la branch o el repo al escribir " + relativePath + ".");
            }
            throw new IOException("GitHub devolvio HTTP " + code + " al escribir " + relativePath);
        }
    }

    private void ensureWriteToken() throws IOException {
        if (!hasWriteToken()) {
            throw new IOException("Falta configurar el token de GitHub.");
        }
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

    private static String credentialsPath(String normalizedMinecraftUser) {
        return USERS_DIR + "/" + normalizedMinecraftUser + "_credentials.json";
    }

    private static String notificationsPath(String normalizedMinecraftUser) {
        return NOTIFICATIONS_DIR + "/" + normalizedMinecraftUser + ".json";
    }

    private static String teamPath(String teamId) {
        return TEAMS_DIR + "/" + teamId + ".json";
    }

    private static JsonObject parseObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static JsonArray ensureArray(JsonObject root, String key) {
        if (root.has(key) && root.get(key).isJsonArray()) {
            return root.getAsJsonArray(key);
        }
        JsonArray array = new JsonArray();
        root.add(key, array);
        return array;
    }

    private static String fallbackDisplayName(JsonObject root, String normalizedMinecraftUsername) {
        String value = stringValue(root, "minecraftUsername");
        return value == null || value.isBlank() ? normalizedMinecraftUsername : value;
    }

    private static String resolveDiscordUsername(JsonObject root) {
        if (root != null && root.has("discord") && root.get("discord").isJsonObject()) {
            JsonObject discord = root.getAsJsonObject("discord");
            String displayName = stringValue(discord, "globalName");
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
            displayName = stringValue(discord, "displayName");
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
            displayName = stringValue(discord, "username");
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
        }
        return "";
    }

    private static boolean isValidTeamName(String raw) {
        if (raw == null) {
            return false;
        }
        return raw.matches("[A-Za-z0-9][A-Za-z0-9 _-]{1,22}[A-Za-z0-9]");
    }

    private static String normalizeTeamName(String raw) {
        if (raw == null) {
            return "";
        }
        String condensed = raw.trim().replaceAll("\\s+", " ");
        return condensed.toLowerCase(Locale.ROOT);
    }

    private static String normalizeMinecraftUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
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

    private static String generateTeamId(String normalizedTeamName) {
        String slug = normalizedTeamName == null ? "" : normalizedTeamName.replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        if (slug.isBlank()) {
            slug = "equipo";
        }
        return "team_" + slug + "_" + randomHex(4);
    }

    private static String randomHex(int bytes) {
        byte[] random = new byte[Math.max(1, bytes)];
        RNG.nextBytes(random);
        return HEX.formatHex(random);
    }

    private static String stringValue(JsonObject root, String key) {
        if (root == null || key == null || !root.has(key) || root.get(key).isJsonNull()) {
            return null;
        }
        try {
            return root.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean booleanValue(JsonObject root, String key, boolean fallback) {
        if (root == null || key == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return root.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
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

    private static String resolveGithubToken() {
        String fromEnvironment = System.getenv(TOKEN_ENV_VAR);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }

        JsonObject localConfig = readPrivateJsonFromDisk();
        String fromDisk = stringValue(localConfig, "githubToken");
        if (fromDisk != null && !fromDisk.isBlank()) {
            return fromDisk.trim();
        }

        JsonObject resourceConfig = readPrivateJsonFromResources();
        String fromResource = stringValue(resourceConfig, "githubToken");
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
        try (InputStream in = GithubTeamsService.class.getResourceAsStream(PRIVATE_RESOURCE_PATH)) {
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

    private record RepoEntry(String path, String name) {
    }

    private record TeamMembership(String teamId, String teamName, String role, String joinedAt) {
    }
}
