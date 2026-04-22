alter table passengers
    alter column nationality drop not null,
    alter column identify_type drop not null,
    alter column identify_number drop not null;
