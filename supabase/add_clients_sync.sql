-- Cloud synchronization for the clients directory.
-- Run this file once in Supabase SQL Editor after migrate_to_auth.sql.

create table if not exists public.order_packer_clients (
    owner_id uuid not null references auth.users(id) on delete cascade,
    last_name text not null check (btrim(last_name) <> ''),
    updated_at timestamptz not null default now(),
    primary key (owner_id, last_name)
);

create unique index if not exists order_packer_clients_owner_name_idx
    on public.order_packer_clients (owner_id, lower(btrim(last_name)));

alter table public.order_packer_clients enable row level security;
revoke all on table public.order_packer_clients from public, anon, authenticated;

drop policy if exists "Client owner can read" on public.order_packer_clients;
drop policy if exists "Client owner can insert" on public.order_packer_clients;
drop policy if exists "Client owner can update" on public.order_packer_clients;
drop policy if exists "Client owner can delete" on public.order_packer_clients;

create policy "Client owner can read" on public.order_packer_clients
for select to authenticated using (owner_id = auth.uid());
create policy "Client owner can insert" on public.order_packer_clients
for insert to authenticated with check (owner_id = auth.uid());
create policy "Client owner can update" on public.order_packer_clients
for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "Client owner can delete" on public.order_packer_clients
for delete to authenticated using (owner_id = auth.uid());

create or replace function public.order_packer_clients_pull()
returns table(last_name text)
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select client.last_name
    from public.order_packer_clients as client
    where client.owner_id = auth.uid()
    order by lower(client.last_name);
$$;

create or replace function public.order_packer_clients_replace(p_clients jsonb)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    current_user_id uuid := auth.uid();
begin
    if current_user_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;

    if jsonb_typeof(p_clients) <> 'array' then
        raise exception 'p_clients must be a JSON array' using errcode = '22023';
    end if;

    delete from public.order_packer_clients
    where owner_id = current_user_id;

    insert into public.order_packer_clients (owner_id, last_name, updated_at)
    select
        current_user_id,
        btrim(entry.value->>'last_name'),
        now()
    from jsonb_array_elements(p_clients) as entry(value)
    where btrim(coalesce(entry.value->>'last_name', '')) <> '';
end;
$$;

revoke all on function public.order_packer_clients_pull() from public, anon;
revoke all on function public.order_packer_clients_replace(jsonb) from public, anon;
grant execute on function public.order_packer_clients_pull() to authenticated;
grant execute on function public.order_packer_clients_replace(jsonb) to authenticated;
