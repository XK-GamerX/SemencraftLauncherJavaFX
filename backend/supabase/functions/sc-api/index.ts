// API de Semencraft: cuentas, sesiones, pagos por Yape y panel de admins.
//
// La usan el launcher y la pagina web con la clave publica. Esta funcion es la unica que usa la
// clave secreta: valida la sesion y los permisos de cada accion antes de tocar la base de datos.
// Las tablas de equipos se usan directo desde el launcher, con reglas RLS que leen la cabecera
// x-sc-session (ver la funcion sc_session_user en las migraciones).
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SECRET_KEY = secretKey();
const db = createClient(SUPABASE_URL, SECRET_KEY, { auth: { persistSession: false, autoRefreshToken: false } });

const WEB_URL = "https://xk-gamerx.github.io/semencraft/";
const DISCORD_INVITE = "https://discord.gg/WBaA4UrVvU";
const YAPE_NUMBER = "+51 977 557 854";
const PRICE_REGULAR = 3;
const PRICE_LATE = 5;
/** Ultimo dia con precio normal: 18 de diciembre, hasta las 23:59 de Peru (UTC-5). */
const PRICE_DEADLINE = new Date("2026-12-19T05:00:00Z");

const PASSWORD_ITERATIONS = 60_000;
const SESSION_DAYS = { launcher: 30, web: 7 } as const;
const TEST_ACCOUNT_MINUTES = 20;
const MAX_PROOF_BYTES = 6 * 1024 * 1024;
const PROOF_BUCKET = "payment-proofs";
const SAMPLE_PROOF_PATH = "samples/test-proof.png";
const USER_COLUMNS =
  "id, minecraft_username, minecraft_username_normalized, discord_username, team_id, role, access_status, banned, ban_reason, " +
  "banned_at, banned_until, is_test, expires_at, registered_via, admin_note, created_at, last_seen_at, presence_status";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-sc-session, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type Json = Record<string, unknown>;
type User = {
  id: string;
  minecraft_username: string;
  minecraft_username_normalized: string;
  discord_username: string | null;
  team_id: string | null;
  role: "player" | "admin";
  access_status: "pending" | "granted" | "revoked";
  banned: boolean;
  ban_reason: string | null;
  banned_at: string | null;
  banned_until: string | null;
  is_test: boolean;
  expires_at: string | null;
  registered_via: string;
  admin_note: string | null;
  created_at: string;
  last_seen_at: string | null;
  presence_status: string | null;
};
type Ctx = { req: Request; body: Json; ip: string; token: string; user: User | null };

/** Error que se le muestra tal cual al usuario. */
class Fail extends Error {
  constructor(message: string, public code = "error", public status = 400, public extra: Json = {}) {
    super(message);
  }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return reply({ ok: false, error: "Usa POST." }, 405);
  let body: Json;
  try {
    body = await req.json();
  } catch {
    return reply({ ok: false, error: "Peticion invalida." }, 400);
  }
  const ip = (req.headers.get("x-forwarded-for") ?? "").split(",")[0].trim() || "desconocida";
  const ctx: Ctx = { req, body, ip, token: req.headers.get("x-sc-session") ?? "", user: null };
  const action = String(body.action ?? "");
  const handler = ACTIONS[action];
  if (!handler) return reply({ ok: false, error: "Accion desconocida." }, 404);
  try {
    return reply({ ok: true, ...(await handler(ctx)) });
  } catch (error) {
    if (error instanceof Fail) return reply({ ok: false, error: error.message, code: error.code, ...error.extra }, error.status);
    console.error(action, error);
    return reply({ ok: false, error: "Algo salio mal en el servidor. Intenta de nuevo en un momento." }, 500);
  }
});

const ACTIONS: Record<string, (ctx: Ctx) => Promise<Json>> = {
  config: async () => ({ config: publicConfig() }),
  register,
  login,
  logout,
  me,
  changePassword,
  submitPayment,
  discordStart,
  discordFinish,
  discordLinkStart,
  discordLinkStatus,
  discordLinkFinish,
  uploadStorage,
  adminOverview,
  adminListUsers,
  adminUser,
  adminPayments,
  adminReviewPayment,
  adminMarkPaid,
  adminCreateUser,
  adminSetAccess,
  adminSetAccessAll,
  adminBan,
  adminUnban,
  adminResetPassword,
  adminLogoutUser,
  adminSetRole,
  adminRemoveFromTeam,
  adminSetTeam,
  adminSetNote,
  adminDeleteUser,
  adminCreateTestUser,
  adminDeleteTestUsers,
  adminAudit,
};

// ---------- Cuenta ----------

async function register(ctx: Ctx): Promise<Json> {
  const username = String(ctx.body.username ?? "").trim();
  const password = String(ctx.body.password ?? "");
  const discordRaw = String(ctx.body.discordUsername ?? "").trim();
  validateUsername(username);
  validatePassword(password);
  await limitAttempts(`register:${ctx.ip}`, 6, 60, true);
  if (await findUser(username)) throw new Fail("Ese nombre de Minecraft ya esta registrado.", "username_taken");
  // Discord es opcional; si lo escribe, se revisa antes de crear la cuenta para poder corregirlo.
  const member = discordRaw ? await findDiscordMember(discordRaw, null) : null;

  const { data, error } = await db.from("app_users").insert({
    minecraft_username: username,
    minecraft_username_normalized: username.toLowerCase(),
    password_hash: await hashPassword(password),
    is_registered: true,
    registered_via: "web",
    access_status: "pending",
  }).select(USER_COLUMNS).single();
  if (error) {
    if (error.code === "23505") throw new Fail("Ese nombre de Minecraft ya esta registrado.", "username_taken");
    throw error;
  }
  await recordAttempt(`register:${ctx.ip}`, true);
  const token = await createSession(data.id, "web");
  const discord = member ? await createDiscordCode(username, member, { purpose: "link", user_id: data.id }) : null;
  return { token, profile: await profile(data as User), discord };
}

async function login(ctx: Ctx): Promise<Json> {
  const username = String(ctx.body.username ?? "").trim();
  const password = String(ctx.body.password ?? "");
  const client = ctx.body.client === "launcher" ? "launcher" : "web";
  const userKey = `login:${username.toLowerCase()}`;
  await limitAttempts(userKey, 8, 15);
  await limitAttempts(`login-ip:${ctx.ip}`, 30, 15);

  const row = await findUser(username, true);
  if (!row || !(await verifyPassword(password, row.password_hash))) {
    await recordAttempt(userKey, false);
    await recordAttempt(`login-ip:${ctx.ip}`, false);
    throw new Fail("Usuario o contrasena incorrectos.", "bad_credentials", 401);
  }
  const user = row as unknown as User;
  if (user.is_test && user.expires_at && new Date(user.expires_at) < new Date()) {
    throw new Fail("Esta cuenta de prueba ya vencio.", "expired", 403);
  }
  assertNotBanned(user);
  // Desde la 1.3.1 (limited: true) se entra sin acceso: el launcher solo deja cambiar la skin hasta que
  // un admin de acceso. Los launchers anteriores no saben limitarse, asi que a ellos se les sigue cerrando.
  if (client === "launcher" && user.access_status !== "granted" && ctx.body.limited !== true) throw accessFail(user);
  await recordAttempt(userKey, true);
  const token = await createSession(user.id, client);
  return { token, profile: await profile(user) };
}

async function logout(ctx: Ctx): Promise<Json> {
  if (ctx.token) await db.from("app_sessions").delete().eq("token_hash", await sha256Hex(ctx.token));
  return {};
}

/** Perfil de la sesion. El launcher lo llama al abrir, antes de jugar y cada minuto para seguir baneos y accesos. */
async function me(ctx: Ctx): Promise<Json> {
  const user = await requireUser(ctx);
  if (ctx.body.client === "launcher" && user.access_status !== "granted" && ctx.body.limited !== true) throw accessFail(user);
  return { profile: await profile(user) };
}

async function changePassword(ctx: Ctx): Promise<Json> {
  const user = await requireUser(ctx);
  const current = String(ctx.body.currentPassword ?? "");
  const next = String(ctx.body.newPassword ?? "");
  validatePassword(next);
  const { data } = await db.from("app_users").select("password_hash").eq("id", user.id).single();
  if (!data || !(await verifyPassword(current, data.password_hash))) {
    throw new Fail("La contrasena actual no es correcta.", "bad_credentials", 401);
  }
  await db.from("app_users").update({ password_hash: await hashPassword(next) }).eq("id", user.id);
  // Se cierran las demas sesiones: si alguien tenia la contrasena vieja, queda fuera.
  await db.from("app_sessions").delete().eq("user_id", user.id).neq("token_hash", await sha256Hex(ctx.token));
  return {};
}

// ---------- Pago por Yape ----------

async function submitPayment(ctx: Ctx): Promise<Json> {
  const user = await requireUser(ctx);
  if (user.access_status === "granted") throw new Fail("Tu cuenta ya tiene acceso. No necesitas pagar otra vez.", "already_granted");
  const { data: pending } = await db.from("payments").select("id").eq("user_id", user.id).eq("status", "pending").limit(1);
  if (pending && pending.length > 0) throw new Fail("Ya enviaste un comprobante. Espera a que lo revisemos.", "pending_exists");

  const image = decodeImage(String(ctx.body.image ?? ""));
  const operationCode = cleanText(ctx.body.operationCode, 32);
  const note = cleanText(ctx.body.note, 300);
  const amount = currentPrice();
  const path = `${user.id}/${crypto.randomUUID()}.${image.ext}`;
  const upload = await db.storage.from(PROOF_BUCKET).upload(path, image.bytes, { contentType: image.mime, upsert: false });
  if (upload.error) throw upload.error;
  const { data, error } = await db.from("payments").insert({
    user_id: user.id,
    amount_soles: amount,
    proof_path: path,
    operation_code: operationCode || null,
    note: note || null,
  }).select("id, amount_soles, status, created_at").single();
  if (error) throw error;
  // SemmieBOT avisa en el canal de admins (lee los pagos con admin_notified_at vacio).
  return { payment: data };
}

// ---------- Registro desde el launcher (verificacion por Discord) ----------

async function discordStart(ctx: Ctx): Promise<Json> {
  const username = String(ctx.body.username ?? "").trim();
  const password = String(ctx.body.password ?? "");
  const discordRaw = String(ctx.body.discordUsername ?? "");
  validateUsername(username);
  validatePassword(password);
  await limitAttempts(`register:${ctx.ip}`, 6, 60, true);
  if (await findUser(username)) throw new Fail("Ese nombre de Minecraft ya esta registrado.", "username_taken");

  const member = await findDiscordMember(discordRaw, null);
  const job = await createDiscordCode(username, member, { password_hash: await hashPassword(password) });
  await recordAttempt(`register:${ctx.ip}`, true);
  return job;
}

async function discordFinish(ctx: Ctx): Promise<Json> {
  const id = String(ctx.body.verificationId ?? "");
  const code = String(ctx.body.code ?? "").trim();
  await limitAttempts(`verify:${id}`, 6, 15);
  const { data: job } = await db.from("discord_role_jobs").select("id, status, payload, job_type").eq("id", id).maybeSingle();
  if (!job || job.job_type !== "registration_verification") throw new Fail("No se encontro la verificacion pendiente.");
  const p = job.payload as Json;
  if (job.status === "completed") throw new Fail("Ese codigo ya fue usado.");
  if (job.status === "expired" || new Date(String(p.expires_at)) < new Date()) {
    throw new Fail("El codigo ya vencio. Vuelve a registrarte para recibir uno nuevo.");
  }
  if (code !== p.verification_code) {
    await recordAttempt(`verify:${id}`, false);
    throw new Fail("El codigo es incorrecto.");
  }
  if (await findUser(String(p.minecraft_username))) throw new Fail("Ese nombre de Minecraft ya se registro mientras validabas el codigo.");
  const { error } = await db.from("app_users").insert({
    minecraft_username: p.minecraft_username,
    minecraft_username_normalized: p.minecraft_username_normalized,
    password_hash: p.password_hash,
    discord_username: p.discord_username,
    discord_lookup_normalized: p.discord_lookup_normalized,
    discord_user_id: p.discord_user_id,
    is_registered: true,
    registered_via: "discord",
    access_status: "pending",
  });
  if (error) throw error;
  await db.from("discord_role_jobs").update({ status: "completed", processed_at: new Date().toISOString(), payload: { ...p, delivery_status: "verified" } }).eq("id", id);
  return {
    message: `Cuenta creada. Para activarla, envia tu comprobante de pago en ${WEB_URL}`,
    webUrl: WEB_URL,
  };
}

// ---------- Vincular Discord a una cuenta ya creada (desde la web) ----------

/** Envia por DM (lo hace SemmieBOT) un codigo para vincular el Discord a la cuenta de la sesion. */
async function discordLinkStart(ctx: Ctx): Promise<Json> {
  const user = await requireUser(ctx);
  await limitAttempts(`discord-link:${user.id}`, 6, 60, true);
  const member = await findDiscordMember(String(ctx.body.discordUsername ?? ""), user.id);
  const job = await createDiscordCode(user.minecraft_username, member, { purpose: "link", user_id: user.id });
  await recordAttempt(`discord-link:${user.id}`, true);
  return job;
}

/** Si el bot ya pudo mandar el DM (para avisar si el jugador tiene los mensajes directos cerrados). */
async function discordLinkStatus(ctx: Ctx): Promise<Json> {
  const user = await requireUser(ctx);
  const job = await linkJob(user, String(ctx.body.verificationId ?? ""));
  const p = job.payload as Json;
  return { status: job.status, delivery: p.delivery_status ?? "pending" };
}

async function discordLinkFinish(ctx: Ctx): Promise<Json> {
  const user = await requireUser(ctx);
  const id = String(ctx.body.verificationId ?? "");
  const code = String(ctx.body.code ?? "").trim();
  await limitAttempts(`verify:${id}`, 6, 15);
  const job = await linkJob(user, id);
  const p = job.payload as Json;
  if (job.status === "completed") throw new Fail("Ese codigo ya fue usado.");
  if (job.status === "expired" || new Date(String(p.expires_at)) < new Date()) {
    throw new Fail("El codigo ya vencio. Pide uno nuevo.");
  }
  if (code !== p.verification_code) {
    await recordAttempt(`verify:${id}`, false);
    throw new Fail("El codigo es incorrecto.");
  }
  const { data: taken } = await db.from("app_users").select("id").eq("discord_user_id", p.discord_user_id).neq("id", user.id).limit(1);
  if (taken && taken.length > 0) throw new Fail("Ese usuario de Discord ya esta vinculado a otra cuenta.");
  const { error } = await db.from("app_users").update({
    discord_username: p.discord_username,
    discord_lookup_normalized: p.discord_lookup_normalized,
    discord_user_id: p.discord_user_id,
  }).eq("id", user.id);
  if (error) throw error;
  await db.from("discord_role_jobs").update({ status: "completed", processed_at: new Date().toISOString(), payload: { ...p, delivery_status: "verified" } }).eq("id", id);
  return { profile: await profile((await userById(user.id))!) };
}

async function linkJob(user: User, id: string): Promise<{ id: string; status: string; payload: Json }> {
  if (!/^[0-9a-f-]{36}$/.test(id)) throw new Fail("No se encontro la verificacion pendiente.");
  const { data: job } = await db.from("discord_role_jobs").select("id, status, payload, job_type").eq("id", id).maybeSingle();
  if (!job || job.job_type !== "registration_verification" || (job.payload as Json)?.user_id !== user.id) {
    throw new Fail("No se encontro la verificacion pendiente.");
  }
  return job as { id: string; status: string; payload: Json };
}

type DiscordMember = { discord_user_id: string; discord_username: string; discord_global_name: string | null; discord_display_name: string | null; lookup: string };

/** Busca al usuario en la lista de miembros del servidor que guarda el bot. */
async function findDiscordMember(raw: string, ownerId: string | null): Promise<DiscordMember> {
  const lookup = normalizeDiscord(raw);
  if (!lookup) throw new Fail("Introduce tu usuario de Discord.");
  const { data: members } = await db.from("discord_members_cache").select("discord_user_id, discord_username, discord_global_name, discord_display_name, lookup_keys, is_bot").limit(5000);
  const member = (members ?? []).find((m) =>
    !m.is_bot && [m.discord_username, m.discord_global_name, m.discord_display_name, ...((m.lookup_keys as string[]) ?? [])]
      .some((key) => normalizeDiscord(String(key ?? "")) === lookup)
  );
  if (!member) {
    throw new Fail(`Ese usuario de Discord no aparece en el servidor. Revisa que este bien escrito y que ya te hayas unido: ${DISCORD_INVITE}`, "discord_not_found");
  }
  let query = db.from("app_users").select("id").eq("discord_user_id", member.discord_user_id);
  if (ownerId) query = query.neq("id", ownerId);
  const { data: linked } = await query.limit(1);
  if (linked && linked.length > 0) throw new Fail("Ese usuario de Discord ya esta vinculado a otra cuenta.");
  return { ...member, lookup } as DiscordMember;
}

/** Crea el trabajo que SemmieBOT lee para mandar el codigo por DM. Invalida los codigos anteriores. */
async function createDiscordCode(username: string, member: DiscordMember, extra: Json): Promise<Json> {
  const { data: jobs } = await db.from("discord_role_jobs").select("id, payload").eq("job_type", "registration_verification").in("status", ["pending", "sent", "delivery_failed"]);
  for (const job of jobs ?? []) {
    const p = job.payload as Json;
    if (p.minecraft_username_normalized === username.toLowerCase() || p.discord_user_id === member.discord_user_id) {
      await db.from("discord_role_jobs").update({ status: "expired", processed_at: new Date().toISOString(), payload: { ...p, delivery_status: "expired" } }).eq("id", job.id);
    }
  }
  const code = String(crypto.getRandomValues(new Uint32Array(1))[0] % 1_000_000).padStart(6, "0");
  const expiresAt = new Date(Date.now() + 10 * 60_000).toISOString();
  const display = member.discord_global_name || member.discord_display_name || member.discord_username;
  const { data: job, error } = await db.from("discord_role_jobs").insert({
    job_type: "registration_verification",
    status: "pending",
    guild_id: "registration",
    payload: {
      ...extra,
      minecraft_username: username,
      minecraft_username_normalized: username.toLowerCase(),
      discord_username: display,
      discord_lookup_normalized: member.lookup,
      discord_user_id: member.discord_user_id,
      verification_code: code,
      expires_at: expiresAt,
      delivery_status: "pending",
    },
  }).select("id").single();
  if (error) throw error;
  return { verificationId: job.id, discordDisplayName: display, expiresAt };
}

// ---------- Archivos del launcher (skins e iconos de equipo) ----------

async function uploadStorage(ctx: Ctx): Promise<Json> {
  const bucket = String(ctx.body.bucket ?? "");
  // La skin propia se puede cambiar sin acceso al launcher; el icono de equipo no.
  const user = await requireUser(ctx, bucket !== "player-skins");
  const path = String(ctx.body.path ?? "");
  const contentType = String(ctx.body.contentType ?? "application/octet-stream");
  const bytes = base64ToBytes(String(ctx.body.content ?? ""));
  if (bytes.length > 2 * 1024 * 1024) throw new Fail("El archivo es demasiado grande.");

  if (bucket === "player-skins") {
    // Solo la carpeta del propio jugador, con la skin y su ficha.
    const own = `${user.minecraft_username_normalized}/`;
    if (path !== own + "skin.png" && path !== own + "meta.json") throw new Fail("No puedes cambiar la skin de otro jugador.", "forbidden", 403);
  } else if (bucket === "team-icons") {
    const match = /^([0-9a-f-]{36})\/icon\.png$/.exec(path);
    if (!match) throw new Fail("Ruta de icono invalida.");
    const { data: team } = await db.from("teams").select("founder_user_id").eq("id", match[1]).maybeSingle();
    // El icono se sube antes de crear el equipo; si ya existe, solo lo cambia el fundador.
    if (team && team.founder_user_id !== user.id) throw new Fail("Solo el fundador puede cambiar el icono.", "forbidden", 403);
  } else {
    throw new Fail("Destino invalido.", "forbidden", 403);
  }
  const { error } = await db.storage.from(bucket).upload(path, bytes, { contentType, upsert: true });
  if (error) throw error;
  return { path };
}

// ---------- Panel de admins ----------

async function adminOverview(ctx: Ctx): Promise<Json> {
  await requireAdmin(ctx);
  await db.rpc("sc_cleanup");
  const { data: users } = await db.from("app_users").select("access_status, banned, banned_until, role, is_test, registered_via");
  const { data: payments } = await db.from("payments").select("status, amount_soles");
  const now = new Date();
  const list = users ?? [];
  const banned = list.filter((u) => u.banned && (!u.banned_until || new Date(u.banned_until) > now)).length;
  return {
    stats: {
      users: list.filter((u) => !u.is_test).length,
      granted: list.filter((u) => !u.is_test && u.access_status === "granted").length,
      pending: list.filter((u) => !u.is_test && u.access_status === "pending").length,
      revoked: list.filter((u) => !u.is_test && u.access_status === "revoked").length,
      banned,
      admins: list.filter((u) => u.role === "admin").length,
      tests: list.filter((u) => u.is_test).length,
      paymentsPending: (payments ?? []).filter((p) => p.status === "pending").length,
      paymentsApproved: (payments ?? []).filter((p) => p.status === "approved").length,
      collected: (payments ?? []).filter((p) => p.status === "approved").reduce((sum, p) => sum + Number(p.amount_soles), 0),
    },
    config: publicConfig(),
  };
}

async function adminListUsers(ctx: Ctx): Promise<Json> {
  await requireAdmin(ctx);
  await db.rpc("sc_cleanup");
  const { data, error } = await db.from("app_users").select(USER_COLUMNS).order("created_at", { ascending: false }).limit(2000);
  if (error) throw error;
  const { data: payments } = await db.from("payments").select("user_id, status, created_at").order("created_at", { ascending: false });
  const { data: teams } = await db.from("teams").select("id, team_name");
  const latest = new Map<string, string>();
  for (const p of payments ?? []) if (!latest.has(p.user_id)) latest.set(p.user_id, p.status);
  const teamNames = new Map((teams ?? []).map((t) => [t.id, t.team_name]));
  return {
    users: (data as User[]).map((u) => ({
      ...publicUser(u),
      paymentStatus: latest.get(u.id) ?? "none",
      teamName: u.team_id ? teamNames.get(u.team_id) ?? null : null,
    })),
  };
}

async function adminUser(ctx: Ctx): Promise<Json> {
  await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  const [{ data: payments }, { count: sessions }, { data: audit }, teams] = await Promise.all([
    db.from("payments").select("*").eq("user_id", target.id).order("created_at", { ascending: false }),
    db.from("app_sessions").select("token_hash", { count: "exact", head: true }).eq("user_id", target.id).gt("expires_at", new Date().toISOString()),
    db.from("admin_audit").select("admin_name, action, details, created_at").eq("target_user_id", target.id).order("created_at", { ascending: false }).limit(30),
    activeTeams(),
  ]);
  const current = target.team_id ? teams.find((t) => t.id === target.team_id) : null;
  const team = current ? { ...current, team_name: current.name, founder: current.founderId === target.id } : null;
  return {
    user: publicUser(target),
    team,
    teams,
    sessions: sessions ?? 0,
    payments: await withProofUrls(payments ?? []),
    audit: audit ?? [],
  };
}

async function adminPayments(ctx: Ctx): Promise<Json> {
  await requireAdmin(ctx);
  const status = String(ctx.body.status ?? "pending");
  let query = db.from("payments").select("*, app_users!payments_user_id_fkey(minecraft_username, access_status, is_test)").order("created_at", { ascending: status === "pending" }).limit(200);
  if (status !== "all") query = query.eq("status", status);
  const { data, error } = await query;
  if (error) throw error;
  return { payments: await withProofUrls(data ?? []) };
}

async function adminReviewPayment(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const id = String(ctx.body.paymentId ?? "");
  const approve = ctx.body.approve === true;
  const note = cleanText(ctx.body.note, 300);
  const { data: payment } = await db.from("payments").select("*").eq("id", id).maybeSingle();
  if (!payment) throw new Fail("No se encontro el comprobante.");
  const target = await userById(payment.user_id);
  await db.from("payments").update({
    status: approve ? "approved" : "rejected",
    reviewed_by: admin.id,
    reviewed_at: new Date().toISOString(),
    review_note: note || null,
  }).eq("id", id);
  if (approve && target) await db.from("app_users").update({ access_status: "granted" }).eq("id", target.id);
  await audit(admin, approve ? "payment_approved" : "payment_rejected", target, { paymentId: id, amount: payment.amount_soles, note });
  return {};
}

/** Marca la cuenta como pagada sin captura (por ejemplo, si pago pero no envio el comprobante). */
async function adminMarkPaid(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  const amount = Number(ctx.body.amount ?? currentPrice());
  if (!Number.isFinite(amount) || amount < 0 || amount > 1000) throw new Fail("Monto invalido.");
  const note = cleanText(ctx.body.note, 300);
  const now = new Date().toISOString();
  // Si tenia un comprobante esperando revision, ese queda aprobado en vez de duplicar el pago.
  const { data: pending } = await db.from("payments").select("id").eq("user_id", target.id).eq("status", "pending").limit(1);
  if (pending && pending.length > 0) {
    await db.from("payments").update({ status: "approved", reviewed_by: admin.id, reviewed_at: now, review_note: note || "Marcado como pagado." }).eq("id", pending[0].id);
  } else {
    await db.from("payments").insert({
      user_id: target.id,
      amount_soles: amount,
      method: "manual",
      status: "approved",
      reviewed_by: admin.id,
      reviewed_at: now,
      review_note: note || "Marcado como pagado por un admin.",
      admin_notified_at: now,
    });
  }
  await db.from("app_users").update({ access_status: "granted" }).eq("id", target.id);
  await audit(admin, "marked_paid", target, { amount, note });
  return {};
}

/** Cuenta real creada por un admin (para alguien que no puede registrarse solo). */
async function adminCreateUser(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const username = String(ctx.body.username ?? "").trim();
  validateUsername(username);
  if (await findUser(username)) throw new Fail("Ese nombre de Minecraft ya esta registrado.", "username_taken");
  const access = ctx.body.access === "granted" ? "granted" : "pending";
  const password = randomPassword();
  const { data, error } = await db.from("app_users").insert({
    minecraft_username: username,
    minecraft_username_normalized: username.toLowerCase(),
    password_hash: await hashPassword(password),
    is_registered: true,
    registered_via: "admin",
    access_status: access,
    admin_note: cleanText(ctx.body.note, 1000) || null,
  }).select(USER_COLUMNS).single();
  if (error) throw error;
  await audit(admin, "user_created", data as User, { access });
  return { user: publicUser(data as User), password };
}

async function adminSetAccess(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  const access = String(ctx.body.access ?? "");
  if (!["granted", "revoked", "pending"].includes(access)) throw new Fail("Estado de acceso invalido.");
  if (access !== "granted") guardOtherAdmin(admin, target, "quitarle el acceso");
  await db.from("app_users").update({ access_status: access }).eq("id", target.id);
  // No se cierran sesiones: el launcher revisa el acceso cada minuto y se limita o desbloquea solo.
  await audit(admin, "access_" + access, target, {});
  return {};
}

/** Da o quita el acceso al launcher a todas las cuentas a la vez, menos a los admins. */
async function adminSetAccessAll(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const access = ctx.body.access === "granted" ? "granted" : ctx.body.access === "revoked" ? "revoked" : "";
  if (!access) throw new Fail("Estado de acceso invalido.");
  const { data, error } = await db.from("app_users").update({ access_status: access })
    .neq("role", "admin").neq("access_status", access).select("id");
  if (error) throw error;
  const count = (data ?? []).length;
  await audit(admin, access === "granted" ? "access_all_granted" : "access_all_revoked", null, { count });
  return { count };
}

async function adminBan(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  guardOtherAdmin(admin, target, "banear");
  const reason = cleanText(ctx.body.reason, 300) || "Sin motivo indicado.";
  const hours = Number(ctx.body.hours ?? 0);
  const until = hours > 0 ? new Date(Date.now() + Math.min(hours, 24 * 3650) * 3600_000).toISOString() : null;
  await db.from("app_users").update({
    banned: true,
    ban_reason: reason,
    banned_at: new Date().toISOString(),
    banned_until: until,
    banned_by: admin.id,
  }).eq("id", target.id);
  await db.from("app_sessions").delete().eq("user_id", target.id);
  await audit(admin, "ban", target, { reason, until });
  return {};
}

async function adminUnban(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  await db.from("app_users").update({ banned: false, ban_reason: null, banned_at: null, banned_until: null, banned_by: null }).eq("id", target.id);
  await audit(admin, "unban", target, {});
  return {};
}

async function adminResetPassword(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  guardOtherAdmin(admin, target, "cambiarle la contrasena");
  const password = randomPassword();
  await db.from("app_users").update({ password_hash: await hashPassword(password) }).eq("id", target.id);
  await db.from("app_sessions").delete().eq("user_id", target.id);
  await audit(admin, "password_reset", target, {});
  return { password };
}

async function adminLogoutUser(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  await db.from("app_sessions").delete().eq("user_id", target.id);
  await audit(admin, "force_logout", target, {});
  return {};
}

async function adminSetRole(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  const role = ctx.body.role === "admin" ? "admin" : "player";
  if (target.is_test && role === "admin") throw new Fail("Las cuentas de prueba no pueden ser admin.");
  if (role === "player") {
    if (target.id === admin.id) throw new Fail("No puedes quitarte el admin a ti mismo.");
    const { count } = await db.from("app_users").select("id", { count: "exact", head: true }).eq("role", "admin");
    if ((count ?? 0) <= 1) throw new Fail("Tiene que quedar al menos un admin.");
  }
  await db.from("app_users").update({ role, ...(role === "admin" ? { access_status: "granted" } : {}) }).eq("id", target.id);
  await audit(admin, role === "admin" ? "admin_granted" : "admin_removed", target, {});
  return {};
}

async function adminRemoveFromTeam(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  if (!target.team_id) throw new Fail("Ese jugador no esta en ningun equipo.");
  const teamName = await leaveTeam(target);
  await audit(admin, "team_removed", target, { team: teamName });
  return {};
}

/** Mueve al jugador a otro equipo existente (si estaba en uno, sale de ese primero). */
async function adminSetTeam(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  const teamId = String(ctx.body.teamId ?? "");
  const { data: team } = await db.from("teams").select("id, team_name, archived").eq("id", /^[0-9a-f-]{36}$/.test(teamId) ? teamId : "00000000-0000-0000-0000-000000000000").maybeSingle();
  if (!team || team.archived) throw new Fail("Ese equipo no existe o esta archivado.");
  if (target.team_id === team.id) throw new Fail("Ese jugador ya esta en ese equipo.");
  const previous = target.team_id ? await leaveTeam(target) : null;
  const { error } = await db.from("team_members").insert({ team_id: team.id, user_id: target.id, member_role: "member" });
  if (error) throw error;
  await db.from("app_users").update({ team_id: team.id }).eq("id", target.id);
  // Las invitaciones pendientes a ese equipo ya no hacen falta.
  await db.from("team_invites").update({ status: "accepted", responded_at: new Date().toISOString() }).eq("team_id", team.id).eq("invited_user_id", target.id).eq("status", "pending");
  await db.from("notifications").insert({
    user_id: target.id,
    type: "info",
    title: "Cambio de equipo",
    message: `Un admin te movio al equipo ${team.team_name}.`,
    status: "info",
    read: false,
    team_id: team.id,
    created_by: admin.id,
  });
  await audit(admin, "team_changed", target, { from: previous, to: team.team_name });
  return {};
}

async function activeTeams(): Promise<{ id: string; name: string; color: string; founderId: string; members: number }[]> {
  const [{ data: teams }, { data: members }] = await Promise.all([
    db.from("teams").select("id, team_name, representative_color, founder_user_id").eq("archived", false).order("team_name"),
    db.from("team_members").select("team_id"),
  ]);
  const counts = new Map<string, number>();
  for (const m of members ?? []) counts.set(m.team_id, (counts.get(m.team_id) ?? 0) + 1);
  return (teams ?? []).map((t) => ({
    id: t.id,
    name: t.team_name,
    color: t.representative_color,
    founderId: t.founder_user_id,
    members: counts.get(t.id) ?? 0,
  }));
}

async function adminSetNote(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  const note = cleanText(ctx.body.note, 1000);
  await db.from("app_users").update({ admin_note: note || null }).eq("id", target.id);
  await audit(admin, "note", target, {});
  return {};
}

async function adminDeleteUser(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const target = await requireTarget(ctx);
  guardOtherAdmin(admin, target, "borrar");
  if (String(ctx.body.confirm ?? "").toLowerCase() !== target.minecraft_username_normalized) {
    throw new Fail("Escribe el nombre exacto del jugador para confirmar.");
  }
  if (target.team_id) await leaveTeam(target);
  // Si fundo equipos ya archivados, se borran: si no, la cuenta no se puede eliminar.
  await db.from("teams").delete().eq("founder_user_id", target.id);
  const { data: proofs } = await db.from("payments").select("proof_path").eq("user_id", target.id);
  const paths = (proofs ?? []).map((p) => p.proof_path).filter((p) => p && p !== SAMPLE_PROOF_PATH);
  if (paths.length > 0) await db.storage.from(PROOF_BUCKET).remove(paths);
  await audit(admin, "user_deleted", target, {});
  const { error } = await db.from("app_users").delete().eq("id", target.id);
  if (error) throw error;
  return {};
}

async function adminCreateTestUser(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const access = ctx.body.access === "granted" ? "granted" : "pending";
  const withPayment = ctx.body.withPayment === true;
  let username = "";
  for (let i = 0; i < 5 && !username; i++) {
    const candidate = "Prueba_" + randomString(6, "abcdefghjkmnpqrstuvwxyz23456789");
    if (!(await findUser(candidate))) username = candidate;
  }
  if (!username) throw new Fail("No se pudo generar un nombre libre. Intenta otra vez.");
  const password = randomPassword();
  const expiresAt = new Date(Date.now() + TEST_ACCOUNT_MINUTES * 60_000).toISOString();
  const { data, error } = await db.from("app_users").insert({
    minecraft_username: username,
    minecraft_username_normalized: username.toLowerCase(),
    password_hash: await hashPassword(password),
    is_registered: true,
    registered_via: "admin",
    access_status: access,
    is_test: true,
    expires_at: expiresAt,
    admin_note: `Cuenta de prueba creada por ${admin.minecraft_username}.`,
  }).select(USER_COLUMNS).single();
  if (error) throw error;
  if (withPayment) {
    await db.from("payments").insert({
      user_id: data.id,
      amount_soles: currentPrice(),
      proof_path: SAMPLE_PROOF_PATH,
      operation_code: "00000000",
      note: "Comprobante de prueba: no es un pago real.",
      admin_notified_at: new Date().toISOString(),
    });
  }
  await audit(admin, "test_user_created", data as User, { withPayment, access });
  return { user: publicUser(data as User), password, expiresAt };
}

async function adminDeleteTestUsers(ctx: Ctx): Promise<Json> {
  const admin = await requireAdmin(ctx);
  const { data: tests } = await db.from("app_users").select("id").eq("is_test", true);
  const ids = (tests ?? []).map((t) => t.id);
  if (ids.length > 0) {
    await db.from("teams").delete().in("founder_user_id", ids);
    await db.from("app_users").delete().in("id", ids);
  }
  await audit(admin, "test_users_deleted", null, { count: ids.length });
  return { deleted: ids.length };
}

async function adminAudit(ctx: Ctx): Promise<Json> {
  await requireAdmin(ctx);
  const { data } = await db.from("admin_audit").select("admin_name, action, target_name, details, created_at").order("created_at", { ascending: false }).limit(200);
  return { entries: data ?? [] };
}

// ---------- Sesiones y permisos ----------

async function createSession(userId: string, client: "launcher" | "web"): Promise<string> {
  const token = randomString(43, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_");
  const { error } = await db.from("app_sessions").insert({
    token_hash: await sha256Hex(token),
    user_id: userId,
    client,
    expires_at: new Date(Date.now() + SESSION_DAYS[client] * 86400_000).toISOString(),
  });
  if (error) throw error;
  return token;
}

/** Usuario de la sesion. Sin `needAccess`, una cuenta sin pagar puede entrar a la web para pagar. */
async function requireUser(ctx: Ctx, needAccess = false): Promise<User> {
  if (ctx.user) return ctx.user;
  if (!ctx.token) throw new Fail("Inicia sesion para continuar.", "no_session", 401);
  const hash = await sha256Hex(ctx.token);
  const { data: session } = await db.from("app_sessions").select("user_id, expires_at").eq("token_hash", hash).maybeSingle();
  if (!session || new Date(session.expires_at) < new Date()) throw new Fail("Tu sesion vencio. Vuelve a iniciar sesion.", "no_session", 401);
  const user = await userById(session.user_id);
  if (!user) throw new Fail("Tu sesion vencio. Vuelve a iniciar sesion.", "no_session", 401);
  if (user.is_test && user.expires_at && new Date(user.expires_at) < new Date()) throw new Fail("Esta cuenta de prueba ya vencio.", "expired", 401);
  assertNotBanned(user);
  if (needAccess && user.access_status !== "granted") throw accessFail(user);
  await db.from("app_sessions").update({ last_used_at: new Date().toISOString() }).eq("token_hash", hash);
  ctx.user = user;
  return user;
}

async function requireAdmin(ctx: Ctx): Promise<User> {
  const user = await requireUser(ctx);
  if (user.role !== "admin") throw new Fail("Solo los admins pueden hacer esto.", "forbidden", 403);
  return user;
}

async function requireTarget(ctx: Ctx): Promise<User> {
  const target = await userById(String(ctx.body.userId ?? ""));
  if (!target) throw new Fail("No se encontro esa cuenta.", "not_found", 404);
  return target;
}

/** Contra otro admin no hay acciones destructivas: primero hay que quitarle el rol. */
function guardOtherAdmin(admin: User, target: User, what: string) {
  if (target.id === admin.id) throw new Fail(`No puedes ${what} a tu propia cuenta.`);
  if (target.role === "admin") throw new Fail(`No puedes ${what} a otro admin. Quitale el rol de admin primero.`);
}

function assertNotBanned(user: User) {
  if (!user.banned) return;
  if (user.banned_until && new Date(user.banned_until) < new Date()) return;
  const until = user.banned_until ? ` hasta el ${formatLima(user.banned_until)}` : "";
  throw new Fail(`Tu cuenta esta baneada${until}. Motivo: ${user.ban_reason ?? "sin motivo indicado"}.`, "banned", 403, {
    banReason: user.ban_reason,
    bannedUntil: user.banned_until,
  });
}

function accessFail(user: User): Fail {
  if (user.access_status === "revoked") {
    return new Fail("Tu acceso al launcher fue retirado. Habla con un admin en Discord.", "access_revoked", 403, { webUrl: WEB_URL });
  }
  return new Fail("Todavia no tienes acceso al launcher. Un admin te lo dara pronto.", "access_pending", 403, { webUrl: WEB_URL });
}

async function profile(user: User): Promise<Json> {
  const { data: payments } = await db.from("payments")
    .select("id, amount_soles, status, method, review_note, created_at, reviewed_at")
    .eq("user_id", user.id).order("created_at", { ascending: false }).limit(10);
  return { ...publicUser(user), payments: payments ?? [], price: currentPrice() };
}

function publicUser(u: User): Json {
  const now = new Date();
  const banned = u.banned && (!u.banned_until || new Date(u.banned_until) > now);
  return {
    id: u.id,
    username: u.minecraft_username,
    discord: u.discord_username,
    role: u.role,
    access: u.access_status,
    banned,
    banReason: banned ? u.ban_reason : null,
    bannedUntil: banned ? u.banned_until : null,
    isTest: u.is_test,
    expiresAt: u.expires_at,
    registeredVia: u.registered_via,
    note: u.admin_note,
    teamId: u.team_id,
    createdAt: u.created_at,
    lastSeenAt: u.last_seen_at,
    presence: u.presence_status,
  };
}

// ---------- Ayudantes de datos ----------

async function findUser(username: string, withHash = false): Promise<(User & { password_hash: string }) | null> {
  const { data } = await db.from("app_users")
    .select(withHash ? USER_COLUMNS + ", password_hash" : USER_COLUMNS)
    .eq("minecraft_username_normalized", username.trim().toLowerCase())
    .maybeSingle();
  return (data as (User & { password_hash: string }) | null) ?? null;
}

async function userById(id: string): Promise<User | null> {
  if (!/^[0-9a-f-]{36}$/.test(id)) return null;
  const { data } = await db.from("app_users").select(USER_COLUMNS).eq("id", id).maybeSingle();
  return (data as User | null) ?? null;
}

/** Saca al jugador de su equipo; si era el fundador, el mando pasa al miembro mas antiguo. */
async function leaveTeam(target: User): Promise<string | null> {
  const teamId = target.team_id!;
  const { data: team } = await db.from("teams").select("id, team_name, founder_user_id").eq("id", teamId).maybeSingle();
  await db.from("team_members").delete().eq("user_id", target.id);
  await db.from("app_users").update({ team_id: null }).eq("id", target.id);
  if (team && team.founder_user_id === target.id) {
    const { data: rest } = await db.from("team_members").select("user_id").eq("team_id", teamId).order("joined_at", { ascending: true }).limit(1);
    if (rest && rest.length > 0) {
      await db.from("team_members").update({ member_role: "founder" }).eq("team_id", teamId).eq("user_id", rest[0].user_id);
      await db.from("teams").update({ founder_user_id: rest[0].user_id }).eq("id", teamId);
    } else {
      await db.from("teams").update({ archived: true }).eq("id", teamId);
    }
  }
  return team?.team_name ?? null;
}

async function withProofUrls(rows: Json[]): Promise<Json[]> {
  const paths = [...new Set(rows.map((r) => r.proof_path).filter((p): p is string => typeof p === "string" && p !== ""))];
  if (paths.length === 0) return rows;
  const { data } = await db.storage.from(PROOF_BUCKET).createSignedUrls(paths, 60 * 30);
  const urls = new Map((data ?? []).map((d) => [d.path, d.signedUrl]));
  return rows.map((r) => ({ ...r, proofUrl: urls.get(String(r.proof_path)) ?? null }));
}

async function audit(admin: User, action: string, target: User | null, details: Json) {
  await db.from("admin_audit").insert({
    admin_id: admin.id,
    admin_name: admin.minecraft_username,
    action,
    target_user_id: target?.id ?? null,
    target_name: target?.minecraft_username ?? null,
    details,
  });
}

async function limitAttempts(key: string, max: number, minutes: number, countSuccess = false) {
  let query = db.from("auth_attempts").select("id", { count: "exact", head: true })
    .eq("attempt_key", key).gt("created_at", new Date(Date.now() - minutes * 60_000).toISOString());
  if (!countSuccess) query = query.eq("success", false);
  const { count } = await query;
  if ((count ?? 0) >= max) throw new Fail(`Demasiados intentos. Espera ${minutes} minutos y vuelve a probar.`, "rate_limited", 429);
}

async function recordAttempt(key: string, success: boolean) {
  await db.from("auth_attempts").insert({ attempt_key: key, success });
}

function publicConfig(): Json {
  return {
    yapeNumber: YAPE_NUMBER,
    priceRegular: PRICE_REGULAR,
    priceLate: PRICE_LATE,
    priceNow: currentPrice(),
    deadline: PRICE_DEADLINE.toISOString(),
    discordInvite: DISCORD_INVITE,
    webUrl: WEB_URL,
  };
}

function currentPrice(): number {
  return Date.now() < PRICE_DEADLINE.getTime() ? PRICE_REGULAR : PRICE_LATE;
}

// ---------- Validacion ----------

function validateUsername(username: string) {
  if (!/^[A-Za-z0-9_]{5,16}$/.test(username)) {
    throw new Fail("El nombre de Minecraft debe tener entre 5 y 16 caracteres: letras, numeros o _.", "invalid_username");
  }
}

function validatePassword(password: string) {
  if (password.length < 6) throw new Fail("La contrasena debe tener al menos 6 caracteres.", "invalid_password");
  if (password.length > 128) throw new Fail("La contrasena es demasiado larga.", "invalid_password");
}

function decodeImage(dataUrl: string): { bytes: Uint8Array; mime: string; ext: string } {
  const base64 = dataUrl.includes(",") ? dataUrl.slice(dataUrl.indexOf(",") + 1) : dataUrl;
  const bytes = base64ToBytes(base64);
  if (bytes.length === 0) throw new Fail("Adjunta la captura de tu pago.");
  if (bytes.length > MAX_PROOF_BYTES) throw new Fail("La imagen pesa mas de 6 MB. Envia una captura mas liviana.");
  // Se mira el contenido, no la extension que diga el navegador.
  if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47) return { bytes, mime: "image/png", ext: "png" };
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return { bytes, mime: "image/jpeg", ext: "jpg" };
  if (String.fromCharCode(...bytes.slice(0, 4)) === "RIFF" && String.fromCharCode(...bytes.slice(8, 12)) === "WEBP") {
    return { bytes, mime: "image/webp", ext: "webp" };
  }
  throw new Fail("La captura tiene que ser una imagen PNG, JPG o WEBP.");
}

function cleanText(value: unknown, max: number): string {
  return String(value ?? "").replace(/[\u0000-\u001f]/g, " ").trim().slice(0, max);
}

function normalizeDiscord(raw: string): string {
  return raw.trim().toLowerCase().replace(/^@/, "").replace(/#0$/, "");
}

// ---------- Criptografia ----------

/** Mismo formato que usaba el launcher: PBKDF2WithHmacSHA256$iteraciones$sal$hash (base64). */
async function hashPassword(password: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const hash = await pbkdf2(password, salt, PASSWORD_ITERATIONS);
  return `PBKDF2WithHmacSHA256$${PASSWORD_ITERATIONS}$${bytesToBase64(salt)}$${bytesToBase64(hash)}`;
}

async function verifyPassword(password: string, encoded: string | null | undefined): Promise<boolean> {
  const parts = String(encoded ?? "").split("$");
  if (parts.length !== 4 || parts[0] !== "PBKDF2WithHmacSHA256") return false;
  const iterations = Number(parts[1]);
  if (!Number.isInteger(iterations) || iterations < 1000 || iterations > 1_000_000) return false;
  const expected = base64ToBytes(parts[3]);
  const actual = await pbkdf2(password, base64ToBytes(parts[2]), iterations, expected.length * 8);
  if (actual.length !== expected.length) return false;
  let diff = 0;
  for (let i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i];
  return diff === 0;
}

async function pbkdf2(password: string, salt: Uint8Array, iterations: number, bits = 256): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  return new Uint8Array(await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations }, key, bits));
}

async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomString(length: number, alphabet: string): string {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return [...bytes].map((b) => alphabet[b % alphabet.length]).join("");
}

function randomPassword(): string {
  return randomString(12, "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789");
}

function base64ToBytes(base64: string): Uint8Array {
  try {
    const binary = atob(base64.replace(/\s/g, ""));
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  } catch {
    throw new Fail("Archivo invalido.");
  }
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary);
}

function formatLima(iso: string): string {
  return new Date(iso).toLocaleString("es-PE", { timeZone: "America/Lima", dateStyle: "long", timeStyle: "short" });
}

function secretKey(): string {
  try {
    const keys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") ?? "{}");
    const key = keys.default ?? Object.values(keys)[0];
    if (typeof key === "string" && key) return key;
  } catch {
    // sigue con la clave antigua
  }
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
}

function reply(payload: Json, status = 200): Response {
  return new Response(JSON.stringify(payload), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}
