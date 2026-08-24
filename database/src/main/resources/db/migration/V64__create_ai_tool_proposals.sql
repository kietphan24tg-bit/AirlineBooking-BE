create table if not exists ai_tool_proposals (
    id uuid primary key,
    user_id bigint not null,
    verified_booking_id bigint null,
    tool_name varchar(100) not null,
    operation varchar(20) not null,
    access_level varchar(30) not null,
    canonical_arguments jsonb not null,
    confirmation_hmac varchar(255) not null,
    confirmation_snapshot jsonb not null,
    status varchar(30) not null,
    idempotency_key varchar(255) not null unique,
    safe_result jsonb null,
    safe_error jsonb null,
    expires_at timestamptz not null,
    claimed_at timestamptz null,
    completed_at timestamptz null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_ai_tool_proposals_user_status
    on ai_tool_proposals (user_id, status);

create index if not exists idx_ai_tool_proposals_expires_at
    on ai_tool_proposals (expires_at);

create index if not exists idx_ai_tool_proposals_created_at
    on ai_tool_proposals (created_at);

create table if not exists ai_tool_audit_events (
    id bigserial primary key,
    proposal_id uuid not null,
    request_id varchar(100) null,
    user_id bigint not null,
    tool_name varchar(100) not null,
    event_type varchar(50) not null,
    status varchar(30) not null,
    error_code varchar(100) null,
    latency_ms bigint null,
    created_at timestamptz not null
);

create index if not exists idx_ai_tool_audit_events_proposal_created_at
    on ai_tool_audit_events (proposal_id, created_at);
