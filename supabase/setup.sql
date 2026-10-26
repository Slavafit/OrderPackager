-- Order Packer cloud storage for Supabase.
-- Run the whole file once in Supabase SQL Editor.

create extension if not exists pgcrypto;

create schema if not exists order_packer_private;
revoke all on schema order_packer_private from public, anon, authenticated;

create table if not exists order_packer_private.config (
    singleton boolean primary key default true check (singleton),
    sync_token_hash bytea not null
);

create table if not exists public.order_packer_orders (
    box_number text primary key,
    batch_number text not null default '',
    client_last_name text not null,
    created_at_ms bigint not null,
    box_length real not null default 0,
    box_width real not null default 0,
    box_height real not null default 0,
    is_completed boolean not null default true,
    positions jsonb not null default '[]'::jsonb,
    updated_at timestamptz not null default now(),
    constraint order_packer_positions_array check (jsonb_typeof(positions) = 'array')
);

alter table public.order_packer_orders enable row level security;
revoke all on table public.order_packer_orders from anon, authenticated;

create or replace function public.order_packer_token_is_valid(p_sync_token text)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, order_packer_private
as $$
    select exists (
        select 1
        from order_packer_private.config
        where sync_token_hash = extensions.digest(p_sync_token, 'sha256')
    );
$$;

revoke all on function public.order_packer_token_is_valid(text) from public;

create or replace function public.order_packer_upsert(
    p_sync_token text,
    p_order jsonb
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if not public.order_packer_token_is_valid(p_sync_token) then
        raise exception 'Invalid sync token' using errcode = '28000';
    end if;

    insert into public.order_packer_orders (
        box_number,
        batch_number,
        client_last_name,
        created_at_ms,
        box_length,
        box_width,
        box_height,
        is_completed,
        positions,
        updated_at
    ) values (
        p_order->>'box_number',
        coalesce(p_order->>'batch_number', ''),
        p_order->>'client_last_name',
        (p_order->>'created_at_ms')::bigint,
        coalesce((p_order->>'box_length')::real, 0),
        coalesce((p_order->>'box_width')::real, 0),
        coalesce((p_order->>'box_height')::real, 0),
        coalesce((p_order->>'is_completed')::boolean, true),
        coalesce(p_order->'positions', '[]'::jsonb),
        now()
    )
    on conflict (box_number) do update set
        batch_number = excluded.batch_number,
        client_last_name = excluded.client_last_name,
        created_at_ms = excluded.created_at_ms,
        box_length = excluded.box_length,
        box_width = excluded.box_width,
        box_height = excluded.box_height,
        is_completed = excluded.is_completed,
        positions = excluded.positions,
        updated_at = now();
end;
$$;

create or replace function public.order_packer_pull(p_sync_token text)
returns setof public.order_packer_orders
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if not public.order_packer_token_is_valid(p_sync_token) then
        raise exception 'Invalid sync token' using errcode = '28000';
    end if;

    return query
        select *
        from public.order_packer_orders
        order by created_at_ms;
end;
$$;

revoke all on function public.order_packer_upsert(text, jsonb) from public;
revoke all on function public.order_packer_pull(text) from public;
grant execute on function public.order_packer_upsert(text, jsonb) to anon, authenticated;
grant execute on function public.order_packer_pull(text) to anon, authenticated;

-- The result grid displays the token only on the first run.
-- Copy it to local.properties as SUPABASE_SYNC_TOKEN=<value>.
with generated as materialized (
    select encode(extensions.gen_random_bytes(32), 'hex') as token
), inserted as (
    insert into order_packer_private.config (singleton, sync_token_hash)
    select true, extensions.digest(token, 'sha256')
    from generated
    on conflict (singleton) do nothing
    returning singleton
)
select token as sync_token
from generated
where exists (select 1 from inserted);
