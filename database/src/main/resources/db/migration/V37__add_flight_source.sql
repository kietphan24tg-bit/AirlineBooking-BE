-- Origin of flight row: imported schedules vs synthetic coverage/mock.
alter table flights
    add column if not exists flight_source varchar(20) not null default 'AIRLABS';

comment on column flights.flight_source is 'AIRLABS=imported schedule; COVERAGE=synthetic gap-fill; MOCK=mock reseed';

create index if not exists idx_flights_flight_date_source
    on flights (flight_date, flight_source);
