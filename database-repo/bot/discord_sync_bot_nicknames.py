from __future__ import annotations

import atexit
import asyncio
import base64
import json
import msvcrt
import os
import socket
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from typing import Dict, List, Optional
from urllib import error, parse, request

import discord
from discord import app_commands
from discord.ext import tasks


CONFIG_PATH = Path(__file__).with_name("bot-config.json")
TEAM_TEXT_CHANNEL_NAME = "💬・ᴄʜᴀᴛ ᴘʀɪᴠ"
TEAM_VOICE_CHANNEL_NAME = "🔊・ᴠᴄ ᴘʀɪᴠᴀᴅᴏ"
USERS_DIR = "users"
TEAMS_DIR = "teams"
LOCK_PATH = Path(__file__).with_name(".discord-bot.lock")
STATE_PATH = Path(__file__).with_name("bot-state.json")
DEFAULT_REQUEST_TIMEOUT_SECONDS = 20
DEFAULT_SUPABASE_RETRY_ATTEMPTS = 3
DEFAULT_ERROR_NOTIFY_COOLDOWN_SECONDS = 1800
REGISTRATION_VERIFICATION_JOB_TYPE = "registration_verification"
REGISTRATION_VERIFICATION_TTL_MINUTES = 10


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def load_config() -> dict:
    if not CONFIG_PATH.exists():
        raise SystemExit(
            "Falta bot-config.json. Copia bot-config.template.json a bot-config.json y completa los tokens."
        )
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


CONFIG = load_config()
STATE_LOCK = Lock()
ERROR_NOTIFY_STATE: Dict[str, float] = {}


def load_state() -> dict:
    if not STATE_PATH.exists():
        return {"teams": {}}
    try:
        payload = json.loads(STATE_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {"teams": {}}
    if not isinstance(payload, dict):
        return {"teams": {}}
    teams = payload.get("teams")
    payload["teams"] = teams if isinstance(teams, dict) else {}
    return payload


BOT_STATE = load_state()


def save_state() -> None:
    temp_path = STATE_PATH.with_suffix(".tmp")
    serialized = json.dumps(BOT_STATE, indent=2, ensure_ascii=False, sort_keys=True)
    temp_path.write_text(serialized, encoding="utf-8")
    temp_path.replace(STATE_PATH)


def get_team_state(team_id: str) -> dict:
    with STATE_LOCK:
        teams = BOT_STATE.setdefault("teams", {})
        payload = teams.get(team_id)
        return dict(payload) if isinstance(payload, dict) else {}


def set_team_state(team_id: str, payload: dict) -> None:
    with STATE_LOCK:
        BOT_STATE.setdefault("teams", {})[team_id] = payload
        save_state()


def should_notify_error(key: str, *, cooldown_seconds: int = DEFAULT_ERROR_NOTIFY_COOLDOWN_SECONDS) -> bool:
    now = time.time()
    last_sent = ERROR_NOTIFY_STATE.get(key, 0.0)
    if now - last_sent < cooldown_seconds:
        return False
    ERROR_NOTIFY_STATE[key] = now
    return True


class SupabaseDataClient:
    def __init__(self, config: dict) -> None:
        self.base_url = str(config.get("supabaseUrl", "")).strip().rstrip("/")
        self.api_key = str(
            config.get("supabaseServiceRoleKey") or config.get("supabaseAnonKey") or ""
        ).strip()

    def is_configured(self) -> bool:
        return bool(self.base_url and self.api_key)

    def _request(
        self,
        path: str,
        *,
        method: str = "GET",
        body: Optional[object] = None,
        query: Optional[dict] = None,
        prefer: Optional[str] = None,
        content_type: str = "application/json",
    ) -> request.Request:
        if not self.is_configured():
            raise RuntimeError("Falta configurar supabaseUrl o supabaseServiceRoleKey en bot-config.json")

        payload = None
        if body is not None:
            payload = json.dumps(body).encode("utf-8")

        url = f"{self.base_url}{path}"
        if query:
            url = f"{url}?{parse.urlencode(query)}"

        req = request.Request(url, data=payload, method=method)
        req.add_header("apikey", self.api_key)
        req.add_header("Authorization", f"Bearer {self.api_key}")
        req.add_header("User-Agent", "SemencraftDiscordBot/1.0")
        if prefer:
            req.add_header("Prefer", prefer)
        if content_type:
            req.add_header("Content-Type", content_type)
        return req

    def request_json(
        self,
        path: str,
        *,
        method: str = "GET",
        body: Optional[object] = None,
        query: Optional[dict] = None,
        prefer: Optional[str] = None,
    ) -> object:
        req = self._request(path, method=method, body=body, query=query, prefer=prefer)
        attempts = max(1, int(CONFIG.get("supabaseRetryAttempts", DEFAULT_SUPABASE_RETRY_ATTEMPTS)))
        timeout_seconds = max(5, int(CONFIG.get("supabaseRequestTimeoutSeconds", DEFAULT_REQUEST_TIMEOUT_SECONDS)))
        retryable_http_codes = {408, 425, 429, 500, 502, 503, 504}
        raw = ""

        for attempt in range(1, attempts + 1):
            try:
                with request.urlopen(req, timeout=timeout_seconds) as response:
                    raw = response.read().decode("utf-8")
                break
            except error.HTTPError as exc:
                details = ""
                try:
                    details = exc.read().decode("utf-8", errors="replace")
                except Exception:
                    details = ""
                if exc.code in retryable_http_codes and attempt < attempts:
                    time.sleep(min(4, attempt))
                    continue
                raise RuntimeError(
                    f"Supabase devolvio HTTP {exc.code} en {path}"
                    + (f": {details}" if details else "")
                ) from exc
            except (error.URLError, TimeoutError, socket.timeout, OSError) as exc:
                if attempt < attempts:
                    time.sleep(min(4, attempt))
                    continue
                raise RuntimeError(f"Supabase no respondio en {path}: {exc}") from exc

        raw = raw.strip()
        if not raw:
            return None
        return json.loads(raw)

    def select(
        self,
        table: str,
        filters: Optional[dict] = None,
        *,
        order: Optional[str] = None,
        limit: Optional[int] = None,
    ) -> List[dict]:
        query = {"select": "*"}
        if filters:
            query.update(filters)
        if order:
            query["order"] = order
        if limit:
            query["limit"] = str(limit)
        payload = self.request_json(f"/rest/v1/{table}", query=query)
        return payload if isinstance(payload, list) else []

    def upsert(self, table: str, payload: object, *, on_conflict: Optional[str] = None) -> object:
        query = {}
        if on_conflict:
            query["on_conflict"] = on_conflict
        return self.request_json(
            f"/rest/v1/{table}",
            method="POST",
            body=payload,
            query=query,
            prefer="return=representation,resolution=merge-duplicates",
        )

    def update(self, table: str, payload: dict, filters: dict) -> object:
        return self.request_json(
            f"/rest/v1/{table}",
            method="PATCH",
            body=payload,
            query=filters,
            prefer="return=representation",
        )

    def delete(self, table: str, filters: dict) -> None:
        self.request_json(
            f"/rest/v1/{table}",
            method="DELETE",
            query=filters,
            prefer="return=minimal",
        )


@dataclass
class RepoFile:
    path: str
    sha: Optional[str]
    content: str


@dataclass
class RegisteredAccount:
    minecraft_username: str
    minecraft_username_normalized: str
    discord_user_id: str


@dataclass
class TeamMemberRecord:
    minecraft_username: str
    minecraft_username_normalized: str
    discord_user_id: str
    role: str


@dataclass
class TeamDocument:
    path: str
    sha: Optional[str]
    payload: dict
    team_id: str
    team_name: str
    representative_color: str
    icon_path: str
    founder_minecraft_username: str
    founder_discord_user_id: str
    archived: bool
    members: List[TeamMemberRecord]

    @property
    def discord_resources(self) -> dict:
        resources = self.payload.get("discordResources")
        return resources if isinstance(resources, dict) else {}


class GithubContentsClient:
    def __init__(self, config: dict) -> None:
        self.owner = config["githubOwner"]
        self.repo = config["githubRepo"]
        self.branch = config.get("githubBranch", "main")
        self.token = config["githubToken"]

    def _url(self, relative_path: str) -> str:
        encoded = "/".join(parse.quote(part) for part in relative_path.split("/"))
        return f"https://api.github.com/repos/{self.owner}/{self.repo}/contents/{encoded}"

    def _request(
        self,
        relative_path: str,
        method: str = "GET",
        body: Optional[dict] = None,
        query: Optional[dict] = None,
    ) -> request.Request:
        payload = None
        if body is not None:
            payload = json.dumps(body).encode("utf-8")
        url = self._url(relative_path)
        if query:
            url = f"{url}?{parse.urlencode(query)}"
        req = request.Request(url, data=payload, method=method)
        req.add_header("Accept", "application/vnd.github+json")
        req.add_header("Authorization", f"Bearer {self.token}")
        req.add_header("User-Agent", "SemencraftDiscordBot/1.0")
        if body is not None:
            req.add_header("Content-Type", "application/json")
        return req

    def read_file(self, relative_path: str) -> Optional[RepoFile]:
        req = self._request(relative_path, query={"ref": self.branch})
        try:
            with request.urlopen(req, timeout=30) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exc:
            if exc.code == 404:
                return None
            raise RuntimeError(f"GitHub devolvio HTTP {exc.code} al leer {relative_path}") from exc

        raw_content = payload.get("content", "").replace("\n", "").replace("\r", "")
        decoded = base64.b64decode(raw_content).decode("utf-8")
        return RepoFile(relative_path, payload.get("sha"), decoded)

    def list_directory(self, relative_path: str) -> List[dict]:
        req = self._request(relative_path, query={"ref": self.branch})
        try:
            with request.urlopen(req, timeout=30) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exc:
            if exc.code == 404:
                return []
            raise RuntimeError(f"GitHub devolvio HTTP {exc.code} al listar {relative_path}") from exc

        if isinstance(payload, list):
            return payload
        return []

    def write_json(self, relative_path: str, payload: dict, message: str, sha: Optional[str] = None) -> None:
        body = {
            "message": message,
            "branch": self.branch,
            "content": base64.b64encode(json.dumps(payload, indent=2).encode("utf-8")).decode("utf-8"),
        }
        if sha:
            body["sha"] = sha
        req = self._request(relative_path, method="PUT", body=body)
        try:
            with request.urlopen(req, timeout=30):
                return
        except error.HTTPError as exc:
            details = ""
            try:
                details = exc.read().decode("utf-8", errors="replace")
            except Exception:
                details = ""
            if exc.code == 404:
                raise RuntimeError(
                    "GitHub devolvio HTTP 404 al escribir "
                    f"{relative_path}. El repo probablemente sigue vacio o la branch '{self.branch}' no existe aun."
                ) from exc
            if details:
                raise RuntimeError(f"GitHub devolvio HTTP {exc.code} al escribir {relative_path}: {details}") from exc
            raise RuntimeError(f"GitHub devolvio HTTP {exc.code} al escribir {relative_path}") from exc


github = (
    GithubContentsClient(CONFIG)
    if CONFIG.get("githubOwner") and CONFIG.get("githubRepo") and CONFIG.get("githubToken")
    else None
)
supabase = SupabaseDataClient(CONFIG)

intents = discord.Intents.default()
intents.guilds = True
intents.members = True
client = discord.Client(intents=intents)
tree = app_commands.CommandTree(client)
sync_lock = asyncio.Lock()
last_team_signature: Dict[str, str] = {}
last_user_signature: Dict[str, str] = {}
LOCK_HANDLE = None
SYNCED_COMMAND_GUILDS: set[int] = set()


async def github_list_directory_async(relative_path: str) -> List[dict]:
    return await asyncio.to_thread(github.list_directory, relative_path)


async def github_read_file_async(relative_path: str) -> Optional[RepoFile]:
    return await asyncio.to_thread(github.read_file, relative_path)


async def github_write_json_async(
    relative_path: str,
    payload: dict,
    message: str,
    sha: Optional[str] = None,
) -> None:
    await asyncio.to_thread(github.write_json, relative_path, payload, message, sha)


async def read_directory_signature_async(directory: str) -> Dict[str, str]:
    return await asyncio.to_thread(read_directory_signature, directory)


async def load_registered_accounts_async() -> Dict[str, RegisteredAccount]:
    return await asyncio.to_thread(load_registered_accounts)


async def load_team_documents_async() -> List[TeamDocument]:
    return await asyncio.to_thread(load_team_documents)


async def persist_team_metadata_async(team: TeamDocument, message: str) -> None:
    await asyncio.to_thread(persist_team_metadata, team, message)


def normalize_lookup(value: Optional[str]) -> str:
    if value is None:
        return ""
    text = value.strip()
    if text.startswith("@"):
        text = text[1:]
    return text.lower()


def normalize_minecraft_username(value: Optional[str]) -> str:
    return (value or "").strip().lower()


def normalize_hex_color(value: Optional[str]) -> str:
    text = (value or "").strip().upper()
    if not text:
        return "#9EC0FF"
    if not text.startswith("#"):
        text = "#" + text
    if len(text) != 7:
        return "#9EC0FF"
    for character in text[1:]:
        if character not in "0123456789ABCDEF":
            return "#9EC0FF"
    return text


def discord_color_from_hex(value: Optional[str]) -> discord.Colour:
    normalized = normalize_hex_color(value)
    return discord.Colour(int(normalized[1:], 16))


def normalize_text_channel_name(value: Optional[str]) -> str:
    return (value or "").strip().replace(" ", "-")


def parse_iso_datetime(raw: Optional[str]) -> Optional[datetime]:
    text = str(raw or "").strip()
    if not text:
        return None
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        return None


def supabase_public_object_url(bucket: str, object_path: Optional[str]) -> str:
    if not supabase.base_url or not object_path:
        return ""
    safe_path = "/".join(parse.quote(part) for part in str(object_path).split("/") if part)
    return f"{supabase.base_url}/storage/v1/object/public/{bucket}/{safe_path}"


def team_icon_url(team: TeamDocument) -> str:
    return supabase_public_object_url("team-icons", team.icon_path)


def format_member_reference(minecraft_username: str, discord_user_id: Optional[str], *, founder: bool = False) -> str:
    prefix = "👑 " if founder else "• "
    if discord_user_id and str(discord_user_id).isdigit():
        return f"{prefix}<@{discord_user_id}> (`{minecraft_username}`)"
    return f"{prefix}`{minecraft_username}`"


def format_member_list(team: TeamDocument, *, include_founder: bool = True) -> str:
    if not team.members:
        return "Sin miembros."
    lines: List[str] = []
    founder_key = normalize_minecraft_username(team.founder_minecraft_username)
    for member in sorted(team.members, key=lambda item: (item.role != "founder", item.minecraft_username.lower())):
        is_founder = member.role == "founder" or member.minecraft_username_normalized == founder_key
        if is_founder and not include_founder:
            continue
        lines.append(format_member_reference(member.minecraft_username, member.discord_user_id, founder=is_founder))
    return "\n".join(lines) if lines else "Sin miembros adicionales."


def normalize_team_snapshot(team: TeamDocument) -> dict:
    return {
        "teamId": team.team_id,
        "teamName": team.team_name,
        "representativeColor": normalize_hex_color(team.representative_color),
        "iconPath": team.icon_path or "",
        "archived": bool(team.archived),
        "founderMinecraftUsername": team.founder_minecraft_username or "",
        "founderDiscordUserId": team.founder_discord_user_id or "",
        "members": sorted(
            [
                {
                    "minecraftUsername": member.minecraft_username,
                    "minecraftUsernameNormalized": member.minecraft_username_normalized,
                    "discordUserId": member.discord_user_id,
                    "role": member.role,
                }
                for member in team.members
            ],
            key=lambda item: (item["role"] != "founder", item["minecraftUsernameNormalized"]),
        ),
    }


def normalize_saved_team_snapshot(saved_state: dict) -> dict:
    return {
        "teamId": str(saved_state.get("teamId", "")).strip(),
        "teamName": str(saved_state.get("teamName", "")).strip(),
        "representativeColor": normalize_hex_color(saved_state.get("representativeColor")),
        "iconPath": str(saved_state.get("iconPath", "")).strip(),
        "archived": bool(saved_state.get("archived", False)),
        "founderMinecraftUsername": str(saved_state.get("founderMinecraftUsername", "")).strip(),
        "founderDiscordUserId": str(saved_state.get("founderDiscordUserId", "")).strip(),
        "members": sorted(
            [
                {
                    "minecraftUsername": str(item.get("minecraftUsername", "")).strip(),
                    "minecraftUsernameNormalized": str(item.get("minecraftUsernameNormalized", "")).strip(),
                    "discordUserId": str(item.get("discordUserId", "")).strip(),
                    "role": str(item.get("role", "member")).strip().lower() or "member",
                }
                for item in saved_state.get("members", [])
                if isinstance(item, dict)
            ],
            key=lambda item: (item["role"] != "founder", item["minecraftUsernameNormalized"]),
        ),
    }


def team_summary_line(team: TeamDocument) -> str:
    leader_name = team.founder_minecraft_username or (team.members[0].minecraft_username if team.members else "Desconocido")
    return (
        f"👑 Lider: `{leader_name}`\n"
        f"👥 Miembros: `{len(team.members)}`\n"
        f"🎨 Color: `{normalize_hex_color(team.representative_color)}`"
    )


def build_lookup_keys(member: discord.Member) -> List[str]:
    keys = []
    for candidate in (
        member.name,
        member.display_name,
        member.global_name,
        f"@{member.name}",
    ):
        normalized = normalize_lookup(candidate)
        if normalized and normalized not in keys:
            keys.append(normalized)
    return keys


def target_guild_id() -> Optional[int]:
    raw = str(CONFIG.get("guildId", "")).strip()
    if not raw:
        return None
    try:
        return int(raw)
    except ValueError:
        raise RuntimeError(f"guildId invalido en bot-config.json: {raw}")


def admin_channel_id() -> Optional[int]:
    raw = str(CONFIG.get("adminChannelId", "")).strip()
    if not raw:
        return None
    try:
        return int(raw)
    except ValueError:
        raise RuntimeError(f"adminChannelId invalido en bot-config.json: {raw}")


def team_updates_channel_id() -> Optional[int]:
    raw = str(CONFIG.get("teamUpdatesChannelId", "")).strip()
    if not raw:
        return None
    try:
        return int(raw)
    except ValueError:
        raise RuntimeError(f"teamUpdatesChannelId invalido en bot-config.json: {raw}")


def resolve_target_guilds() -> List[discord.Guild]:
    configured_guild_id = target_guild_id()
    if configured_guild_id is None:
        return list(client.guilds)

    guild = client.get_guild(configured_guild_id)
    if guild is None:
        raise RuntimeError(
            "El bot no encuentra el servidor configurado "
            f"({configured_guild_id}). Revisa si esta invitado a ese server y si el ID es correcto."
        )
    return [guild]


async def resolve_bot_member(guild: discord.Guild) -> Optional[discord.Member]:
    if client.user is None:
        return None
    member = guild.me or guild.get_member(client.user.id)
    if member is not None:
        return member
    try:
        return await guild.fetch_member(client.user.id)
    except Exception:
        return None


async def get_messageable_channel(channel_id: Optional[int]) -> Optional[discord.abc.Messageable]:
    if channel_id is None:
        return None

    channel = client.get_channel(channel_id)
    if channel is not None:
        return channel

    try:
        fetched = await client.fetch_channel(channel_id)
    except Exception:
        return None
    return fetched


async def get_admin_channel() -> Optional[discord.abc.Messageable]:
    return await get_messageable_channel(admin_channel_id())


async def get_team_updates_channel() -> Optional[discord.abc.Messageable]:
    return await get_messageable_channel(team_updates_channel_id())


async def notify_admin(message: str) -> None:
    if not message:
        return
    channel = await get_admin_channel()
    if channel is None:
        return
    try:
        await channel.send(message[:1900])
    except Exception:
        return


async def notify_admin_error(scope: str, exc: Exception) -> None:
    message = str(exc).strip() or exc.__class__.__name__
    cooldown_seconds = max(
        60,
        int(CONFIG.get("errorNotifyCooldownSeconds", DEFAULT_ERROR_NOTIFY_COOLDOWN_SECONDS)),
    )
    key = f"{scope}:{message}"
    if should_notify_error(key, cooldown_seconds=cooldown_seconds):
        await notify_admin(f"[SemmieBOT][{scope}][error] {message}")


async def process_registration_verification_jobs() -> None:
    jobs = await asyncio.to_thread(
        lambda: supabase.select(
            "discord_role_jobs",
            {"job_type": f"eq.{REGISTRATION_VERIFICATION_JOB_TYPE}"},
            order="created_at.asc",
            limit=5000,
        )
    )
    if not jobs:
        return

    now = datetime.now(timezone.utc)
    for job in jobs:
        if not isinstance(job, dict):
            continue
        status = str(job.get("status", "")).strip().lower()
        if status not in {"pending", "sent", "delivery_failed"}:
            continue

        payload = job.get("payload")
        if not isinstance(payload, dict):
            continue

        expires_at = parse_iso_datetime(payload.get("expires_at"))
        if expires_at is None or now >= expires_at:
            updated_payload = dict(payload)
            updated_payload["delivery_status"] = "expired"
            await asyncio.to_thread(
                supabase.update,
                "discord_role_jobs",
                {
                    "status": "expired",
                    "processed_at": utc_now(),
                    "payload": updated_payload,
                },
                {"id": f"eq.{str(job.get('id', '')).strip()}"},
            )
            continue

        if status != "pending":
            continue

        discord_user_id = str(payload.get("discord_user_id", "")).strip()
        verification_code = str(payload.get("verification_code", "")).strip()
        minecraft_username = str(payload.get("minecraft_username", "")).strip()
        if not discord_user_id.isdigit() or not verification_code or not minecraft_username:
            updated_payload = dict(payload)
            updated_payload["delivery_status"] = "invalid_payload"
            await asyncio.to_thread(
                supabase.update,
                "discord_role_jobs",
                {
                    "status": "delivery_failed",
                    "processed_at": utc_now(),
                    "payload": updated_payload,
                },
                {"id": f"eq.{str(job.get('id', '')).strip()}"},
            )
            continue

        try:
            user = client.get_user(int(discord_user_id)) or await client.fetch_user(int(discord_user_id))
            embed = discord.Embed(
                title="🔐 Verificacion de Semencraft",
                description=(
                    f"Tu codigo para completar el registro de **{minecraft_username}** es:\n\n"
                    f"## `{verification_code}`"
                ),
                colour=discord.Colour.from_rgb(104, 156, 255),
                timestamp=discord.utils.utcnow(),
            )
            embed.add_field(
                name="⏳ Tiempo limite",
                value=f"Este codigo vence en {REGISTRATION_VERIFICATION_TTL_MINUTES} minutos.",
                inline=False,
            )
            embed.add_field(
                name="📌 Que hacer ahora",
                value="Vuelve al launcher, coloca el codigo y termina tu registro.",
                inline=False,
            )
            embed.set_footer(text="Semencraft Launcher")
            await user.send(embed=embed)

            updated_payload = dict(payload)
            updated_payload["delivery_status"] = "sent"
            updated_payload["sent_at"] = utc_now()
            await asyncio.to_thread(
                supabase.update,
                "discord_role_jobs",
                {
                    "status": "sent",
                    "processed_at": utc_now(),
                    "payload": updated_payload,
                },
                {"id": f"eq.{str(job.get('id', '')).strip()}"},
            )
        except Exception as exc:
            updated_payload = dict(payload)
            updated_payload["delivery_status"] = "delivery_failed"
            updated_payload["last_error"] = str(exc)
            await asyncio.to_thread(
                supabase.update,
                "discord_role_jobs",
                {
                    "status": "delivery_failed",
                    "processed_at": utc_now(),
                    "payload": updated_payload,
                },
                {"id": f"eq.{str(job.get('id', '')).strip()}"},
            )
            await notify_admin_error("registration_dm", exc)


def build_team_update_embed(team: TeamDocument, title: str, description: str, changes: List[str]) -> discord.Embed:
    color = discord_color_from_hex(team.representative_color)
    embed = discord.Embed(
        title=title,
        description=description,
        colour=color,
        timestamp=discord.utils.utcnow(),
    )
    embed.add_field(name="Resumen", value=team_summary_line(team), inline=True)
    embed.add_field(name="Integrantes", value=format_member_list(team)[:1024], inline=True)
    if changes:
        embed.add_field(name="Cambios", value="\n".join(f"• {line}" for line in changes)[:1024], inline=False)
    icon_url = team_icon_url(team)
    if icon_url:
        embed.set_thumbnail(url=icon_url)
    embed.set_footer(text="Semencraft Teams")
    return embed


async def notify_team_update(team: TeamDocument, previous_snapshot: dict, reason: str) -> None:
    channel = await get_team_updates_channel()
    if channel is None:
        return
    if reason == "startup":
        return

    current_snapshot = normalize_team_snapshot(team)
    previous_snapshot = normalize_saved_team_snapshot(previous_snapshot)
    if team_resources_snapshot(previous_snapshot) == team_resources_snapshot(current_snapshot):
        return
    previous_members = {
        str(item.get("minecraftUsernameNormalized", "")): item
        for item in previous_snapshot.get("members", [])
        if isinstance(item, dict)
    }
    current_members = {
        str(item.get("minecraftUsernameNormalized", "")): item
        for item in current_snapshot.get("members", [])
        if isinstance(item, dict)
    }

    joined = [current_members[key] for key in current_members.keys() - previous_members.keys()]
    left = [previous_members[key] for key in previous_members.keys() - current_members.keys()]
    changes: List[str] = []

    if previous_snapshot.get("teamName") and previous_snapshot.get("teamName") != current_snapshot["teamName"]:
        changes.append(f"Nombre: `{previous_snapshot.get('teamName')}` -> `{current_snapshot['teamName']}`")
    if previous_snapshot.get("representativeColor") and previous_snapshot.get("representativeColor") != current_snapshot["representativeColor"]:
        changes.append(
            f"Color: `{previous_snapshot.get('representativeColor')}` -> `{current_snapshot['representativeColor']}`"
        )
    if previous_snapshot.get("founderMinecraftUsername") and previous_snapshot.get("founderMinecraftUsername") != current_snapshot["founderMinecraftUsername"]:
        changes.append(
            f"Lider: `{previous_snapshot.get('founderMinecraftUsername')}` -> `{current_snapshot['founderMinecraftUsername']}`"
        )
    if previous_snapshot.get("iconPath") and previous_snapshot.get("iconPath") != current_snapshot["iconPath"]:
        changes.append("Icono del equipo actualizado.")
    if joined:
        joined_lines = ", ".join(
            format_member_reference(
                str(item.get("minecraftUsername", "")),
                str(item.get("discordUserId", "")),
                founder=str(item.get("role", "")) == "founder",
            )
            for item in joined
        )
        changes.append(f"Nuevos miembros: {joined_lines}")
    if left:
        left_lines = ", ".join(
            format_member_reference(
                str(item.get("minecraftUsername", "")),
                str(item.get("discordUserId", "")),
                founder=str(item.get("role", "")) == "founder",
            )
            for item in left
        )
        changes.append(f"Salieron: {left_lines}")

    title = "🛡️ Equipo actualizado"
    description = f"El equipo **{team.team_name}** recibio cambios."

    if not previous_snapshot:
        title = "✨ Nuevo equipo registrado"
        description = f"Se creo el equipo **{team.team_name}**."
    elif current_snapshot["archived"] and not previous_snapshot.get("archived", False):
        title = "🗑️ Equipo archivado"
        description = f"El equipo **{team.team_name}** fue archivado."
    elif joined and not left and len(changes) == 1:
        title = "📥 Nuevo integrante"
        description = f"El equipo **{team.team_name}** sumo miembro(s)."
    elif left and not joined and len(changes) == 1:
        title = "📤 Movimiento de integrantes"
        description = f"El equipo **{team.team_name}** perdio miembro(s)."

    embed = build_team_update_embed(team, title, description, changes)
    try:
        await channel.send(embed=embed)
    except Exception:
        return


async def fetch_all_members(guild: discord.Guild) -> List[discord.Member]:
    members: List[discord.Member] = []
    async for member in guild.fetch_members(limit=None):
        if not member.bot:
            members.append(member)
    return members


async def load_members_for_guild(guild: discord.Guild) -> List[discord.Member]:
    print(f"[sync] Cargando miembros de {guild.name} ({guild.id})")
    cached_members = [member for member in guild.members if not member.bot]
    if cached_members:
        print(f"[sync] Cache inicial disponible: {len(cached_members)} miembro(s)")

    try:
        fetched_members = await asyncio.wait_for(fetch_all_members(guild), timeout=45)
        print(f"[sync] Miembros obtenidos por API: {len(fetched_members)}")
        return fetched_members
    except Exception as exc:
        print(f"[sync][warn] No se pudo cargar miembros por API: {exc}")
        key = f"members:{guild.id}:{type(exc).__name__}:{str(exc).strip()}"
        if should_notify_error(key, cooldown_seconds=1800):
            await notify_admin(
                f"[SemmieBOT] No se pudo cargar la lista completa de miembros de {guild.name}: {exc}\n"
                f"Revisa que el bot tenga activado Server Members Intent."
            )
        return cached_members


async def collect_directory_payload() -> tuple[dict, Dict[int, discord.Member]]:
    users_by_id: Dict[int, dict] = {}
    guild_payload = []
    members_by_id: Dict[int, discord.Member] = {}

    target_guilds = resolve_target_guilds()
    for guild in target_guilds:
        real_members = await load_members_for_guild(guild)
        guild_payload.append(
            {
                "id": str(guild.id),
                "name": guild.name,
                "memberCount": len(real_members),
            }
        )

        for member in real_members:
            members_by_id[member.id] = member
            users_by_id[member.id] = {
                "id": str(member.id),
                "username": member.name,
                "globalName": member.global_name or "",
                "displayName": member.display_name or "",
                "lookupKeys": build_lookup_keys(member),
                "guildId": str(guild.id),
                "guildName": guild.name,
                "isBot": False,
            }

    users = sorted(users_by_id.values(), key=lambda item: (item["username"].lower(), item["id"]))
    payload = {
        "schemaVersion": 1,
        "updatedAt": utc_now(),
        "guilds": guild_payload,
        "users": users,
    }
    return payload, members_by_id


def stable_directory_payload(payload: dict) -> dict:
    return {
        "schemaVersion": payload.get("schemaVersion", 1),
        "guilds": payload.get("guilds", []),
        "users": payload.get("users", []),
    }


def read_directory_signature(directory: str) -> Dict[str, str]:
    if directory == USERS_DIR:
        accounts = load_registered_accounts()
        return {
            account.minecraft_username_normalized: f"{account.discord_user_id}"
            for account in accounts.values()
        }
    if directory == TEAMS_DIR:
        teams = load_team_documents()
        return {
            team.team_id: json.dumps(
                {
                    "name": team.team_name,
                    "color": team.representative_color,
                    "archived": team.archived,
                    "members": [
                        (member.minecraft_username_normalized, member.role)
                        for member in team.members
                    ],
                },
                sort_keys=True,
                ensure_ascii=False,
            )
            for team in teams
        }
    return {}


def collect_cached_members() -> Dict[int, discord.Member]:
    members_by_id: Dict[int, discord.Member] = {}
    for guild in resolve_target_guilds():
        for member in guild.members:
            if not member.bot:
                members_by_id[member.id] = member
    return members_by_id


async def sync_discord_directory() -> Dict[int, discord.Member]:
    payload, members_by_id = await collect_directory_payload()
    guild_ids = {str(entry.get("id", "")).strip() for entry in payload.get("guilds", []) if entry.get("id")}
    existing_rows = await asyncio.to_thread(
        supabase.select,
        "discord_members_cache",
        {},
        order="discord_username.asc",
        limit=5000,
    )
    existing_payload = {
        "schemaVersion": 1,
        "guilds": payload.get("guilds", []),
        "users": sorted(
            [
                {
                    "id": str(row.get("discord_user_id", "")).strip(),
                    "username": str(row.get("discord_username", "")).strip(),
                    "globalName": str(row.get("discord_global_name", "") or "").strip(),
                    "displayName": str(row.get("discord_display_name", "") or "").strip(),
                    "lookupKeys": row.get("lookup_keys", []),
                    "guildId": str(row.get("guild_id", "")).strip(),
                    "guildName": str(row.get("guild_name", "")).strip(),
                    "isBot": bool(row.get("is_bot", False)),
                }
                for row in existing_rows
                if str(row.get("guild_id", "")).strip() in guild_ids
            ],
            key=lambda item: (item["username"].lower(), item["id"]),
        ),
    }
    serialized = json.dumps(stable_directory_payload(payload), indent=2, sort_keys=True)
    existing_serialized = json.dumps(stable_directory_payload(existing_payload), indent=2, sort_keys=True)
    if serialized == existing_serialized:
        print("[sync] discord_members_cache no tuvo cambios")
        return members_by_id

    desired_users = payload.get("users", [])
    for user in desired_users:
        row = {
            "discord_user_id": str(user.get("id", "")).strip(),
            "discord_username": str(user.get("username", "")).strip(),
            "discord_global_name": str(user.get("globalName", "") or "").strip(),
            "discord_display_name": str(user.get("displayName", "") or "").strip(),
            "lookup_keys": user.get("lookupKeys", []),
            "guild_id": str(user.get("guildId", "")).strip(),
            "guild_name": str(user.get("guildName", "")).strip(),
            "is_bot": False,
            "updated_at": utc_now(),
        }
        await asyncio.to_thread(
            supabase.upsert,
            "discord_members_cache",
            row,
            on_conflict="discord_user_id",
        )

    current_ids = {
        str(user.get("id", "")).strip()
        for user in desired_users
        if str(user.get("id", "")).strip()
    }
    for row in existing_rows:
        discord_user_id = str(row.get("discord_user_id", "")).strip()
        guild_id = str(row.get("guild_id", "")).strip()
        if guild_id in guild_ids and discord_user_id and discord_user_id not in current_ids:
            await asyncio.to_thread(
                supabase.delete,
                "discord_members_cache",
                {"discord_user_id": f"eq.{discord_user_id}"},
            )
    return members_by_id


def parse_json_document(content: str) -> Optional[dict]:
    try:
        data = json.loads(content)
    except json.JSONDecodeError:
        return None
    return data if isinstance(data, dict) else None


def list_repo_json_files(directory: str) -> List[str]:
    paths: List[str] = []
    for entry in github.list_directory(directory):
        path = entry.get("path", "")
        if not path.endswith(".json") or path.endswith("_placeholder.json"):
            continue
        paths.append(path)
    return paths


def load_registered_accounts() -> Dict[str, RegisteredAccount]:
    accounts: Dict[str, RegisteredAccount] = {}
    for row in supabase.select(
        "app_users",
        {"is_registered": "eq.true"},
        order="minecraft_username.asc",
        limit=5000,
    ):
        minecraft_username = str(row.get("minecraft_username", "")).strip()
        normalized_username = normalize_minecraft_username(
            row.get("minecraft_username_normalized") or minecraft_username
        )
        discord_user_id = str(row.get("discord_user_id", "")).strip()
        if not normalized_username or not discord_user_id:
            continue

        accounts[normalized_username] = RegisteredAccount(
            minecraft_username=minecraft_username or normalized_username,
            minecraft_username_normalized=normalized_username,
            discord_user_id=discord_user_id,
        )
    return accounts


def parse_team_document(repo_file: RepoFile) -> Optional[TeamDocument]:
    payload = parse_json_document(repo_file.content)
    if payload is None:
        return None
    return None


def load_team_documents() -> List[TeamDocument]:
    users_by_id = {
        str(row.get("id", "")).strip(): row
        for row in supabase.select("app_users", {}, order="minecraft_username.asc", limit=5000)
    }
    members_by_team_id: Dict[str, List[TeamMemberRecord]] = {}
    for row in supabase.select("team_members", {}, order="joined_at.asc", limit=5000):
        team_id = str(row.get("team_id", "")).strip()
        user_id = str(row.get("user_id", "")).strip()
        user = users_by_id.get(user_id)
        minecraft_username = str((user or {}).get("minecraft_username", "")).strip()
        normalized_username = normalize_minecraft_username(
            (user or {}).get("minecraft_username_normalized") or minecraft_username
        )
        if not team_id or not normalized_username:
            continue
        members_by_team_id.setdefault(team_id, []).append(
            TeamMemberRecord(
                minecraft_username=minecraft_username or normalized_username,
                minecraft_username_normalized=normalized_username,
                discord_user_id=str((user or {}).get("discord_user_id", "")).strip(),
                role=str(row.get("member_role", "member")).strip().lower() or "member",
            )
        )

    documents: List[TeamDocument] = []
    for row in supabase.select("teams", {}, order="created_at.asc", limit=5000):
        team_id = str(row.get("id", "")).strip()
        team_name = str(row.get("team_name", "")).strip()
        if not team_id or not team_name:
            continue
        founder_user = users_by_id.get(str(row.get("founder_user_id", "")).strip(), {})
        members = members_by_team_id.get(team_id, [])
        archived = bool(row.get("archived", False)) or not members
        state_payload = get_team_state(team_id)
        discord_resources = state_payload.get("discordResources")
        if not isinstance(discord_resources, dict):
            discord_resources = {}
        documents.append(
            TeamDocument(
                path=f"teams/{team_id}.json",
                sha=None,
                payload={"discordResources": discord_resources},
                team_id=team_id,
                team_name=team_name,
                representative_color=normalize_hex_color(row.get("representative_color")),
                icon_path=str(row.get("icon_path", "")).strip(),
                founder_minecraft_username=str((founder_user or {}).get("minecraft_username", "")).strip(),
                founder_discord_user_id=str((founder_user or {}).get("discord_user_id", "")).strip(),
                archived=archived,
                members=members,
            )
        )
    return documents


def permission_overwrite_signature(mapping: Dict[object, discord.PermissionOverwrite]) -> List[tuple[int, int, int]]:
    signature: List[tuple[int, int, int]] = []
    for target, overwrite in mapping.items():
        allow, deny = overwrite.pair()
        signature.append((int(getattr(target, "id", 0)), allow.value, deny.value))
    signature.sort()
    return signature


def permission_overwrites_match(
    existing: Dict[object, discord.PermissionOverwrite],
    desired: Dict[object, discord.PermissionOverwrite],
) -> bool:
    return permission_overwrite_signature(existing) == permission_overwrite_signature(desired)


def build_team_overwrites(
    guild: discord.Guild,
    role: discord.Role,
    bot_member: Optional[discord.Member],
) -> Dict[object, discord.PermissionOverwrite]:
    overwrites: Dict[object, discord.PermissionOverwrite] = {
        guild.default_role: discord.PermissionOverwrite(
            view_channel=False,
            send_messages=False,
            read_message_history=False,
            connect=False,
            speak=False,
        ),
        role: discord.PermissionOverwrite(
            view_channel=True,
            send_messages=True,
            read_message_history=True,
            attach_files=True,
            embed_links=True,
            connect=True,
            speak=True,
            stream=True,
            use_voice_activation=True,
        ),
    }
    if bot_member is not None:
        overwrites[bot_member] = discord.PermissionOverwrite(
            view_channel=True,
            send_messages=True,
            read_message_history=True,
            manage_channels=True,
            manage_permissions=True,
            connect=True,
            speak=True,
            stream=True,
        )
    return overwrites


def resolve_team_role(
    guild: discord.Guild,
    resources: dict,
    team_name: str,
    desired_color: discord.Colour,
    team_members: List[discord.Member],
) -> Optional[discord.Role]:
    role_id = str(resources.get("roleId", "")).strip()
    if role_id.isdigit():
        role = guild.get_role(int(role_id))
        if role is not None:
            return role

    stored_role_name = str(resources.get("roleName", "")).strip()
    registered_role_name = str(CONFIG.get("roleName", "Registrado")).strip()
    candidate_scores: Dict[int, int] = {}
    candidates: Dict[int, discord.Role] = {}

    def add_candidate(role: discord.Role, score: int) -> None:
        if role.is_default() or role.managed or role.name == registered_role_name:
            return
        candidates[role.id] = role
        candidate_scores[role.id] = candidate_scores.get(role.id, 0) + score

    for role in guild.roles:
        if role.name == team_name:
            add_candidate(role, 180)
        elif stored_role_name and role.name == stored_role_name:
            add_candidate(role, 120)
        if role.colour.value == desired_color.value:
            add_candidate(role, 20)
        if role.hoist:
            add_candidate(role, 5)

    for member in team_members:
        for role in member.roles:
            if role.is_default() or role.managed or role.name == registered_role_name:
                continue
            if (
                role.colour.value == desired_color.value
                or role.name == team_name
                or (stored_role_name and role.name == stored_role_name)
            ):
                add_candidate(role, 240)

    if not candidates:
        return None

    ranked = sorted(
        candidates.values(),
        key=lambda role: (
            -candidate_scores.get(role.id, 0),
            -(1 if role.hoist else 0),
            -role.position,
            role.id,
        ),
    )
    best = ranked[0]
    return best if candidate_scores.get(best.id, 0) >= 120 else None


def category_grants_role_access(category: discord.CategoryChannel, role: Optional[discord.Role]) -> bool:
    if role is None:
        return False
    for target, overwrite in category.overwrites.items():
        if getattr(target, "id", None) == role.id:
            return overwrite.view_channel is True
    return False


def count_expected_team_channels(category: discord.CategoryChannel) -> int:
    score = 0
    expected_text_name = normalize_text_channel_name(TEAM_TEXT_CHANNEL_NAME)
    if any(normalize_text_channel_name(channel.name) == expected_text_name for channel in category.text_channels):
        score += 1
    if any(channel.name == TEAM_VOICE_CHANNEL_NAME for channel in category.voice_channels):
        score += 1
    return score


def resolve_team_category(
    guild: discord.Guild,
    resources: dict,
    team_name: str,
    role: Optional[discord.Role],
) -> Optional[discord.CategoryChannel]:
    category_id = str(resources.get("categoryId", "")).strip()
    if category_id.isdigit():
        channel = guild.get_channel(int(category_id))
        if isinstance(channel, discord.CategoryChannel):
            return channel

    stored_category_name = str(resources.get("categoryName", "")).strip()
    candidates: List[discord.CategoryChannel] = []

    for category in guild.categories:
        if category.name == team_name:
            candidates.append(category)
            continue
        if stored_category_name and category.name == stored_category_name:
            candidates.append(category)
            continue
        if category_grants_role_access(category, role):
            candidates.append(category)

    if not candidates:
        return None

    def category_score(category: discord.CategoryChannel) -> tuple[int, int, int]:
        score = 0
        if category_grants_role_access(category, role):
            score += 300
        if category.name == team_name:
            score += 120
        if stored_category_name and category.name == stored_category_name:
            score += 80
        score += count_expected_team_channels(category) * 40
        score += min(len(category.channels), 5)
        return (-score, category.id, len(category.channels))

    ranked = sorted(candidates, key=category_score)
    return ranked[0]


def find_duplicate_categories(
    guild: discord.Guild,
    resources: dict,
    team_name: str,
    role: Optional[discord.Role],
    primary_category: Optional[discord.CategoryChannel],
) -> List[discord.CategoryChannel]:
    if primary_category is None:
        return []
    stored_category_name = str(resources.get("categoryName", "")).strip()
    duplicates: List[discord.CategoryChannel] = []
    for category in guild.categories:
        if category.id == primary_category.id:
            continue
        if category.name == team_name or (stored_category_name and category.name == stored_category_name):
            duplicates.append(category)
            continue
        if category_grants_role_access(category, role):
            duplicates.append(category)
    duplicates.sort(key=lambda item: item.id, reverse=True)
    return duplicates


def find_duplicate_team_roles(
    team_members: List[discord.Member],
    primary_role: discord.Role,
    desired_color: discord.Colour,
) -> List[discord.Role]:
    registered_role_name = str(CONFIG.get("roleName", "Registrado")).strip()
    duplicates: Dict[int, discord.Role] = {}
    for member in team_members:
        for role in member.roles:
            if (
                role.id == primary_role.id
                or role.is_default()
                or role.managed
                or role.name == registered_role_name
            ):
                continue
            if role.colour.value == desired_color.value:
                duplicates[role.id] = role
    return sorted(duplicates.values(), key=lambda item: item.position, reverse=True)


def resolve_text_channel(
    guild: discord.Guild,
    resources: dict,
    category: Optional[discord.CategoryChannel],
) -> Optional[discord.TextChannel]:
    channel_id = str(resources.get("textChannelId", "")).strip()
    if channel_id.isdigit():
        channel = guild.get_channel(int(channel_id))
        if isinstance(channel, discord.TextChannel):
            return channel
    expected_name = normalize_text_channel_name(TEAM_TEXT_CHANNEL_NAME)
    if category is not None:
        for channel in sorted(category.text_channels, key=lambda item: item.id):
            if normalize_text_channel_name(channel.name) == expected_name:
                return channel
    return None


def resolve_voice_channel(
    guild: discord.Guild,
    resources: dict,
    category: Optional[discord.CategoryChannel],
) -> Optional[discord.VoiceChannel]:
    channel_id = str(resources.get("voiceChannelId", "")).strip()
    if channel_id.isdigit():
        channel = guild.get_channel(int(channel_id))
        if isinstance(channel, discord.VoiceChannel):
            return channel
    if category is not None:
        for channel in category.voice_channels:
            if channel.name == TEAM_VOICE_CHANNEL_NAME:
                return channel
    return None


def find_duplicate_text_channels(category: Optional[discord.CategoryChannel]) -> List[discord.TextChannel]:
    if category is None:
        return []
    expected_name = normalize_text_channel_name(TEAM_TEXT_CHANNEL_NAME)
    matches = [
        channel
        for channel in sorted(category.text_channels, key=lambda item: item.id)
        if normalize_text_channel_name(channel.name) == expected_name
    ]
    return matches[1:] if len(matches) > 1 else []


def team_resources_snapshot(payload: dict) -> str:
    return json.dumps(payload, sort_keys=True, ensure_ascii=False)


def persist_team_metadata(team: TeamDocument, message: str) -> None:
    set_team_state(
        team.team_id,
        {
            "teamId": team.team_id,
            "teamName": team.team_name,
            "representativeColor": normalize_hex_color(team.representative_color),
            "iconPath": team.icon_path or "",
            "founderMinecraftUsername": team.founder_minecraft_username or "",
            "founderDiscordUserId": team.founder_discord_user_id or "",
            "archived": bool(team.archived),
            "members": normalize_team_snapshot(team).get("members", []),
            "discordResources": team.payload.get("discordResources", {}),
            "updatedAt": utc_now(),
            "message": message,
        },
    )


async def ensure_team_resources(
    guild: discord.Guild,
    bot_member: discord.Member,
    team: TeamDocument,
    team_members: List[discord.Member],
) -> tuple[discord.Role, List[str]]:
    if not bot_member.guild_permissions.manage_roles:
        raise RuntimeError("El bot necesita 'Manage Roles' para sincronizar los roles de equipos.")
    if not bot_member.guild_permissions.manage_channels:
        raise RuntimeError("El bot necesita 'Manage Channels' para crear y actualizar canales privados de equipos.")

    resources = team.discord_resources
    created_parts: List[str] = []

    desired_color = discord_color_from_hex(team.representative_color)
    role = resolve_team_role(guild, resources, team.team_name, desired_color, team_members)
    if role is None:
        role = await guild.create_role(
            name=team.team_name,
            colour=desired_color,
            hoist=True,
            mentionable=False,
            reason=f"Crear rol para equipo {team.team_name}",
        )
        created_parts.append("rol")
    else:
        edits = {}
        if role.name != team.team_name:
            edits["name"] = team.team_name
        if role.colour.value != desired_color.value:
            edits["colour"] = desired_color
        if not role.hoist:
            edits["hoist"] = True
        if role.mentionable:
            edits["mentionable"] = False
        if edits:
            await role.edit(reason=f"Actualizar rol del equipo {team.team_name}", **edits)

    if bot_member.top_role <= role:
        raise RuntimeError(
            f"El rol del bot debe estar por encima del rol del equipo '{role.name}' para poder administrarlo."
        )

    target_position = max(1, bot_member.top_role.position - 1)
    if role.position < target_position:
        await role.edit(position=target_position, reason=f"Subir rol del equipo {team.team_name}")

    overwrites = build_team_overwrites(guild, role, bot_member)

    category = resolve_team_category(guild, resources, team.team_name, role)
    if category is None:
        category = await guild.create_category(
            team.team_name,
            overwrites=overwrites,
            reason=f"Crear categoria privada para equipo {team.team_name}",
        )
        created_parts.append("categoria")
    elif category.name != team.team_name or not permission_overwrites_match(category.overwrites, overwrites):
        await category.edit(
            name=team.team_name,
            overwrites=overwrites,
            reason=f"Actualizar categoria privada de {team.team_name}",
        )

    duplicate_categories = find_duplicate_categories(guild, resources, team.team_name, role, category)
    for duplicate_category in duplicate_categories:
        try:
            for child in list(duplicate_category.channels):
                await child.delete(reason=f"Eliminar categoria duplicada del equipo {team.team_name}")
            await duplicate_category.delete(reason=f"Eliminar categoria duplicada del equipo {team.team_name}")
            created_parts.append("categoria_duplicada_eliminada")
        except discord.HTTPException as exc:
            raise RuntimeError(
                f"No se pudo eliminar la categoria duplicada '{duplicate_category.name}' de {team.team_name}: {exc}"
            ) from exc

    text_channel = resolve_text_channel(guild, resources, category)
    if text_channel is None:
        text_channel = await guild.create_text_channel(
            normalize_text_channel_name(TEAM_TEXT_CHANNEL_NAME),
            category=category,
            overwrites=overwrites,
            reason=f"Crear chat privado para equipo {team.team_name}",
        )
        created_parts.append("chat")
    elif (
        normalize_text_channel_name(text_channel.name) != normalize_text_channel_name(TEAM_TEXT_CHANNEL_NAME)
        or text_channel.category_id != category.id
        or not permission_overwrites_match(text_channel.overwrites, overwrites)
    ):
        await text_channel.edit(
            name=normalize_text_channel_name(TEAM_TEXT_CHANNEL_NAME),
            category=category,
            overwrites=overwrites,
            reason=f"Actualizar chat privado de {team.team_name}",
        )

    duplicate_text_channels = find_duplicate_text_channels(category)
    for duplicate in duplicate_text_channels:
        try:
            await duplicate.delete(reason=f"Eliminar chat duplicado del equipo {team.team_name}")
            created_parts.append("chat_duplicado_eliminado")
        except discord.HTTPException as exc:
            raise RuntimeError(f"No se pudo eliminar el chat duplicado '{duplicate.name}' de {team.team_name}: {exc}") from exc

    voice_channel = resolve_voice_channel(guild, resources, category)
    if voice_channel is None:
        voice_channel = await guild.create_voice_channel(
            TEAM_VOICE_CHANNEL_NAME,
            category=category,
            overwrites=overwrites,
            reason=f"Crear canal de voz privado para equipo {team.team_name}",
        )
        created_parts.append("voz")
    elif (
        voice_channel.name != TEAM_VOICE_CHANNEL_NAME
        or voice_channel.category_id != category.id
        or not permission_overwrites_match(voice_channel.overwrites, overwrites)
    ):
        await voice_channel.edit(
            name=TEAM_VOICE_CHANNEL_NAME,
            category=category,
            overwrites=overwrites,
            reason=f"Actualizar canal de voz privado de {team.team_name}",
        )

    desired_resources = {
        "status": "active",
        "guildId": str(guild.id),
        "guildName": guild.name,
        "roleId": str(role.id),
        "roleName": role.name,
        "roleColorHex": normalize_hex_color(team.representative_color),
        "categoryId": str(category.id),
        "categoryName": category.name,
        "textChannelId": str(text_channel.id),
        "textChannelName": text_channel.name,
        "voiceChannelId": str(voice_channel.id),
        "voiceChannelName": voice_channel.name,
    }

    if team_resources_snapshot(resources) != team_resources_snapshot(desired_resources):
        team.payload["discordResources"] = desired_resources
        await persist_team_metadata_async(team, f"Sincronizar recursos Discord para equipo {team.team_name}")

    return role, created_parts


async def cleanup_team_resources(
    guild: discord.Guild,
    bot_member: discord.Member,
    team: TeamDocument,
) -> None:
    resources = team.discord_resources
    role = resolve_team_role(guild, resources, team.team_name, discord_color_from_hex(team.representative_color), [])
    category = resolve_team_category(guild, resources, team.team_name, role)
    text_channel = resolve_text_channel(guild, resources, category)
    voice_channel = resolve_voice_channel(guild, resources, category)

    deleted_parts: List[str] = []

    if category is not None:
        for child in list(category.channels):
            try:
                await child.delete(reason=f"Eliminar canales del equipo archivado {team.team_name}")
                deleted_parts.append(f"canal:{child.name}")
            except discord.HTTPException as exc:
                raise RuntimeError(f"No se pudo eliminar el canal '{child.name}' de {team.team_name}: {exc}") from exc
        try:
            await category.delete(reason=f"Eliminar categoria del equipo archivado {team.team_name}")
            deleted_parts.append("categoria")
        except discord.HTTPException as exc:
            raise RuntimeError(f"No se pudo eliminar la categoria de {team.team_name}: {exc}") from exc
        text_channel = None
        voice_channel = None
    else:
        if text_channel is not None:
            try:
                await text_channel.delete(reason=f"Eliminar chat del equipo archivado {team.team_name}")
                deleted_parts.append("chat")
            except discord.HTTPException as exc:
                raise RuntimeError(f"No se pudo eliminar el chat de {team.team_name}: {exc}") from exc
        if voice_channel is not None:
            try:
                await voice_channel.delete(reason=f"Eliminar voz del equipo archivado {team.team_name}")
                deleted_parts.append("voz")
            except discord.HTTPException as exc:
                raise RuntimeError(f"No se pudo eliminar la voz de {team.team_name}: {exc}") from exc

    if role is not None:
        if bot_member.top_role <= role:
            raise RuntimeError(
                f"El rol del bot debe estar por encima del rol '{role.name}' para poder eliminarlo."
            )
        try:
            await role.delete(reason=f"Eliminar rol del equipo archivado {team.team_name}")
            deleted_parts.append("rol")
        except discord.HTTPException as exc:
            raise RuntimeError(f"No se pudo eliminar el rol de {team.team_name}: {exc}") from exc

    updated_resources = dict(resources)
    updated_resources.update(
        {
            "status": "deleted",
            "cleanupStatus": "deleted",
            "cleanedUpAt": utc_now(),
            "roleName": updated_resources.get("roleName") or team.team_name,
            "roleColorHex": normalize_hex_color(team.representative_color),
            "categoryName": updated_resources.get("categoryName") or team.team_name,
            "textChannelName": updated_resources.get("textChannelName") or TEAM_TEXT_CHANNEL_NAME,
            "voiceChannelName": updated_resources.get("voiceChannelName") or TEAM_VOICE_CHANNEL_NAME,
        }
    )

    if deleted_parts or team_resources_snapshot(resources) != team_resources_snapshot(updated_resources):
        team.payload["discordResources"] = updated_resources
        await persist_team_metadata_async(team, f"Marcar limpieza Discord para equipo {team.team_name}")

    if deleted_parts:
        await notify_admin(
            f"[SemmieBOT][team] Recursos eliminados para '{team.team_name}': {', '.join(deleted_parts)}."
        )


async def cleanup_stale_team_roles(
    guild: discord.Guild,
    bot_member: discord.Member,
    stale_roles: List[discord.Role],
) -> None:
    seen: set[int] = set()
    for role in stale_roles:
        if role.id in seen:
            continue
        seen.add(role.id)

        members_using_role = [member for member in guild.members if not member.bot and role in member.roles]
        if members_using_role:
            continue

        duplicate_categories = [category for category in guild.categories if category_grants_role_access(category, role)]
        for category in duplicate_categories:
            try:
                for child in list(category.channels):
                    await child.delete(reason=f"Eliminar recursos huerfanos del rol duplicado {role.name}")
                await category.delete(reason=f"Eliminar categoria huerfana del rol duplicado {role.name}")
            except discord.HTTPException as exc:
                raise RuntimeError(
                    f"No se pudo eliminar la categoria huerfana '{category.name}' del rol duplicado {role.name}: {exc}"
                ) from exc

        if bot_member.top_role <= role:
            continue
        try:
            await role.delete(reason=f"Eliminar rol duplicado del equipo {role.name}")
        except discord.HTTPException as exc:
            raise RuntimeError(f"No se pudo eliminar el rol duplicado '{role.name}': {exc}") from exc


async def reconcile_team_roles(
    members_by_id: Dict[int, discord.Member],
    desired_role_by_member_id: Dict[int, discord.Role],
    managed_team_role_ids: set[int],
) -> None:
    for member_id, member in members_by_id.items():
        desired_role = desired_role_by_member_id.get(member_id)
        removable_roles = [role for role in member.roles if role.id in managed_team_role_ids and role != desired_role]

        if removable_roles:
            try:
                await member.remove_roles(*removable_roles, reason="Sincronizacion de equipos de Semencraft")
            except discord.Forbidden as exc:
                raise RuntimeError(
                    f"No pude quitar roles de equipo a {member.display_name}. Revisa la jerarquia del bot."
                ) from exc
            except discord.HTTPException as exc:
                raise RuntimeError(f"Discord devolvio un error quitando roles de {member.display_name}: {exc}") from exc

        if desired_role is not None and desired_role not in member.roles:
            try:
                await member.add_roles(desired_role, reason="Sincronizacion de equipos de Semencraft")
            except discord.Forbidden as exc:
                raise RuntimeError(
                    f"No pude asignar el rol del equipo '{desired_role.name}' a {member.display_name}. "
                    "Revisa permisos y jerarquia."
                ) from exc
            except discord.HTTPException as exc:
                raise RuntimeError(
                    f"Discord devolvio un error asignando el rol '{desired_role.name}' a {member.display_name}: {exc}"
                ) from exc


async def process_team_resources(members_by_id: Dict[int, discord.Member], reason: str) -> None:
    target_guilds = resolve_target_guilds()
    if not target_guilds:
        return

    guild = target_guilds[0]
    bot_member = await resolve_bot_member(guild)
    if bot_member is None:
        raise RuntimeError("No se pudo resolver el miembro del bot dentro del servidor.")

    accounts, teams = await asyncio.gather(
        load_registered_accounts_async(),
        load_team_documents_async(),
    )

    managed_team_role_ids: set[int] = set()
    desired_role_by_member_id: Dict[int, discord.Role] = {}
    stale_team_roles: List[discord.Role] = []

    for team in teams:
        previous_state = get_team_state(team.team_id)
        if team.archived:
            await cleanup_team_resources(guild, bot_member, team)
            await notify_team_update(team, previous_state, reason)
            await persist_team_metadata_async(team, f"Estado archivado sincronizado para equipo {team.team_name}")
            continue

        team_guild_members: List[discord.Member] = []
        for team_member in team.members:
            account = accounts.get(team_member.minecraft_username_normalized)
            if account is None or not account.discord_user_id.isdigit():
                continue
            member = members_by_id.get(int(account.discord_user_id))
            if member is not None:
                team_guild_members.append(member)

        role, created_parts = await ensure_team_resources(guild, bot_member, team, team_guild_members)
        managed_team_role_ids.add(role.id)
        duplicate_roles = find_duplicate_team_roles(team_guild_members, role, desired_color=discord_color_from_hex(team.representative_color))
        for duplicate_role in duplicate_roles:
            managed_team_role_ids.add(duplicate_role.id)
            stale_team_roles.append(duplicate_role)

        for team_member in team.members:
            account = accounts.get(team_member.minecraft_username_normalized)
            if account is None:
                print(
                    "[team][warn] No encontre cuenta registrada para "
                    f"{team_member.minecraft_username_normalized} en el equipo {team.team_name}"
                )
                continue

            if not account.discord_user_id.isdigit():
                print(
                    "[team][warn] La cuenta "
                    f"{team_member.minecraft_username_normalized} no tiene un discordUserId valido"
                )
                continue

            discord_user_id = int(account.discord_user_id)
            member = members_by_id.get(discord_user_id)
            if member is None:
                print(
                    "[team][warn] El usuario de Discord "
                    f"{account.discord_user_id} no esta en el guild para el equipo {team.team_name}"
                )
                continue

            if discord_user_id in desired_role_by_member_id and desired_role_by_member_id[discord_user_id] != role:
                print(
                    "[team][warn] El usuario "
                    f"{member.display_name} aparece en mas de un equipo. Se respetara el primero detectado."
                )
                continue

            desired_role_by_member_id[discord_user_id] = role

        if created_parts:
            await notify_admin(
                f"[SemmieBOT][team] Recursos listos para '{team.team_name}': {', '.join(created_parts)}."
            )

        await notify_team_update(team, previous_state, reason)
        await persist_team_metadata_async(team, f"Estado del equipo sincronizado ({reason})")

    await reconcile_team_roles(members_by_id, desired_role_by_member_id, managed_team_role_ids)
    await cleanup_stale_team_roles(guild, bot_member, stale_team_roles)
    print("[sync] Equipos de Discord sincronizados")


async def run_action_sync_cycle(reason: str) -> None:
    global last_team_signature, last_user_signature

    async with sync_lock:
        await process_registration_verification_jobs()
        members = collect_cached_members()
        if not members:
            return

        await process_role_requests(members)

        current_user_signature, current_team_signature = await asyncio.gather(
            read_directory_signature_async(USERS_DIR),
            read_directory_signature_async(TEAMS_DIR),
        )
        if current_user_signature == last_user_signature and current_team_signature == last_team_signature:
            return

        print(f"[sync] Cambios de equipos detectados ({reason})")
        await process_team_resources(members, reason)
        last_user_signature, last_team_signature = await asyncio.gather(
            read_directory_signature_async(USERS_DIR),
            read_directory_signature_async(TEAMS_DIR),
        )


async def run_sync_cycle(reason: str) -> None:
    global last_team_signature, last_user_signature

    async with sync_lock:
        print(f"[sync] Iniciando sincronizacion ({reason})")
        members = await sync_discord_directory()
        print(f"[sync] discord_members_cache sincronizado con {len(members)} usuario(s)")
        await process_registration_verification_jobs()
        await process_role_requests(members)
        print(f"[sync] Solicitudes de rol revisadas")
        await process_team_resources(members, reason)
        last_user_signature, last_team_signature = await asyncio.gather(
            read_directory_signature_async(USERS_DIR),
            read_directory_signature_async(TEAMS_DIR),
        )


async def sync_registered_nickname(
    guild: discord.Guild,
    bot_member: discord.Member,
    member: discord.Member,
    desired_nickname: str,
) -> None:
    if not desired_nickname or member.bot:
        return
    if member.nick == desired_nickname:
        return
    if member.id == guild.owner_id:
        return
    if not bot_member.guild_permissions.manage_nicknames:
        await notify_admin_error(
            "nick",
            RuntimeError("Falta el permiso 'Manage Nicknames' para sincronizar nicks de registrados."),
        )
        return
    if bot_member.top_role <= member.top_role:
        await notify_admin_error(
            "nick",
            RuntimeError(
                f"No puedo cambiar el nick de {member.display_name} porque su rol mas alto esta por encima del bot."
            ),
        )
        return
    try:
        await member.edit(nick=desired_nickname, reason="Registro validado en Semencraft Launcher")
    except discord.Forbidden as exc:
        await notify_admin_error("nick", exc)
    except discord.HTTPException as exc:
        await notify_admin_error("nick", exc)


async def process_role_requests(members_by_id: Dict[int, discord.Member]) -> None:
    accounts = await load_registered_accounts_async()
    if not accounts:
        return

    target_guilds = resolve_target_guilds()
    if not target_guilds:
        return

    guild = target_guilds[0]
    bot_member = await resolve_bot_member(guild)
    if bot_member is None:
        await notify_admin("[SemmieBOT][role] No pude resolver el miembro del bot dentro del servidor.")
        return
    # El nickname se sincroniza independientemente de los roles de Discord.
    for account in accounts.values():
        if not account.discord_user_id.isdigit():
            continue
        member = members_by_id.get(int(account.discord_user_id))
        if member is not None:
            await sync_registered_nickname(guild, bot_member, member, account.minecraft_username)

    role = discord.utils.get(guild.roles, name=CONFIG.get("roleName", "Registrado"))
    if role is None:
        await notify_admin(
            f"[SemmieBOT][role] No existe el rol '{CONFIG.get('roleName', 'Registrado')}' en {guild.name}."
        )
        return

    if not bot_member.guild_permissions.manage_roles:
        await notify_admin(
            f"[SemmieBOT][role] Falta el permiso 'Manage Roles' para asignar '{role.name}'."
        )
        return
    if bot_member.top_role <= role:
        await notify_admin(
            f"[SemmieBOT][role] No puedo asignar '{role.name}' porque el rol mas alto del bot "
            f"({bot_member.top_role.name}) esta por debajo o al mismo nivel."
        )
        return

    for account in accounts.values():
        if not account.discord_user_id.isdigit():
            continue
        member = members_by_id.get(int(account.discord_user_id))
        if member is None:
            continue
        if role in member.roles:
            continue
        try:
            await member.add_roles(role, reason="Registro validado en Semencraft Launcher")
            await notify_admin(
                f"[SemmieBOT] Rol '{role.name}' asignado a {member.display_name} ({member.id})."
            )
        except discord.Forbidden:
            await notify_admin(
                f"[SemmieBOT][role] Discord nego la asignacion del rol '{role.name}' a "
                f"{member.display_name}. Revisa permisos y jerarquia."
            )
        except discord.HTTPException as exc:
            await notify_admin(
                f"[SemmieBOT][role] Error HTTP asignando '{role.name}' a {member.display_name}: {exc}"
            )


def build_team_list_embeds(teams: List[TeamDocument]) -> List[discord.Embed]:
    active_teams = [team for team in teams if not team.archived]
    active_teams.sort(key=lambda item: item.team_name.lower())
    embeds: List[discord.Embed] = []
    for index, team in enumerate(active_teams, start=1):
        embed = discord.Embed(
            title=f"🛡️ {team.team_name}",
            description=team_summary_line(team),
            colour=discord_color_from_hex(team.representative_color),
            timestamp=discord.utils.utcnow(),
        )
        embed.add_field(
            name="Lider",
            value=format_member_reference(team.founder_minecraft_username or "Desconocido", team.founder_discord_user_id, founder=True),
            inline=True,
        )
        embed.add_field(name="Integrantes", value=format_member_list(team, include_founder=False)[:1024], inline=False)
        icon_url = team_icon_url(team)
        if icon_url:
            embed.set_thumbnail(url=icon_url)
        embed.set_footer(text=f"Equipo {index} de {len(active_teams)}")
        embeds.append(embed)
    return embeds


async def sync_app_commands() -> None:
    target_guilds = resolve_target_guilds()
    if not target_guilds:
        return
    for guild in target_guilds:
        if guild.id in SYNCED_COMMAND_GUILDS:
            continue
        guild_object = discord.Object(id=guild.id)
        tree.copy_global_to(guild=guild_object)
        await tree.sync(guild=guild_object)
        SYNCED_COMMAND_GUILDS.add(guild.id)


@tree.command(name="team_list", description="Muestra la lista actual de equipos de Semencraft.")
async def team_list_command(interaction: discord.Interaction) -> None:
    await interaction.response.defer(thinking=True)
    try:
        teams = await load_team_documents_async()
        embeds = build_team_list_embeds(teams)
        if not embeds:
            await interaction.followup.send("No hay equipos activos en este momento.")
            return
        first_chunk = embeds[:10]
        await interaction.followup.send(embeds=first_chunk)
        remaining = embeds[10:]
        while remaining:
            await interaction.followup.send(embeds=remaining[:10])
            remaining = remaining[10:]
    except Exception as exc:
        await interaction.followup.send(f"No pude cargar la lista de equipos: {exc}")


@tasks.loop(seconds=CONFIG.get("syncIntervalSeconds", 120))
async def sync_loop() -> None:
    try:
        await run_sync_cycle("interval")
    except Exception as exc:
        print(f"[sync][error] {exc}")
        await notify_admin_error("sync", exc)


@sync_loop.before_loop
async def before_sync_loop() -> None:
    await client.wait_until_ready()


@tasks.loop(seconds=CONFIG.get("actionSyncIntervalSeconds", 5))
async def action_sync_loop() -> None:
    try:
        await run_action_sync_cycle("fast_interval")
    except Exception as exc:
        print(f"[fast_sync][error] {exc}")
        await notify_admin_error("fast_sync", exc)


@action_sync_loop.before_loop
async def before_action_sync_loop() -> None:
    await client.wait_until_ready()


@client.event
async def on_ready() -> None:
    print(f"Bot conectado como {client.user} ({client.user.id})")
    print(f"Guilds visibles: {[f'{guild.name} ({guild.id})' for guild in client.guilds]}")
    await notify_admin(
        f"[SemmieBOT] Bot conectado como {client.user}.\n"
        f"Guild objetivo: {CONFIG.get('guildId', 'todos')}\n"
        f"Guilds visibles: {[f'{guild.name} ({guild.id})' for guild in client.guilds]}"
    )
    try:
        await sync_app_commands()
    except Exception as exc:
        print(f"[commands][error] {exc}")
        await notify_admin_error("commands", exc)
    try:
        await run_sync_cycle("startup")
    except Exception as exc:
        print(f"[startup][error] {exc}")
        await notify_admin_error("startup", exc)
    if not sync_loop.is_running():
        sync_loop.start()
    if not action_sync_loop.is_running():
        action_sync_loop.start()


@client.event
async def on_member_join(member: discord.Member) -> None:
    if member.bot:
        return
    try:
        await run_sync_cycle("member_join")
    except Exception as exc:
        print(f"[member_join][error] {exc}")
        await notify_admin_error("member_join", exc)


@client.event
async def on_member_remove(member: discord.Member) -> None:
    if member.bot:
        return
    try:
        await run_sync_cycle("member_remove")
    except Exception as exc:
        print(f"[member_remove][error] {exc}")
        await notify_admin_error("member_remove", exc)


@client.event
async def on_member_update(before: discord.Member, after: discord.Member) -> None:
    if after.bot:
        return
    try:
        await run_action_sync_cycle("member_update")
    except Exception as exc:
        print(f"[member_update][error] {exc}")
        await notify_admin_error("member_update", exc)


def release_single_instance_lock() -> None:
    global LOCK_HANDLE
    if LOCK_HANDLE is None:
        return
    try:
        LOCK_HANDLE.seek(0)
        msvcrt.locking(LOCK_HANDLE.fileno(), msvcrt.LK_UNLCK, 1)
    except OSError:
        pass
    try:
        LOCK_HANDLE.close()
    except OSError:
        pass
    LOCK_HANDLE = None


def acquire_single_instance_lock() -> None:
    global LOCK_HANDLE
    LOCK_PATH.parent.mkdir(parents=True, exist_ok=True)
    lock_handle = LOCK_PATH.open("w")
    try:
        msvcrt.locking(lock_handle.fileno(), msvcrt.LK_NBLCK, 1)
    except OSError:
        try:
            lock_handle.close()
        except OSError:
            pass
        raise SystemExit("Ya hay otra instancia de SemmieBOT ejecutandose.")
    lock_handle.seek(0)
    lock_handle.truncate()
    lock_handle.write(str(os.getpid()))
    lock_handle.flush()
    LOCK_HANDLE = lock_handle
    atexit.register(release_single_instance_lock)


def main() -> None:
    acquire_single_instance_lock()
    token = CONFIG.get("discordBotToken", "").strip()
    if not token:
        raise SystemExit("Falta discordBotToken en bot-config.json")

    try:
        client.run(token)
    except KeyboardInterrupt:
        print("Bot detenido manualmente.")


if __name__ == "__main__":
    main()
