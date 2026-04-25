-- Supabase: SQL Editor → run once. Project settings → copy Project URL + anon public key
-- into local.properties as SUPABASE_URL and SUPABASE_ANON_KEY, then rebuild the app.
--
-- POC RLS: anyone with the anon key can read/write. Tighten before production
-- (Supabase Auth, column-level rules, or Edge Functions with service role only).

create table if not exists public.priyatra_state (
  id int primary key,
  catalog_json text not null,
  -- Comma-separated normalized digits, e.g. 9432748575,8334809635,7003438191
  admin_phone_digits text,
  updated_at timestamptz not null default now()
);

alter table public.priyatra_state enable row level security;

drop policy if exists "priyatra_state_all_poc" on public.priyatra_state;
create policy "priyatra_state_all_poc" on public.priyatra_state
  for all
  using (true)
  with check (true);

grant select, insert, update, delete on public.priyatra_state to anon, authenticated;
