set statement_timeout to '15min';

-- V46 allowed only one active hold per booking, which breaks round-trip (two fares).
drop index if exists uq_booking_fare_holds_booking_active;

create unique index if not exists uq_booking_fare_holds_booking_fare_active
    on booking_fare_holds(booking_id, flight_fare_id)
    where status in ('HELD', 'CONFIRMED');
