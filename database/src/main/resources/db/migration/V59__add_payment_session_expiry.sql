alter table payments
    add column session_expires_at timestamp with time zone;

update payments p
set session_expires_at = b.pnr_expires_at
from bookings b
where p.booking_id = b.id
  and p.session_expires_at is null
  and p.payment_status in ('PENDING', 'SUSPENDED');
