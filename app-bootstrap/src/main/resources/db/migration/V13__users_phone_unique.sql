drop index if exists idx_users_phone;

alter table users
    add constraint uq_users_phone unique (phone);
