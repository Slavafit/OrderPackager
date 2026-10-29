-- Cloud synchronization for the cyclic positions directory.
-- Run this file once in Supabase SQL Editor after add_clients_sync.sql.

create table if not exists public.order_packer_cyclic_items (
    owner_id uuid not null references auth.users(id) on delete cascade,
    name text not null check (btrim(name) <> ''),
    position integer not null check (position >= 0),
    updated_at timestamptz not null default now(),
    primary key (owner_id, position)
);

create unique index if not exists order_packer_cyclic_items_owner_name_idx
    on public.order_packer_cyclic_items (owner_id, lower(btrim(name)));

alter table public.order_packer_cyclic_items enable row level security;
revoke all on table public.order_packer_cyclic_items from public, anon, authenticated;

drop policy if exists "Cyclic item owner can read" on public.order_packer_cyclic_items;
drop policy if exists "Cyclic item owner can insert" on public.order_packer_cyclic_items;
drop policy if exists "Cyclic item owner can update" on public.order_packer_cyclic_items;
drop policy if exists "Cyclic item owner can delete" on public.order_packer_cyclic_items;

create policy "Cyclic item owner can read" on public.order_packer_cyclic_items
for select to authenticated using (owner_id = auth.uid());
create policy "Cyclic item owner can insert" on public.order_packer_cyclic_items
for insert to authenticated with check (owner_id = auth.uid());
create policy "Cyclic item owner can update" on public.order_packer_cyclic_items
for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "Cyclic item owner can delete" on public.order_packer_cyclic_items
for delete to authenticated using (owner_id = auth.uid());

create or replace function public.order_packer_cyclic_items_pull()
returns table(name text, position integer)
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select item.name, item.position
    from public.order_packer_cyclic_items as item
    where item.owner_id = auth.uid()
    order by item.position;
$$;

create or replace function public.order_packer_cyclic_items_replace(p_items jsonb)
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

    if jsonb_typeof(p_items) <> 'array' then
        raise exception 'p_items must be a JSON array' using errcode = '22023';
    end if;

    delete from public.order_packer_cyclic_items
    where owner_id = current_user_id;

    insert into public.order_packer_cyclic_items (owner_id, name, position, updated_at)
    select
        current_user_id,
        btrim(entry.value->>'name'),
        (entry.ordinality - 1)::integer,
        now()
    from jsonb_array_elements(p_items) with ordinality as entry(value, ordinality)
    where btrim(coalesce(entry.value->>'name', '')) <> '';
end;
$$;

revoke all on function public.order_packer_cyclic_items_pull() from public, anon;
revoke all on function public.order_packer_cyclic_items_replace(jsonb) from public, anon;
grant execute on function public.order_packer_cyclic_items_pull() to authenticated;
grant execute on function public.order_packer_cyclic_items_replace(jsonb) to authenticated;
