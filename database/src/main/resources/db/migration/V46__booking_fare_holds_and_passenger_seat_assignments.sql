set statement_timeout to '15min';

create table if not exists booking_fare_holds (
    id bigserial primary key,
    booking_id bigint not null references bookings(id),
    flight_fare_id bigint not null references flight_fares(id),
    quantity integer not null,
    status varchar(20) not null,
    expires_at timestamp with time zone,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint chk_booking_fare_holds_quantity check (quantity > 0),
    constraint chk_booking_fare_holds_status check (status in ('HELD', 'CONFIRMED', 'RELEASED'))
);

create unique index if not exists uq_booking_fare_holds_booking_active
    on booking_fare_holds(booking_id)
    where status in ('HELD', 'CONFIRMED');

create index if not exists idx_booking_fare_holds_fare_status_expires
    on booking_fare_holds(flight_fare_id, status, expires_at);

create table if not exists passenger_seat_assignments (
    id bigserial primary key,
    booking_id bigint not null references bookings(id),
    passenger_id bigint not null references passengers(id),
    flight_seat_id bigint not null references flight_seats(id),
    status varchar(20) not null,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint chk_passenger_seat_assignments_status check (status in ('RESERVED', 'BOOKED', 'RELEASED'))
);

create unique index if not exists uq_passenger_seat_assignments_passenger_active
    on passenger_seat_assignments(passenger_id)
    where status in ('RESERVED', 'BOOKED');

create unique index if not exists uq_passenger_seat_assignments_seat_active
    on passenger_seat_assignments(flight_seat_id)
    where status in ('RESERVED', 'BOOKED');

create index if not exists idx_passenger_seat_assignments_booking_status
    on passenger_seat_assignments(booking_id, status);
