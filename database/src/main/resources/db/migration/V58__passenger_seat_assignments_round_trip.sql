set statement_timeout to '15min';

-- V46 allowed only one active seat per passenger across the whole booking.
-- Round-trip needs one active seat per passenger per flight.
alter table passenger_seat_assignments
    add column if not exists flight_id bigint;

update passenger_seat_assignments psa
set flight_id = fs.flight_id
from flight_seats fs
where psa.flight_seat_id = fs.id
  and psa.flight_id is null;

create or replace function set_passenger_seat_assignment_flight_id()
returns trigger
language plpgsql
as $$
begin
    select fs.flight_id
    into new.flight_id
    from flight_seats fs
    where fs.id = new.flight_seat_id;

    if new.flight_id is null then
        raise exception 'flight_seat_id % does not reference a flight', new.flight_seat_id;
    end if;

    return new;
end;
$$;

drop trigger if exists trg_passenger_seat_assignments_flight_id
    on passenger_seat_assignments;

create trigger trg_passenger_seat_assignments_flight_id
before insert or update of flight_seat_id
on passenger_seat_assignments
for each row
execute function set_passenger_seat_assignment_flight_id();

alter table passenger_seat_assignments
    alter column flight_id set not null;

alter table passenger_seat_assignments
    drop constraint if exists fk_passenger_seat_assignments_flight;

alter table passenger_seat_assignments
    add constraint fk_passenger_seat_assignments_flight
    foreign key (flight_id) references flights(id);

drop index if exists uq_passenger_seat_assignments_passenger_active;

create unique index if not exists uq_passenger_seat_assignments_passenger_flight_active
    on passenger_seat_assignments(passenger_id, flight_id)
    where status in ('RESERVED', 'BOOKED');

create index if not exists idx_passenger_seat_assignments_flight
    on passenger_seat_assignments(flight_id);
