create table if not exists service_change_settlements (
    id bigserial primary key,
    booking_id bigint not null references bookings(id),
    preview_token varchar(120),
    idempotency_key varchar(120),
    settlement_type varchar(30) not null,
    status varchar(30) not null,
    additional_amount numeric(18, 2) not null,
    refund_amount numeric(18, 2) not null,
    net_amount numeric(18, 2) not null,
    currency varchar(10) not null,
    provider varchar(30),
    provider_refund_id varchar(255),
    error_code varchar(80),
    error_message varchar(500),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_service_change_settlements_booking_id
    on service_change_settlements(booking_id, created_at desc);

create table if not exists service_change_settlement_items (
    id bigserial primary key,
    settlement_id bigint not null references service_change_settlements(id) on delete cascade,
    passenger_id bigint not null,
    service_id bigint not null,
    option_id bigint not null,
    quantity integer not null,
    service_type varchar(30),
    service_name varchar(255),
    option_name varchar(255),
    old_amount numeric(18, 2) not null,
    new_amount numeric(18, 2) not null,
    delta_amount numeric(18, 2) not null
);

create index if not exists idx_service_change_settlement_items_settlement_id
    on service_change_settlement_items(settlement_id);

alter table payments
    add column if not exists service_change_settlement_id bigint references service_change_settlements(id);

create index if not exists idx_payments_service_change_settlement_id
    on payments(service_change_settlement_id);
