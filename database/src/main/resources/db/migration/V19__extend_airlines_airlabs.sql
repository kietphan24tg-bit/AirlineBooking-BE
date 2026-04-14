alter table airlines
    add column country_code varchar(2),
    add column country varchar(150),
    add column city varchar(150),
    add column updated_at timestamp with time zone not null default current_timestamp;

update airlines set updated_at = created_at;
