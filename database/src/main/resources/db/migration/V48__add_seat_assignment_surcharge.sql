alter table passenger_seat_assignments
    add column if not exists surcharge_amount numeric(18, 2) not null default 0,
    add column if not exists currency varchar(10);

alter table passenger_seat_assignments
    add constraint chk_passenger_seat_assignments_surcharge_non_negative
        check (surcharge_amount >= 0);
