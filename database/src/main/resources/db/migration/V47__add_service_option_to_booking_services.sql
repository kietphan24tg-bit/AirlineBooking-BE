alter table booking_services
    add column service_option_id bigint references service_options(id),
    add column updated_at timestamp with time zone not null default current_timestamp;

create index idx_booking_services_passenger_id
    on booking_services(passenger_id);

create index idx_booking_services_service_option_id
    on booking_services(service_option_id);

create unique index ux_booking_services_booking_passenger_option
    on booking_services(booking_id, passenger_id, service_option_id);
