-- Seat regeneration marks seats no longer on the layout as UNAVAILABLE (see FlightSeatGenerationService).
alter table flight_seats
    drop constraint if exists chk_flight_seats_status;

alter table flight_seats
    add constraint chk_flight_seats_status
        check (status in ('AVAILABLE', 'LOCKED', 'RESERVED', 'BOOKED', 'UNAVAILABLE'));
