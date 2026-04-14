alter table flight_seats
    drop column if exists seat_id cascade;

drop table if exists seats cascade;

create unique index if not exists uq_flight_seats_flight_seat_number
    on flight_seats(flight_id, seat_number);

create index if not exists idx_flight_seats_flight_id
    on flight_seats(flight_id);

create index if not exists idx_flight_seats_status
    on flight_seats(status);

create index if not exists idx_flight_seats_fare_class_id
    on flight_seats(fare_class_id);
