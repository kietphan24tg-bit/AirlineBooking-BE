alter table users
    add column token_invalid_before timestamp with time zone;

comment on column users.token_invalid_before is
    'Tokens issued before this timestamp must be rejected';
