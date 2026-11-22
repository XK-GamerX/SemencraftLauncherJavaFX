-- Cuentas, pagos por Yape y panel de admins (aplicado el 22-11-2026 en el proyecto semencraft-backend).
-- Registro de las migraciones aplicadas, en orden. La API que usa estas tablas esta en
-- backend/supabase/functions/sc-api/index.ts.

-- ===== 1. Acceso, pagos, sesiones e historial =====
alter table public.app_users
  add column if not exists role text not null default 'player',
  add column if not exists access_status text not null default 'pending',
  add column if not exists banned boolean not null default false,
  add column if not exists ban_reason text,
  add column if not exists banned_at timestamptz,
  add column if not exists banned_until timestamptz,
  add column if not exists banned_by uuid,
  add column if not exists is_test boolean not null default false,
  add column if not exists expires_at timestamptz,
  add column if not exists registered_via text not null default 'discord',
  add column if not exists admin_note text;
alter table public.app_users
  add constraint app_users_role_check check (role in ('player', 'admin')),
  add constraint app_users_access_status_check check (access_status in ('pending', 'granted', 'revoked')),
  add constraint app_users_registered_via_check check (registered_via in ('discord', 'web', 'admin'));
update public.app_users set access_status = 'granted';
update public.app_users set role = 'admin' where minecraft_username_normalized in ('khelmc', 'semmie');

create table if not exists public.app_sessions (
  token_hash text primary key,
  user_id uuid not null references public.app_users(id) on delete cascade,
  client text not null check (client in ('launcher', 'web')),
  created_at timestamptz not null default now(),
  last_used_at timestamptz not null default now(),
  expires_at timestamptz not null
);
create index if not exists app_sessions_user_idx on public.app_sessions(user_id);

create table if not exists public.payments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.app_users(id) on delete cascade,
  amount_soles numeric(6, 2) not null,
  proof_path text,
  operation_code text,
  note text,
  method text not null default 'yape_proof' check (method in ('yape_proof', 'manual')),
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  reviewed_by uuid references public.app_users(id) on delete set null,
  reviewed_at timestamptz,
  review_note text,
  admin_notified_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists payments_user_idx on public.payments(user_id);
create index if not exists payments_status_idx on public.payments(status, created_at);

create table if not exists public.admin_audit (
  id bigserial primary key,
  admin_id uuid references public.app_users(id) on delete set null,
  admin_name text not null,
  action text not null,
  target_user_id uuid references public.app_users(id) on delete set null,
  target_name text,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists admin_audit_created_idx on public.admin_audit(created_at desc);

create table if not exists public.auth_attempts (
  id bigserial primary key,
  attempt_key text not null,
  success boolean not null,
  created_at timestamptz not null default now()
);
create index if not exists auth_attempts_key_idx on public.auth_attempts(attempt_key, created_at desc);

alter table public.app_sessions enable row level security;
alter table public.payments enable row level security;
alter table public.admin_audit enable row level security;
alter table public.auth_attempts enable row level security;
revoke all on public.app_sessions, public.payments, public.admin_audit, public.auth_attempts from anon, authenticated;
revoke all on sequence public.admin_audit_id_seq, public.auth_attempts_id_seq from anon, authenticated;

-- Las cuentas creadas en la web o por un admin no tienen Discord.
alter table public.app_users alter column discord_username drop not null;
alter table public.app_users alter column discord_lookup_normalized drop not null;
alter table public.app_users alter column discord_user_id drop not null;

-- Usuario de la sesion enviada en la cabecera x-sc-session (null si no hay, vencio o no tiene acceso).
create or replace function public.sc_session_user()
returns uuid language sql stable security definer set search_path = public, extensions as $$
  select s.user_id
  from public.app_sessions s
  join public.app_users u on u.id = s.user_id
  where s.token_hash = encode(extensions.digest(coalesce(current_setting('request.headers', true)::json ->> 'x-sc-session', ''), 'sha256'), 'hex')
    and s.expires_at > now()
    and u.access_status = 'granted'
    and not (u.banned and (u.banned_until is null or u.banned_until > now()))
    and (u.expires_at is null or u.expires_at > now())
$$;
revoke all on function public.sc_session_user() from public;
grant execute on function public.sc_session_user() to anon, authenticated, service_role;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('payment-proofs', 'payment-proofs', false, 6291456, array['image/png', 'image/jpeg', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

-- Limpieza cada 5 minutos: cuentas de prueba vencidas (y sus equipos), sesiones vencidas e intentos viejos.
create or replace function public.sc_cleanup()
returns void language sql security definer set search_path = public as $$
  delete from public.teams where founder_user_id in (select id from public.app_users where is_test and expires_at < now());
  delete from public.app_users where is_test and expires_at < now();
  delete from public.app_sessions where expires_at < now();
  delete from public.auth_attempts where created_at < now() - interval '1 day';
$$;
revoke all on function public.sc_cleanup() from public;
create extension if not exists pg_cron;
select cron.schedule('semencraft-cleanup', '*/5 * * * *', 'select public.sc_cleanup()');

-- ===== 2. Candado: la clave publica ya no lee ni escribe todo =====
revoke all on function public.register_user(text, text, text, text, text) from public, anon, authenticated;
revoke all on function public.create_team_for_user(uuid, text, text, text) from public, anon, authenticated;
revoke all on function public.invite_user_to_team(uuid, uuid, uuid) from public, anon, authenticated;

drop policy if exists "public read users" on public.app_users;
drop policy if exists "public read discord cache" on public.discord_members_cache;
drop policy if exists "public read notifications" on public.notifications;
drop policy if exists "public read invites" on public.team_invites;
drop policy if exists "public read team members" on public.team_members;
drop policy if exists "public read teams" on public.teams;
revoke all on public.app_users, public.discord_members_cache, public.discord_role_jobs, public.notifications,
  public.team_invites, public.team_members, public.teams from anon, authenticated;

grant select (id, minecraft_username, minecraft_username_normalized, discord_username, team_id, presence_status, last_seen_at, is_registered, created_at)
  on public.app_users to anon, authenticated;
grant update (team_id, presence_status, last_seen_at) on public.app_users to anon, authenticated;
create policy "sc read public profile" on public.app_users for select to anon, authenticated using (true);
create policy "sc update own row or founder clears" on public.app_users for update to anon, authenticated
  using (id = public.sc_session_user()
    or team_id in (select t.id from public.teams t where t.founder_user_id = public.sc_session_user()))
  with check ((id = public.sc_session_user()
      and (team_id is null or team_id in (select m.team_id from public.team_members m where m.user_id = public.sc_session_user())))
    or (id <> public.sc_session_user() and team_id is null and public.sc_session_user() is not null));

grant select, insert, update on public.teams to anon, authenticated;
create policy "sc read teams" on public.teams for select to anon, authenticated using (true);
create policy "sc create own team" on public.teams for insert to anon, authenticated
  with check (founder_user_id = public.sc_session_user());
create policy "sc founder edits team" on public.teams for update to anon, authenticated
  using (founder_user_id = public.sc_session_user())
  with check (founder_user_id = public.sc_session_user()
    or founder_user_id in (select m.user_id from public.team_members m where m.team_id = teams.id));

grant select, insert, update, delete on public.team_members to anon, authenticated;
create policy "sc read members" on public.team_members for select to anon, authenticated using (true);
create policy "sc join team" on public.team_members for insert to anon, authenticated
  with check (user_id = public.sc_session_user()
    and (team_id in (select t.id from public.teams t where t.founder_user_id = public.sc_session_user())
      or exists (select 1 from public.team_invites i
                 where i.team_id = team_members.team_id and i.invited_user_id = public.sc_session_user() and i.status = 'pending')));
create policy "sc founder updates members" on public.team_members for update to anon, authenticated
  using (team_id in (select t.id from public.teams t where t.founder_user_id = public.sc_session_user()))
  with check (team_id in (select t.id from public.teams t where t.founder_user_id = public.sc_session_user()));
create policy "sc leave or kick" on public.team_members for delete to anon, authenticated
  using (user_id = public.sc_session_user()
    or team_id in (select t.id from public.teams t where t.founder_user_id = public.sc_session_user()));

grant select, insert, update on public.team_invites to anon, authenticated;
create policy "sc read own invites" on public.team_invites for select to anon, authenticated
  using (invited_user_id = public.sc_session_user() or invited_by_user_id = public.sc_session_user()
    or team_id in (select t.id from public.teams t where t.founder_user_id = public.sc_session_user()));
create policy "sc founder invites" on public.team_invites for insert to anon, authenticated
  with check (invited_by_user_id = public.sc_session_user()
    and team_id in (select t.id from public.teams t where t.founder_user_id = public.sc_session_user()));
create policy "sc respond invite" on public.team_invites for update to anon, authenticated
  using (invited_user_id = public.sc_session_user()
    or team_id in (select t.id from public.teams t where t.founder_user_id = public.sc_session_user()));

alter table public.notifications add column if not exists created_by uuid default public.sc_session_user();
grant select, insert, update on public.notifications to anon, authenticated;
create policy "sc read own notifications" on public.notifications for select to anon, authenticated
  using (user_id = public.sc_session_user() or created_by = public.sc_session_user());
create policy "sc update own notifications" on public.notifications for update to anon, authenticated
  using (user_id = public.sc_session_user());
create policy "sc create team notifications" on public.notifications for insert to anon, authenticated
  with check (public.sc_session_user() is not null and type in ('info', 'team_invite'));
