with pricing_targets as (
    select *
    from (
        values
            ('VN', 'VND', 'ECONOMY', 960000.00),
            ('VN', 'VND', 'PREMIUM', 1840000.00),
            ('VN', 'VND', 'BUSINESS', 4480000.00),
            ('VN', 'VND', 'FIRST', 7230000.00),

            ('TH', 'THB', 'ECONOMY', 2420.00),
            ('TH', 'THB', 'PREMIUM', 4730.00),
            ('TH', 'THB', 'BUSINESS', 12100.00),
            ('TH', 'THB', 'FIRST', 19580.00),

            ('SG', 'SGD', 'ECONOMY', 79.20),
            ('SG', 'SGD', 'PREMIUM', 159.50),
            ('SG', 'SGD', 'BUSINESS', 396.00),
            ('SG', 'SGD', 'FIRST', 649.00),

            ('MY', 'MYR', 'ECONOMY', 264.00),
            ('MY', 'MYR', 'PREMIUM', 517.00),
            ('MY', 'MYR', 'BUSINESS', 1320.00),
            ('MY', 'MYR', 'FIRST', 2145.00),

            ('ID', 'IDR', 'ECONOMY', 1210000.00),
            ('ID', 'IDR', 'PREMIUM', 2365000.00),
            ('ID', 'IDR', 'BUSINESS', 6050000.00),
            ('ID', 'IDR', 'FIRST', 9790000.00),

            ('PH', 'PHP', 'ECONOMY', 3080.00),
            ('PH', 'PHP', 'PREMIUM', 6050.00),
            ('PH', 'PHP', 'BUSINESS', 15400.00),
            ('PH', 'PHP', 'FIRST', 25080.00),

            ('US', 'USD', 'ECONOMY', 59.40),
            ('US', 'USD', 'PREMIUM', 118.80),
            ('US', 'USD', 'BUSINESS', 297.00),
            ('US', 'USD', 'FIRST', 484.00),

            ('DE', 'EUR', 'ECONOMY', 55.00),
            ('DE', 'EUR', 'PREMIUM', 110.00),
            ('DE', 'EUR', 'BUSINESS', 275.00),
            ('DE', 'EUR', 'FIRST', 451.00),

            ('JP', 'JPY', 'ECONOMY', 8800.00),
            ('JP', 'JPY', 'PREMIUM', 17600.00),
            ('JP', 'JPY', 'BUSINESS', 44000.00),
            ('JP', 'JPY', 'FIRST', 71500.00),

            ('GB', 'GBP', 'ECONOMY', 48.40),
            ('GB', 'GBP', 'PREMIUM', 96.80),
            ('GB', 'GBP', 'BUSINESS', 242.00),
            ('GB', 'GBP', 'FIRST', 396.00),

            ('AU', 'AUD', 'ECONOMY', 92.40),
            ('AU', 'AUD', 'PREMIUM', 184.80),
            ('AU', 'AUD', 'BUSINESS', 462.00),
            ('AU', 'AUD', 'FIRST', 759.00),

            ('GL', 'VND', 'ECONOMY', 960000.00),
            ('GL', 'VND', 'PREMIUM', 1840000.00),
            ('GL', 'VND', 'BUSINESS', 4480000.00),
            ('GL', 'VND', 'FIRST', 7230000.00)
    ) as src(country_code, currency, fare_code, target_total)
),
active_fixed_fees as (
    select coalesce(sum(value), 0) as fixed_total
    from tax_fees
    where is_active = true
      and apply_to = 'ADULT'
      and upper(amount_type) = 'FIXED'
),
active_fixed_fee_items as (
    select code, coalesce(name, code) as name, value
    from tax_fees
    where is_active = true
      and apply_to = 'ADULT'
      and upper(amount_type) = 'FIXED'
),
vat_tax as (
    select coalesce(max(name), 'VAT') as name
    from tax_fees
    where is_active = true
      and apply_to = 'ADULT'
      and upper(amount_type) = 'PERCENT'
      and code = 'VAT_DOM'
),
computed_base_prices as (
    select
        fc.id as fare_class_id,
        pt.currency,
        pt.country_code,
        round(
            case
                when pt.currency = 'VND'
                    then (pt.target_total - (select fixed_total from active_fixed_fees)) / 1.10
                else pt.target_total / 1.10
            end,
            2
        ) as base_price
    from pricing_targets pt
    join fare_classes fc on upper(fc.code) = pt.fare_code
),
upserted_prices as (
    insert into fare_class_prices (fare_class_id, currency, country_code, base_price, created_at, updated_at)
    select fare_class_id, currency, country_code, base_price, current_timestamp, current_timestamp
    from computed_base_prices
    on conflict (fare_class_id, currency, country_code) do update
    set
        base_price = excluded.base_price,
        updated_at = current_timestamp
    returning fare_class_id, currency, country_code, base_price
),
market_reprices as (
    select
        ff.id as flight_fare_id,
        up.base_price,
        up.currency
    from flight_fares ff
    join flights f on f.id = ff.flight_id
    join airports dep on dep.id = f.departure_airport_id
    join upserted_prices up
        on up.fare_class_id = ff.fare_class_id
       and up.country_code = upper(coalesce(dep.country_code, 'GL'))
),
recomputed_values as (
    select
        mr.flight_fare_id,
        mr.base_price,
        round(
            mr.base_price * 0.10
            + case when mr.currency = 'VND' then (select fixed_total from active_fixed_fees) else 0 end,
            2
        ) as total_tax_amount,
        round(
            mr.base_price
            + mr.base_price * 0.10
            + case when mr.currency = 'VND' then (select fixed_total from active_fixed_fees) else 0 end,
            2
        ) as total_price,
        case
            when mr.currency = 'VND' then
                jsonb_build_array(
                    jsonb_build_object(
                        'code', 'VAT_DOM',
                        'name', (select name from vat_tax),
                        'amount', round(mr.base_price * 0.10, 2)
                    )
                ) || coalesce(
                    (
                        select jsonb_agg(
                            jsonb_build_object(
                                'code', ffi.code,
                                'name', ffi.name,
                                'amount', round(ffi.value, 2)
                            )
                        )
                        from active_fixed_fee_items ffi
                    ),
                    '[]'::jsonb
                )
            else
                jsonb_build_array(
                    jsonb_build_object(
                        'code', 'VAT_DOM',
                        'name', (select name from vat_tax),
                        'amount', round(mr.base_price * 0.10, 2)
                    )
                )
        end as tax_breakdown
    from market_reprices mr
)
update flight_fares ff
set
    base_price = rv.base_price,
    total_tax_amount = rv.total_tax_amount,
    total_price = rv.total_price,
    tax_breakdown = rv.tax_breakdown,
    updated_at = current_timestamp
from recomputed_values rv
where ff.id = rv.flight_fare_id;
