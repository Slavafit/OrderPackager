-- Run once in Supabase SQL Editor before distributing v1.25.
-- Idempotent: reruns retain the current cloud catalog and all custom products.
begin;

create table if not exists public.order_packer_catalog (
    id integer primary key check (id = 1),
    payload jsonb not null check (jsonb_typeof(payload) = 'object'),
    updated_at timestamptz not null default now()
);
create table if not exists public.order_packer_custom_products (
    owner_id uuid not null references auth.users(id) on delete cascade,
    id uuid not null,
    payload jsonb not null check (jsonb_typeof(payload) = 'object'),
    created_at timestamptz not null default now(),
    primary key (owner_id, id)
);

alter table public.order_packer_catalog enable row level security;
alter table public.order_packer_custom_products enable row level security;
drop policy if exists "Catalog authenticated read" on public.order_packer_catalog;
create policy "Catalog authenticated read" on public.order_packer_catalog
    for select to authenticated using (true);
drop policy if exists "Custom products owner read" on public.order_packer_custom_products;
create policy "Custom products owner read" on public.order_packer_custom_products
    for select to authenticated using (owner_id = auth.uid());
revoke all on public.order_packer_catalog from public, anon, authenticated;
revoke all on public.order_packer_custom_products from public, anon, authenticated;
grant select on public.order_packer_catalog to authenticated;
grant select on public.order_packer_custom_products to authenticated;

insert into public.order_packer_catalog (id, payload)
values (1, $catalog_seed${
  "schemaVersion": 1,
  "languages": [
    "ru",
    "en",
    "es"
  ],
  "categories": [
    {
      "id": "clothing",
      "names": {
        "ru": "Одежда",
        "en": "Clothing",
        "es": "Ropa"
      },
      "sortOrder": 10
    },
    {
      "id": "footwear",
      "names": {
        "ru": "Обувь",
        "en": "Footwear",
        "es": "Calzado"
      },
      "sortOrder": 20
    },
    {
      "id": "bags_accessories",
      "names": {
        "ru": "Сумки и аксессуары",
        "en": "Bags and accessories",
        "es": "Bolsos y accesorios"
      },
      "sortOrder": 30
    },
    {
      "id": "beauty",
      "names": {
        "ru": "Красота и уход",
        "en": "Beauty and personal care",
        "es": "Belleza y cuidado personal"
      },
      "sortOrder": 40
    },
    {
      "id": "toys_sports",
      "names": {
        "ru": "Игрушки и спорт",
        "en": "Toys and sports",
        "es": "Juguetes y deporte"
      },
      "sortOrder": 50
    },
    {
      "id": "electronics_appliances",
      "names": {
        "ru": "Электроника и техника",
        "en": "Electronics and appliances",
        "es": "Electrónica y electrodomésticos"
      },
      "sortOrder": 60
    },
    {
      "id": "home",
      "names": {
        "ru": "Товары для дома",
        "en": "Home goods",
        "es": "Artículos para el hogar"
      },
      "sortOrder": 70
    },
    {
      "id": "health",
      "names": {
        "ru": "Здоровье",
        "en": "Health",
        "es": "Salud"
      },
      "sortOrder": 80
    },
    {
      "id": "food_drinks",
      "names": {
        "ru": "Продукты питания и напитки",
        "en": "Food and beverages",
        "es": "Alimentos y bebidas"
      },
      "sortOrder": 90
    },
    {
      "id": "misc",
      "names": {
        "ru": "Прочее",
        "en": "Other",
        "es": "Otros"
      },
      "sortOrder": 100
    }
  ],
  "products": [
    {
      "id": "clothing_tops_shirts",
      "categoryId": "clothing",
      "names": {
        "ru": "Футболки, майки, топы, рубашки и блузки",
        "en": "T-shirts, tank tops, tops, shirts and blouses",
        "es": "Camisetas, camisas y tops"
      },
      "reportNameEn": "T-shirts, tank tops, tops, shirts and blouses",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 10,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "clothing_sweaters_hoodies",
      "categoryId": "clothing",
      "names": {
        "ru": "Свитеры, кардиганы, худи",
        "en": "Sweaters, cardigans and hoodies",
        "es": "Jerséis y sudaderas"
      },
      "reportNameEn": "Sweaters, cardigans and hoodies",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 20,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "clothing_trousers_shorts",
      "categoryId": "clothing",
      "names": {
        "ru": "Брюки, джинсы, шорты",
        "en": "Trousers, jeans and shorts",
        "es": "Pantalones y pantalones cortos"
      },
      "reportNameEn": "Trousers, jeans and shorts",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 30,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "clothing_dresses_skirts",
      "categoryId": "clothing",
      "names": {
        "ru": "Платья, юбки, комбинезоны",
        "en": "Dresses, skirts and jumpsuits",
        "es": "Vestidos, faldas y monos"
      },
      "reportNameEn": "Dresses, skirts and jumpsuits",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 40,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "clothing_coats_jackets",
      "categoryId": "clothing",
      "names": {
        "ru": "Куртки, пальто, пуховики",
        "en": "Jackets, coats and down jackets",
        "es": "Abrigos y chaquetas"
      },
      "reportNameEn": "Jackets, coats and down jackets",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 50,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "clothing_suits_blazers",
      "categoryId": "clothing",
      "names": {
        "ru": "Костюмы и пиджаки",
        "en": "Suits and blazers",
        "es": "Trajes y americanas"
      },
      "reportNameEn": "Suits and blazers",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 60,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "clothing_sportswear",
      "categoryId": "clothing",
      "names": {
        "ru": "Спортивная одежда",
        "en": "Sportswear",
        "es": "Ropa deportiva"
      },
      "reportNameEn": "Sportswear",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 70,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "clothing_underwear_sleepwear",
      "categoryId": "clothing",
      "names": {
        "ru": "Нижнее бельё, пижамы, купальники",
        "en": "Underwear, pajamas and swimwear",
        "es": "Ropa interior, pijamas y bañadores"
      },
      "reportNameEn": "Underwear, pajamas and swimwear",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 80,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "clothing_baby",
      "categoryId": "clothing",
      "names": {
        "ru": "Одежда для младенцев",
        "en": "Baby clothing",
        "es": "Ropa para bebés"
      },
      "reportNameEn": "Baby clothing",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 90,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "footwear",
      "categoryId": "footwear",
      "names": {
        "ru": "Обувь",
        "en": "Footwear",
        "es": "Calzado"
      },
      "reportNameEn": "Footwear",
      "unitSingular": "pair",
      "unitPlural": "pairs",
      "sortOrder": 10,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "bags_luggage_wallets",
      "categoryId": "bags_accessories",
      "names": {
        "ru": "Сумки, чемоданы, кошельки",
        "en": "Bags, suitcases and wallets",
        "es": "Bolsos, maletas y carteras"
      },
      "reportNameEn": "Bags, suitcases and wallets",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 10,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "headwear",
      "categoryId": "bags_accessories",
      "names": {
        "ru": "Головные уборы",
        "en": "Hats and other headwear",
        "es": "Sombreros y demás tocados"
      },
      "reportNameEn": "Hats and other headwear",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 20,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "costume_jewelry",
      "categoryId": "bags_accessories",
      "names": {
        "ru": "Бижутерия",
        "en": "Costume jewelry",
        "es": "Bisutería"
      },
      "reportNameEn": "Costume jewelry",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 30,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "watches",
      "categoryId": "bags_accessories",
      "names": {
        "ru": "Часы",
        "en": "Watches",
        "es": "Relojes"
      },
      "reportNameEn": "Watches",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 40,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "glasses",
      "categoryId": "bags_accessories",
      "names": {
        "ru": "Очки",
        "en": "Glasses",
        "es": "Gafas"
      },
      "reportNameEn": "Glasses",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 50,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "beauty_cosmetics_skin",
      "categoryId": "beauty",
      "names": {
        "ru": "Косметика и уход за кожей",
        "en": "Cosmetics and skin care products",
        "es": "Cosméticos y productos para el cuidado de la piel"
      },
      "reportNameEn": "Cosmetics and skin care products",
      "unitSingular": "package",
      "unitPlural": "packages",
      "sortOrder": 10,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "beauty_perfume",
      "categoryId": "beauty",
      "names": {
        "ru": "Парфюмерия",
        "en": "Perfumes and eau de toilette",
        "es": "Perfumes y aguas de tocador"
      },
      "reportNameEn": "Perfumes and eau de toilette",
      "unitSingular": "bottle",
      "unitPlural": "bottles",
      "sortOrder": 20,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "beauty_hair",
      "categoryId": "beauty",
      "names": {
        "ru": "Шампуни и средства для волос",
        "en": "Hair care products",
        "es": "Productos para el cuidado del cabello"
      },
      "reportNameEn": "Hair care products",
      "unitSingular": "bottle",
      "unitPlural": "bottles",
      "sortOrder": 30,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "toys",
      "categoryId": "toys_sports",
      "names": {
        "ru": "Игрушки",
        "en": "Toys",
        "es": "Juguetes"
      },
      "reportNameEn": "Toys",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 10,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "sporting_goods",
      "categoryId": "toys_sports",
      "names": {
        "ru": "Спортивные товары",
        "en": "Sporting goods",
        "es": "Artículos de deporte"
      },
      "reportNameEn": "Sporting goods",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 20,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "electronics",
      "categoryId": "electronics_appliances",
      "names": {
        "ru": "Электроника",
        "en": "Electronic devices",
        "es": "Aparatos electrónicos"
      },
      "reportNameEn": "Electronic devices",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 10,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "household_appliances",
      "categoryId": "electronics_appliances",
      "names": {
        "ru": "Бытовая техника",
        "en": "Household appliances",
        "es": "Electrodomésticos"
      },
      "reportNameEn": "Household appliances",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 20,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "home_textiles",
      "categoryId": "home",
      "names": {
        "ru": "Текстиль для дома",
        "en": "Home textiles",
        "es": "Artículos textiles para el hogar"
      },
      "reportNameEn": "Home textiles",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 10,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "tableware_kitchenware",
      "categoryId": "home",
      "names": {
        "ru": "Посуда",
        "en": "Tableware and kitchen utensils",
        "es": "Vajilla y utensilios de cocina"
      },
      "reportNameEn": "Tableware and kitchen utensils",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 20,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "vitamins_supplements",
      "categoryId": "health",
      "names": {
        "ru": "Витамины и БАДы",
        "en": "Vitamins and dietary supplements",
        "es": "Vitaminas y complementos alimenticios"
      },
      "reportNameEn": "Vitamins and dietary supplements",
      "unitSingular": "package",
      "unitPlural": "packages",
      "sortOrder": 10,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "food_products",
      "categoryId": "food_drinks",
      "names": {
        "ru": "Продукты питания",
        "en": "Food products",
        "es": "Productos alimenticios"
      },
      "reportNameEn": "Food products",
      "unitSingular": "package",
      "unitPlural": "packages",
      "sortOrder": 10,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "beverages",
      "categoryId": "food_drinks",
      "names": {
        "ru": "Напитки",
        "en": "Beverages",
        "es": "Bebidas"
      },
      "reportNameEn": "Beverages",
      "unitSingular": "bottle",
      "unitPlural": "bottles",
      "sortOrder": 20,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "documents",
      "categoryId": "misc",
      "names": {
        "ru": "Документы",
        "en": "Documents",
        "es": "Documentos"
      },
      "reportNameEn": "Documents",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 10,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "optical_discs",
      "categoryId": "misc",
      "names": {
        "ru": "Диски (CD/DVD)",
        "en": "CDs and DVDs",
        "es": "Discos (CD/DVD)"
      },
      "reportNameEn": "CDs and DVDs",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 20,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "vinyl_records",
      "categoryId": "misc",
      "names": {
        "ru": "Пластинки",
        "en": "Vinyl records",
        "es": "Discos de vinilo"
      },
      "reportNameEn": "Vinyl records",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 30,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "magazines",
      "categoryId": "misc",
      "names": {
        "ru": "Журналы",
        "en": "Magazines",
        "es": "Revistas"
      },
      "reportNameEn": "Magazines",
      "unitSingular": "pc",
      "unitPlural": "pcs",
      "sortOrder": 40,
      "custom": false,
      "source": "built_in"
    },
    {
      "id": "gloves",
      "categoryId": "bags_accessories",
      "names": {
        "ru": "Перчатки",
        "en": "Gloves",
        "es": "Guantes"
      },
      "reportNameEn": "Gloves",
      "unitSingular": "pair",
      "unitPlural": "pairs",
      "sortOrder": 60,
      "custom": true,
      "source": "known_custom"
    },
    {
      "id": "socks",
      "categoryId": "bags_accessories",
      "names": {
        "ru": "Носки",
        "en": "Socks",
        "es": "Calcetines"
      },
      "reportNameEn": "Socks",
      "unitSingular": "pair",
      "unitPlural": "pairs",
      "sortOrder": 70,
      "custom": true,
      "source": "known_custom"
    }
  ],
  "notes": [
    "Built-in products were exported from FastScan 4.33.",
    "Gloves and Socks are known user-defined products; their original Firestore UUIDs were not available, so stable export IDs are used.",
    "Other warehouse-specific custom products require a Firestore export from warehouses/{warehouse_id}/product_catalog."
  ]
}$catalog_seed$::jsonb)
on conflict (id) do nothing;

create or replace function public.order_packer_catalog_pull()
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    current_user_id uuid := auth.uid();
    catalog jsonb;
begin
    if current_user_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;
    select payload into strict catalog from public.order_packer_catalog where id = 1;
    return jsonb_build_object(
        'catalog', catalog,
        'products', coalesce((
            select jsonb_agg(payload order by created_at, id)
            from public.order_packer_custom_products
            where owner_id = current_user_id
        ), '[]'::jsonb)
    );
end;
$$;

create or replace function public.order_packer_catalog_upsert(p_products jsonb)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    current_user_id uuid := auth.uid();
    product jsonb;
    product_id uuid;
    catalog jsonb;
begin
    if current_user_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;
    if p_products is null or jsonb_typeof(p_products) <> 'array' then
        raise exception 'Products must be an array' using errcode = '22023';
    end if;
    if jsonb_array_length(p_products) > 100 then
        raise exception 'Maximum 100 products per batch' using errcode = '22023';
    end if;
    select payload into strict catalog from public.order_packer_catalog where id = 1;
    for product in select value from jsonb_array_elements(p_products) loop
        if jsonb_typeof(product) is distinct from 'object'
            or jsonb_typeof(product->'names') is distinct from 'object'
            or jsonb_typeof(product->'reportNameEn') is distinct from 'string'
            or length(btrim(coalesce(product->>'reportNameEn', ''))) not between 1 and 120
            or jsonb_typeof(product->'names'->'en') is distinct from 'string'
            or length(btrim(coalesce(product->'names'->>'en', ''))) not between 1 and 120
            or product->'custom' is distinct from 'true'::jsonb
            or product->>'source' is distinct from 'user_defined'
            or product->'sortOrder' is distinct from '10000'::jsonb then
            raise exception 'Invalid product' using errcode = '22023';
        end if;
        if exists (
            select 1 from jsonb_each(product->'names') as n
            where n.key not in ('ru', 'en', 'es') or jsonb_typeof(n.value) <> 'string'
                or length(btrim(n.value #>> '{}')) not between 1 and 120
        ) then
            raise exception 'Invalid product names' using errcode = '22023';
        end if;
        if not exists (
            select 1 from jsonb_array_elements(catalog->'categories') as c
            where c->>'id' = product->>'categoryId'
        ) then
            raise exception 'Unknown category' using errcode = '22023';
        end if;
        if not exists (
            select 1 from (values ('pc', 'pcs'), ('pair', 'pairs'),
                ('package', 'packages'), ('bottle', 'bottles')) as units(singular, plural)
            where singular = product->>'unitSingular' and plural = product->>'unitPlural'
        ) then
            raise exception 'Invalid units' using errcode = '22023';
        end if;
        product_id := (product->>'id')::uuid;
        if product_id is null or exists (
            select 1 from jsonb_array_elements(catalog->'products') as p where p->>'id' = product->>'id'
        ) then
            raise exception 'Invalid custom product ID' using errcode = '22023';
        end if;
        -- Only creation is exposed. Retries cannot overwrite an existing product.
        insert into public.order_packer_custom_products(owner_id, id, payload)
        values (current_user_id, product_id, product)
        on conflict (owner_id, id) do nothing;
    end loop;
end;
$$;

revoke all on function public.order_packer_catalog_pull() from public, anon;
revoke all on function public.order_packer_catalog_upsert(jsonb) from public, anon;
grant execute on function public.order_packer_catalog_pull() to authenticated;
grant execute on function public.order_packer_catalog_upsert(jsonb) to authenticated;
commit;
