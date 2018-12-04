create table commodity
(
  id int auto_increment
    primary key,
  v_name varchar(50) null,
  n_price decimal null,
  v_info varchar(50) null,
  b_sell tinyint(1) default 0 null
);

create table commodity2
(
  id int auto_increment
    primary key,
  v_name varchar(50) null,
  n_price decimal null,
  v_info varchar(50) null,
  i_quantity int default 0 null,
  i_quantity_sell int default 0 null
);

create table orders
(
  id int auto_increment
    primary key,
  id_at_commodity int null,
  t_create timestamp default CURRENT_TIMESTAMP null
);

