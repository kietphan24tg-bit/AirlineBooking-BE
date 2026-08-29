create table if not exists ai_chat_conversations (
    id uuid primary key,
    user_id bigint null,
    guest_token_hash varchar(128) null,
    status varchar(20) not null,
    title varchar(255) null,
    summary text null,
    structured_state jsonb null,
    summary_status varchar(20) not null,
    summarized_through_sequence bigint not null default 0,
    summary_claimed_at timestamptz null,
    summary_attempts integer not null default 0,
    next_sequence bigint not null default 1,
    last_active_at timestamptz not null,
    archived_at timestamptz null,
    archive_reason varchar(40) null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint chk_ai_chat_owner_present
        check (
            (user_id is not null and guest_token_hash is null)
            or (user_id is null and guest_token_hash is not null)
        )
);

create unique index if not exists uq_ai_chat_active_user
    on ai_chat_conversations (user_id)
    where status = 'ACTIVE' and user_id is not null;

create unique index if not exists uq_ai_chat_active_guest
    on ai_chat_conversations (guest_token_hash)
    where status = 'ACTIVE' and guest_token_hash is not null;

create index if not exists idx_ai_chat_conversations_last_active
    on ai_chat_conversations (last_active_at);

create index if not exists idx_ai_chat_conversations_status_updated
    on ai_chat_conversations (status, updated_at);

create table if not exists ai_chat_turns (
    id uuid primary key,
    conversation_id uuid not null references ai_chat_conversations(id) on delete cascade,
    client_message_id varchar(100) not null,
    status varchar(20) not null,
    answer_type varchar(40) null,
    safe_response jsonb null,
    memory_eligible boolean not null default true,
    created_at timestamptz not null,
    completed_at timestamptz null,
    updated_at timestamptz not null
);

create unique index if not exists uq_ai_chat_turns_conversation_client
    on ai_chat_turns (conversation_id, client_message_id);

create index if not exists idx_ai_chat_turns_conversation_created
    on ai_chat_turns (conversation_id, created_at desc);

create table if not exists ai_chat_messages (
    id bigserial primary key,
    conversation_id uuid not null references ai_chat_conversations(id) on delete cascade,
    turn_id uuid not null references ai_chat_turns(id) on delete cascade,
    sequence_number bigint not null,
    role varchar(20) not null,
    content text not null,
    safe_metadata jsonb null,
    created_at timestamptz not null
);

create unique index if not exists uq_ai_chat_messages_sequence
    on ai_chat_messages (conversation_id, sequence_number);

create index if not exists idx_ai_chat_messages_conversation_sequence
    on ai_chat_messages (conversation_id, sequence_number desc);

alter table ai_tool_proposals
    add column if not exists conversation_id uuid null;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where constraint_name = 'fk_ai_tool_proposals_conversation'
          and table_name = 'ai_tool_proposals'
    ) then
        alter table ai_tool_proposals
            add constraint fk_ai_tool_proposals_conversation
                foreign key (conversation_id)
                references ai_chat_conversations(id)
                on delete set null;
    end if;
end
$$;

create index if not exists idx_ai_tool_proposals_conversation_status
    on ai_tool_proposals (conversation_id, status);

update ai_tool_proposals
   set status = 'EXPIRED',
       completed_at = coalesce(completed_at, now()),
       updated_at = now()
 where conversation_id is null
   and status = 'PENDING';
