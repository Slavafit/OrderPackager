-- Migrate Order Packer from a shared sync token to Supabase Auth.
-- Owner UID supplied by the project owner:
-- 8a3b89d8-a113-4da1-b8e5-a9acb9cc4287

alter table public.order_packer_orders
    add column if not exists owner_id uuid references auth.users(id);

update public.order_packer_orders
set owner_id = '8a3b89d8-a113-4da1-b8e5-a9acb9cc4287'::uuid
where owner_id is null;

alter table public.order_packer_orders alter column owner_id set not null;
create index if not exists order_packer_orders_owner_id_idx
    on public.order_packer_orders(owner_id);

drop function if exists public.order_packer_upsert(text, jsonb);
drop function if exists public.order_packer_pull(text);
drop function if exists public.order_packer_token_is_valid(text);

create or replace function public.order_packer_upsert(p_order jsonb)
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

    insert into public.order_packer_orders (
        box_number, batch_number, client_last_name, created_at_ms,
        box_length, box_width, box_height, is_completed, positions,
        owner_id, updated_at
    ) values (
        p_order->>'box_number', coalesce(p_order->>'batch_number', ''),
        p_order->>'client_last_name', (p_order->>'created_at_ms')::bigint,
        coalesce((p_order->>'box_length')::real, 0),
        coalesce((p_order->>'box_width')::real, 0),
        coalesce((p_order->>'box_height')::real, 0),
        coalesce((p_order->>'is_completed')::boolean, true),
        coalesce(p_order->'positions', '[]'::jsonb), current_user_id, now()
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
        updated_at = now()
    where order_packer_orders.owner_id = current_user_id;
end;
$$;

create or replace function public.order_packer_pull()
returns setof public.order_packer_orders
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if auth.uid() is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;
    return query
        select * from public.order_packer_orders
        where owner_id = auth.uid()
        order by created_at_ms;
end;
$$;

revoke all on function public.order_packer_upsert(jsonb) from public, anon;
revoke all on function public.order_packer_pull() from public, anon;
grant execute on function public.order_packer_upsert(jsonb) to authenticated;
grant execute on function public.order_packer_pull() to authenticated;

alter table public.order_packer_orders enable row level security;
drop policy if exists "Order owner can read" on public.order_packer_orders;
drop policy if exists "Order owner can insert" on public.order_packer_orders;
drop policy if exists "Order owner can update" on public.order_packer_orders;
drop policy if exists "Order owner can delete" on public.order_packer_orders;

create policy "Order owner can read" on public.order_packer_orders
for select to authenticated using (owner_id = auth.uid());
create policy "Order owner can insert" on public.order_packer_orders
for insert to authenticated with check (owner_id = auth.uid());
create policy "Order owner can update" on public.order_packer_orders
for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "Order owner can delete" on public.order_packer_orders
for delete to authenticated using (owner_id = auth.uid());

drop table if exists order_packer_private.config;
drop schema if exists order_packer_private;
