insert into aircraft (model, manufacturer, total_seats, created_at, updated_at)
values ('A320-200', 'Airbus', 150, current_timestamp, current_timestamp),
       ('A321-200', 'Airbus', 192, current_timestamp, current_timestamp),
       ('A321neo', 'Airbus', 220, current_timestamp, current_timestamp),
       ('B787-9', 'Boeing', 274, current_timestamp, current_timestamp),
       ('A350-900', 'Airbus', 305, current_timestamp, current_timestamp),
       ('A330-300', 'Airbus', 280, current_timestamp, current_timestamp);

insert into aircraft_layout (aircraft_id, total_rows, columns, business_rows, created_at)
select a.id, v.total_rows, v.columns, v.business_rows, current_timestamp
from aircraft a
join (
    values ('A320-200', 30, 'ABCDEF', 2),
           ('A321-200', 36, 'ABCDEF', 4),
           ('A321neo', 40, 'ABCDEF', 4),
           ('B787-9', 40, 'ABCDEFGHJ', 8),
           ('A350-900', 42, 'ABCDEFGHJ', 8),
           ('A330-300', 42, 'ABCDEFGH', 6)
) as v(model, total_rows, columns, business_rows)
    on v.model = a.model;

do
$$
declare
    aid bigint;
begin
    -- A320-200: 150 seats (8 business, 142 economy)
    select id into aid from aircraft where model = 'A320-200';
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'BUSINESS', current_timestamp, current_timestamp
    from generate_series(1, 2) as r,
         unnest(array ['A', 'C', 'D', 'F']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(3, 4) as r,
         unnest(array ['A', 'B', 'C', 'D', 'E', 'F']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(5, 30) as r,
         unnest(array ['A', 'B', 'C', 'D', 'F']) as c;

    -- A321-200: 192 seats (16 business, 176 economy)
    select id into aid from aircraft where model = 'A321-200';
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'BUSINESS', current_timestamp, current_timestamp
    from generate_series(1, 4) as r,
         unnest(array ['A', 'C', 'D', 'F']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(5, 20) as r,
         unnest(array ['A', 'B', 'C', 'D', 'F']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(21, 36) as r,
         unnest(array ['A', 'B', 'C', 'D', 'E', 'F']) as c;

    -- A321neo: 220 seats (16 business, 204 economy)
    select id into aid from aircraft where model = 'A321neo';
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'BUSINESS', current_timestamp, current_timestamp
    from generate_series(1, 4) as r,
         unnest(array ['A', 'C', 'D', 'F']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(5, 28) as r,
         unnest(array ['A', 'B', 'C', 'D', 'E', 'F']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(29, 40) as r,
         unnest(array ['A', 'B', 'C', 'D', 'F']) as c;

    -- B787-9: 274 seats (48 business, 226 economy)
    select id into aid from aircraft where model = 'B787-9';
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'BUSINESS', current_timestamp, current_timestamp
    from generate_series(1, 8) as r,
         unnest(array ['A', 'C', 'D', 'F', 'G', 'J']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(9, 10) as r,
         unnest(array ['A', 'B', 'C', 'D', 'E', 'F', 'H', 'J']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(11, 40) as r,
         unnest(array ['A', 'B', 'C', 'D', 'E', 'F', 'H']) as c;

    -- A350-900: 305 seats (48 business, 257 economy)
    select id into aid from aircraft where model = 'A350-900';
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'BUSINESS', current_timestamp, current_timestamp
    from generate_series(1, 8) as r,
         unnest(array ['A', 'C', 'D', 'F', 'G', 'J']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(9, 27) as r,
         unnest(array ['A', 'B', 'C', 'D', 'E', 'F', 'H', 'J']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(28, 42) as r,
         unnest(array ['A', 'B', 'C', 'D', 'E', 'F', 'H']) as c;

    -- A330-300: 280 seats (36 business, 244 economy)
    select id into aid from aircraft where model = 'A330-300';
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'BUSINESS', current_timestamp, current_timestamp
    from generate_series(1, 6) as r,
         unnest(array ['A', 'C', 'D', 'F', 'G', 'H']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(7, 34) as r,
         unnest(array ['A', 'B', 'C', 'D', 'E', 'G', 'H']) as c;
    insert into seats (aircraft_id, seat_number, seat_class, created_at, updated_at)
    select aid, r::text || c, 'ECONOMY', current_timestamp, current_timestamp
    from generate_series(35, 42) as r,
         unnest(array ['A', 'B', 'C', 'D', 'G', 'H']) as c;
end
$$;
