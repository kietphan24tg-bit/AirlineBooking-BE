alter table payments
    add column request_key varchar(255);

create unique index ux_payments_request_key
    on payments(request_key)
    where request_key is not null;
