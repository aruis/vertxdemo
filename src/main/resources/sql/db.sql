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

