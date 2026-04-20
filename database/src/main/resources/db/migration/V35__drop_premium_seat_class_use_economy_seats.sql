-- Physical seat classes: only ECONOMY + BUSINESS. Premium Economy fares still exist in
-- fare_classes but map to ECONOMY seats (same cabin as economy rows on the layout).

delete from fare_class_seat_classes fsc
using seat_classes sc
where fsc.seat_class_id = sc.id
  and sc.code = 'PREMIUM';

insert into fare_class_seat_classes (fare_class_id, seat_class_id)
select fc.id, sc.id
from fare_classes fc
join seat_classes sc on sc.code = 'ECONOMY'
where fc.cabin_class = 'PREMIUM_ECONOMY'
on conflict (fare_class_id, seat_class_id) do nothing;

update flight_seats fs
set seat_class_id = ec.id
from seat_classes pc
cross join seat_classes ec
where fs.seat_class_id = pc.id
  and pc.code = 'PREMIUM'
  and ec.code = 'ECONOMY';

delete from seat_classes where code = 'PREMIUM';
