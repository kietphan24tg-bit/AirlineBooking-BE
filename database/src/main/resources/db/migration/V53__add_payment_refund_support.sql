alter table payments
    add column if not exists provider_payment_reference varchar(255),
    add column if not exists refund_counter_account_bank_id varchar(50),
    add column if not exists refund_counter_account_bank_name varchar(255),
    add column if not exists refund_counter_account_name varchar(255),
    add column if not exists refund_counter_account_number varchar(100);

alter table payments
    drop constraint if exists chk_payments_status;

alter table payments
    add constraint chk_payments_status
        check (payment_status in (
            'PENDING',
            'SUCCESS',
            'FAILED',
            'EXPIRED',
            'CANCELLED',
            'PARTIALLY_REFUNDED',
            'REFUNDED'
        ));
