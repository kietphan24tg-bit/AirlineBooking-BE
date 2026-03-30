create index idx_users_phone on users(phone);

create index idx_airports_city on airports(city);

create index idx_flights_search_route_date
    on flights(departure_airport_id, arrival_airport_id, flight_date);

create index idx_flights_airline_id on flights(airline_id);
create index idx_flights_status on flights(status);

create index idx_flight_fares_status on flight_fares(status);
create index idx_flight_seats_status on flight_seats(flight_id, status);
create index idx_flight_seats_reserved_until on flight_seats(reserved_until);

create index idx_bookings_user_status on bookings(user_id, status);
create index idx_bookings_pnr_expires_at on bookings(pnr_expires_at);

create index idx_passengers_booking_id on passengers(booking_id);

create index idx_payments_booking_status on payments(booking_id, payment_status);

create index idx_booking_services_booking_id on booking_services(booking_id);
create index idx_service_options_service_id on service_options(service_id);

alter table seats
    add constraint chk_seats_seat_class
        check (seat_class in ('ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST'));

alter table fare_classes
    add constraint chk_fare_classes_cabin_class
        check (cabin_class in ('ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST'));

alter table tax_fees
    add constraint chk_tax_fees_amount_type
        check (amount_type in ('PERCENT', 'FIXED'));

alter table tax_fees
    add constraint chk_tax_fees_apply_to
        check (apply_to in ('ADULT', 'CHILD', 'INFANT'));

alter table promotions
    add constraint chk_promotions_discount_type
        check (discount_type in ('PERCENT', 'FIXED'));

alter table flights
    add constraint chk_flights_status
        check (status in ('SCHEDULED', 'DELAYED', 'CANCELLED', 'BOARDING', 'DEPARTED', 'ARRIVED'));

alter table flight_fares
    add constraint chk_flight_fares_status
        check (status in ('AVAILABLE', 'SOLD_OUT'));

alter table flight_seats
    add constraint chk_flight_seats_status
        check (status in ('AVAILABLE', 'RESERVED', 'BOOKED', 'BLOCKED'));

alter table bookings
    add constraint chk_bookings_status
        check (status in ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'CHECKED_IN_LOCKED'));

alter table passengers
    add constraint chk_passengers_gender
        check (gender in ('MALE', 'FEMALE', 'OTHER'));

alter table passengers
    add constraint chk_passengers_type
        check (passenger_type in ('ADULT', 'CHILD', 'INFANT'));

alter table passengers
    add constraint chk_passengers_identify_type
        check (identify_type in ('ID_CARD', 'PASSPORT'));

alter table tickets
    add constraint chk_tickets_status
        check (status in ('ISSUED', 'CHECKED_IN', 'USED', 'CANCELLED'));

alter table payments
    add constraint chk_payments_status
        check (payment_status in ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED'));

alter table services
    add constraint chk_services_type
        check (type in ('BAGGAGE', 'MEAL', 'INSURANCE', 'PRIORITY_BOARDING', 'LOUNGE'));

alter table flight_fares
    add constraint chk_flight_fares_prices_non_negative
        check (base_price >= 0 and total_tax_amount >= 0 and total_price >= 0 and available_seats >= 0);

alter table flight_seats
    add constraint chk_flight_seats_price_non_negative
        check (price >= 0);

alter table bookings
    add constraint chk_bookings_amounts_non_negative
        check (total_amount >= 0 and discount_amount >= 0);

alter table payments
    add constraint chk_payments_amount_non_negative
        check (amount >= 0);

alter table booking_services
    add constraint chk_booking_services_quantity_positive
        check (quantity > 0 and price >= 0);

alter table passengers
    add constraint chk_passengers_expiry_date_after_birth
        check (expiry_date is null or expiry_date > date_of_birth);
