with seat_type_ids as (
    select max(case when code = 'WINDOW' then id end) as window_id,
           max(case when code = 'AISLE' then id end) as aisle_id,
           max(case when code = 'MIDDLE' then id end) as middle_id
    from seat_types
),
seat_category_ids as (
    select max(case when code = 'STANDARD' then id end) as standard_id
    from seat_categories
),
normalized as (
    select fs.id,
           fs.seat_class_id as current_seat_class_id,
           fs.seat_type_id as current_seat_type_id,
           fs.seat_category_id as current_seat_category_id,
           al.id as aircraft_layout_id,
           cast(nullif(regexp_replace(fs.seat_number, '[^0-9]', '', 'g'), '') as integer) as seat_row,
           regexp_replace(upper(fs.seat_number), '[^A-Z]', '', 'g') as seat_column,
           regexp_replace(upper(coalesce(al.columns, '')), '[^A-Z]', '', 'g') as layout_columns
    from flight_seats fs
    join flights f on f.id = fs.flight_id
    join aircraft_layout al on al.aircraft_id = f.aircraft_id
    where fs.seat_number is not null
),
resolved as (
    select n.id,
           coalesce(class_zone.seat_class_id, n.current_seat_class_id) as seat_class_id,
           coalesce(
               type_zone.seat_type_id,
               case
                   when n.seat_column is null or n.seat_column = '' then sti.middle_id
                   when strpos(n.layout_columns, n.seat_column) in (1, char_length(n.layout_columns)) then sti.window_id
                   when strpos(n.layout_columns, n.seat_column) in (2, greatest(1, char_length(n.layout_columns) - 1)) then sti.aisle_id
                   else sti.middle_id
               end,
               n.current_seat_type_id
           ) as seat_type_id,
           coalesce(category_zone.seat_category_id, sci.standard_id, n.current_seat_category_id) as seat_category_id
    from normalized n
    cross join seat_type_ids sti
    cross join seat_category_ids sci
    left join lateral (
        select z.seat_class_id
        from aircraft_layout_zones z
        where z.aircraft_layout_id = n.aircraft_layout_id
          and n.seat_row is not null
          and n.seat_row between z.row_start and z.row_end
          and (
              z.columns is null
              or btrim(z.columns) = ''
              or position(
                    ',' || n.seat_column || ','
                    in ',' || replace(upper(z.columns), ' ', '') || ','
                 ) > 0
              or position(
                    n.seat_column
                    in regexp_replace(upper(z.columns), '[^A-Z]', '', 'g')
                 ) > 0
          )
        order by z.row_start desc, z.id desc
        limit 1
    ) class_zone on true
    left join lateral (
        select z.seat_type_id
        from aircraft_layout_seat_type_zones z
        where z.aircraft_layout_id = n.aircraft_layout_id
          and n.seat_row is not null
          and n.seat_row between z.row_start and z.row_end
          and (
              z.columns is null
              or btrim(z.columns) = ''
              or position(
                    ',' || n.seat_column || ','
                    in ',' || replace(upper(z.columns), ' ', '') || ','
                 ) > 0
              or position(
                    n.seat_column
                    in regexp_replace(upper(z.columns), '[^A-Z]', '', 'g')
                 ) > 0
          )
        order by z.row_start desc, z.id desc
        limit 1
    ) type_zone on true
    left join lateral (
        select z.seat_category_id
        from aircraft_layout_seat_category_zones z
        where z.aircraft_layout_id = n.aircraft_layout_id
          and n.seat_row is not null
          and n.seat_row between z.row_start and z.row_end
          and (
              z.columns is null
              or btrim(z.columns) = ''
              or position(
                    ',' || n.seat_column || ','
                    in ',' || replace(upper(z.columns), ' ', '') || ','
                 ) > 0
              or position(
                    n.seat_column
                    in regexp_replace(upper(z.columns), '[^A-Z]', '', 'g')
                 ) > 0
          )
        order by z.row_start desc, z.id desc
        limit 1
    ) category_zone on true
)
update flight_seats fs
set seat_class_id = r.seat_class_id,
    seat_type_id = r.seat_type_id,
    seat_category_id = r.seat_category_id
from resolved r
where fs.id = r.id
  and (
      fs.seat_class_id is distinct from r.seat_class_id
      or fs.seat_type_id is distinct from r.seat_type_id
      or fs.seat_category_id is distinct from r.seat_category_id
  );
