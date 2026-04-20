alter table fare_classes
    add column if not exists lounge_access boolean not null default false,
    add column if not exists seat_selection boolean not null default false,
    add column if not exists meal_type varchar(30) not null default 'NONE',
    add column if not exists wifi_included boolean not null default false,
    add column if not exists fast_track_security boolean not null default false;

update fare_classes
set
    lounge_access =
        case
            when description is null then lounge_access
            when description ilike '%lounge%' then true
            else lounge_access
        end,
    seat_selection =
        case
            when description is null then seat_selection
            when description ilike '%seat selection%' then true
            when description ilike '%lie-flat seats%' then true
            when description ilike '%private suites%' then true
            else seat_selection
        end,
    meal_type =
        case
            when description is null then meal_type
            when description ilike '%michelin%' then 'GOURMET'
            when description ilike '%gourmet%' then 'GOURMET'
            when description ilike '%premium meal%' then 'PREMIUM'
            when description ilike '%premium meals%' then 'PREMIUM'
            when description ilike '%snacks%' then 'STANDARD'
            when description ilike '%meal%' then 'STANDARD'
            else meal_type
        end,
    wifi_included =
        case
            when description is null then wifi_included
            when description ilike '%wifi%' then true
            else wifi_included
        end,
    fast_track_security =
        case
            when description is null then fast_track_security
            when description ilike '%fast-track%' then true
            when description ilike '%fast track%' then true
            else fast_track_security
        end;

update fare_classes
set
    lounge_access = true,
    seat_selection = true,
    meal_type = 'GOURMET',
    wifi_included = true,
    fast_track_security = true
where code in ('BUSINESS', 'FIRST');

update fare_classes
set
    seat_selection = true,
    meal_type = 'PREMIUM',
    wifi_included = true
where code = 'PREMIUM';

update fare_classes
set
    seat_selection = true,
    meal_type = 'STANDARD'
where code = 'ECONOMY_FLEX';

update fare_classes
set meal_type = 'STANDARD'
where code = 'ECONOMY';

alter table fare_classes
    drop constraint if exists chk_fare_classes_meal_type;

alter table fare_classes
    add constraint chk_fare_classes_meal_type
        check (meal_type in ('NONE', 'STANDARD', 'PREMIUM', 'GOURMET'));

alter table fare_classes
    drop column if exists description;
