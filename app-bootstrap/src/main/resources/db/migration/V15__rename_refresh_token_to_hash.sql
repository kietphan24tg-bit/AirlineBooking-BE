alter table refresh_token_sessions
    rename column refresh_token to refresh_token_hash;

alter table refresh_token_sessions
    rename constraint uq_refresh_token_sessions_token to uq_refresh_token_sessions_token_hash;

