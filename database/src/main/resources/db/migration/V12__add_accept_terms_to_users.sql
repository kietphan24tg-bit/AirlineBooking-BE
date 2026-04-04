alter table users drop column if exists terms_accepted_at;

alter table users
    add column accept_terms boolean not null default false;

comment on column users.accept_terms is
    'User accepted terms and conditions at registration';
