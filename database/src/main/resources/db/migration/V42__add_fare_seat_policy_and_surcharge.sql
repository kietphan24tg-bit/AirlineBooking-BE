alter table fare_classes
    add column if not exists seat_selection_policy varchar(20) not null default 'NOT_ALLOWED';

update fare_classes
set seat_selection_policy =
    case
        when code in ('BUSINESS', 'FIRST', 'PREMIUM', 'ECONOMY_FLEX') then 'INCLUDED'
        when code = 'ECONOMY' then 'PAID'
        when seat_selection then 'INCLUDED'
        else 'NOT_ALLOWED'
    end;

alter table fare_classes
    drop constraint if exists chk_fare_classes_seat_selection_policy;

alter table fare_classes
    add constraint chk_fare_classes_seat_selection_policy
        check (seat_selection_policy in ('NOT_ALLOWED', 'PAID', 'INCLUDED', 'CHECKIN_ONLY'));

alter table seat_types
    add column if not exists surcharge_amount numeric(18, 2) not null default 0;

update seat_types
set surcharge_amount =
    case
        when code = 'MIDDLE' then 30000.00
        when code in ('WINDOW', 'AISLE') then 50000.00
        else 0
    end;
