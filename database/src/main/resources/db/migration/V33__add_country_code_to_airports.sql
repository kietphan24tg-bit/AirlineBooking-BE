alter table airports
    add column if not exists country_code varchar(2);

create index if not exists idx_airports_country_code
    on airports(country_code);

