update flight_fares ff
set total_tax_amount = round(ff.base_price * 0.10, 2),
    total_price = ff.base_price + round(ff.base_price * 0.10, 2),
    tax_breakdown = jsonb_build_array(
        jsonb_build_object(
            'code', 'VAT_DOM',
            'name', coalesce((select tf.name from tax_fees tf where tf.code = 'VAT_DOM'), 'VAT'),
            'amount', round(ff.base_price * 0.10, 2)
        )
    ),
    updated_at = current_timestamp
from flights f
join airports dep on dep.id = f.departure_airport_id
where ff.flight_id = f.id
  and upper(coalesce(dep.country_code, '')) in ('US', 'GB', 'DE', 'JP', 'AU', 'SG')
  and ff.total_tax_amount >= 80000;
