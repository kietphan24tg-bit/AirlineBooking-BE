alter table payments
    drop constraint if exists chk_payments_status;

alter table payments
    add constraint chk_payments_status
        check (payment_status in (
            'PENDING',
            'SUSPENDED',
            'SUCCESS',
            'FAILED',
            'EXPIRED',
            'CANCELLED',
            'PARTIALLY_REFUNDED',
            'REFUNDED'
        ));
