insert into fare_classes (
    code, name, cabin_class,
    baggage_checkin_kg, baggage_hand_kg,
    change_fee, refund_fee,
    is_refundable, is_changeable, is_priority_checkin, is_priority_boarding,
    mileage_rate, description, created_at, updated_at
)
values
    ('ECONOMY_SAVER', 'Economy Saver', 'ECONOMY', 20, 7, 500000, 700000, false, true, false, false, 1.00, 'Lowest economy fare with basic flexibility', current_timestamp, current_timestamp),
    ('ECONOMY_FLEX', 'Economy Flex', 'ECONOMY', 25, 10, 200000, 400000, true, true, false, false, 1.20, 'Flexible economy fare', current_timestamp, current_timestamp),
    ('BUSINESS', 'Business', 'BUSINESS', 40, 14, 0, 200000, true, true, true, true, 1.80, 'Business class fare', current_timestamp, current_timestamp)
on conflict (code) do nothing;

insert into tax_fees (code, name, amount_type, value, apply_to, description, is_active, created_at)
values
    ('VAT_DOM', 'Thuế GTGT', 'PERCENT', 0.10, 'ADULT', 'VAT for domestic itinerary', true, current_timestamp),
    ('APT_FEE', 'Phí sân bay', 'FIXED', 60000, 'ADULT', 'Airport service fee', true, current_timestamp),
    ('SEC_FEE', 'Phí an ninh', 'FIXED', 20000, 'ADULT', 'Security fee', true, current_timestamp)
on conflict (code) do nothing;

alter table flight_fares
    drop constraint if exists chk_flight_fares_prices_non_negative;

alter table flight_fares
    drop column if exists available_seats;

alter table flight_fares
    add constraint chk_flight_fares_prices_non_negative
        check (base_price >= 0 and total_tax_amount >= 0 and total_price >= 0);
