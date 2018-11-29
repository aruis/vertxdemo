create table commodity
(
  id varchar default uuid_generate_v4() not null
    constraint commodity_pk
      primary key,
  v_name varchar,
  n_price numeric,
  v_info varchar,
  b_sell boolean default false
);

alter table commodity owner to postgres;



create table orders
(
  id varchar default uuid_generate_v4() not null
    constraint orders_pk
      primary key,
  id_at_commodity varchar,
  t_create timestamp default now()
);

alter table orders owner to postgres;


insert into commodity (n_price)
select (random() * 100)::int
from generate_series(1, 10000);


update commodity set v_name = 'name_'||n_price;
update commodity set v_info = md5(v_name::text);

CREATE or replace FUNCTION miao() RETURNS varchar AS
$$
declare
  id_at_commodity varchar;
  id_at_order     varchar;
BEGIN

  select id into id_at_commodity from commodity where b_sell = false for update skip locked limit 1;
  if id_at_commodity notnull
  then
    update commodity set b_sell = true where id = id_at_commodity;
    execute 'insert into orders (id_at_commodity) values ($1) RETURNING id' into id_at_order using id_at_commodity;
  end if;

  return id_at_order;
END;
$$ LANGUAGE plpgsql;

