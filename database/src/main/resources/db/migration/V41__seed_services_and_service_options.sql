-- Seed ancillary services and service options.
-- Idempotent by natural keys (service type + service name, option name within service).

insert into services (name, type, icon_url, is_active)
select 'Baggage Add-on', 'BAGGAGE', 'https://img.icons8.com/fluency/96/suitcase.png', true
where not exists (
    select 1
    from services
    where type = 'BAGGAGE'
      and name = 'Baggage Add-on'
);

insert into services (name, type, icon_url, is_active)
select 'Pre-order Meals', 'MEAL', 'https://img.icons8.com/fluency/96/meal.png', true
where not exists (
    select 1
    from services
    where type = 'MEAL'
      and name = 'Pre-order Meals'
);

insert into services (name, type, icon_url, is_active)
select 'Travel Insurance', 'INSURANCE', 'https://img.icons8.com/fluency/96/health-book.png', true
where not exists (
    select 1
    from services
    where type = 'INSURANCE'
      and name = 'Travel Insurance'
);

insert into services (name, type, icon_url, is_active)
select 'Priority Boarding', 'PRIORITY_BOARDING', 'https://img.icons8.com/fluency/96/airport.png', true
where not exists (
    select 1
    from services
    where type = 'PRIORITY_BOARDING'
      and name = 'Priority Boarding'
);

insert into services (name, type, icon_url, is_active)
select 'Airport Lounge', 'LOUNGE', 'https://img.icons8.com/fluency/96/lounge-chair.png', true
where not exists (
    select 1
    from services
    where type = 'LOUNGE'
      and name = 'Airport Lounge'
);

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Extra 15kg',
       'Add one checked baggage allowance up to 15kg.',
       180000.00,
       'https://images.unsplash.com/photo-1527631746610-bca00a040d60?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'BAGGAGE'
  and s.name = 'Baggage Add-on'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Extra 15kg'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Extra 20kg',
       'Add one checked baggage allowance up to 20kg.',
       250000.00,
       'https://images.unsplash.com/photo-1517479149777-5f3b1511d5ad?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'BAGGAGE'
  and s.name = 'Baggage Add-on'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Extra 20kg'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Extra 25kg',
       'Add one checked baggage allowance up to 25kg.',
       320000.00,
       'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'BAGGAGE'
  and s.name = 'Baggage Add-on'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Extra 25kg'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Extra 30kg',
       'Add one checked baggage allowance up to 30kg.',
       390000.00,
       'https://images.unsplash.com/photo-1493558103817-58b2924bce98?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'BAGGAGE'
  and s.name = 'Baggage Add-on'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Extra 30kg'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Extra 40kg',
       'Add one checked baggage allowance up to 40kg.',
       520000.00,
       'https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'BAGGAGE'
  and s.name = 'Baggage Add-on'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Extra 40kg'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Beef Pho Meal',
       'Hot Vietnamese beef pho served with beverage.',
       90000.00,
       'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'MEAL'
  and s.name = 'Pre-order Meals'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Beef Pho Meal'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Chicken Rice Meal',
       'Hot chicken rice meal with seasonal side dish.',
       95000.00,
       'https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'MEAL'
  and s.name = 'Pre-order Meals'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Chicken Rice Meal'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Vegetarian Delight',
       'Vegetarian meal with fresh vegetables and rice.',
       95000.00,
       'https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'MEAL'
  and s.name = 'Pre-order Meals'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Vegetarian Delight'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Halal Chicken Meal',
       'Halal-certified chicken meal with side vegetables.',
       110000.00,
       'https://images.unsplash.com/photo-1604908554027-97a5bbdf3f1c?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'MEAL'
  and s.name = 'Pre-order Meals'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Halal Chicken Meal'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Kids Pasta Set',
       'Child-friendly pasta set with juice and dessert.',
       85000.00,
       'https://images.unsplash.com/photo-1621996346565-e3dbc646d9a9?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'MEAL'
  and s.name = 'Pre-order Meals'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Kids Pasta Set'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Premium Combo Meal',
       'Upgraded meal with main course, dessert, and beverage.',
       150000.00,
       'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'MEAL'
  and s.name = 'Pre-order Meals'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Premium Combo Meal'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Domestic Basic Cover',
       'Medical and trip protection for domestic itineraries.',
       65000.00,
       'https://images.unsplash.com/photo-1450101499163-c8848c66ca85?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'INSURANCE'
  and s.name = 'Travel Insurance'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Domestic Basic Cover'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Domestic Premium Cover',
       'Expanded medical, baggage, and delay protection for domestic trips.',
       99000.00,
       'https://images.unsplash.com/photo-1521790797524-b2497295b8a0?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'INSURANCE'
  and s.name = 'Travel Insurance'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Domestic Premium Cover'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'International Basic Cover',
       'Essential overseas medical and baggage protection.',
       160000.00,
       'https://images.unsplash.com/photo-1489515217757-5fd1be406fef?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'INSURANCE'
  and s.name = 'Travel Insurance'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'International Basic Cover'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'International Premium Cover',
       'Comprehensive international protection including delays and missed connections.',
       245000.00,
       'https://images.unsplash.com/photo-1520607162513-77705c0f0d4a?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'INSURANCE'
  and s.name = 'Travel Insurance'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'International Premium Cover'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Priority Boarding Standard',
       'Board earlier than general passengers and settle in faster.',
       80000.00,
       'https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'PRIORITY_BOARDING'
  and s.name = 'Priority Boarding'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Priority Boarding Standard'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Priority Boarding Plus',
       'Priority boarding with fast-lane handling at the gate.',
       120000.00,
       'https://images.unsplash.com/photo-1521727857535-28d2047314ac?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'PRIORITY_BOARDING'
  and s.name = 'Priority Boarding'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Priority Boarding Plus'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'Domestic Lounge Pass',
       'Single-use domestic lounge access with snacks and Wi-Fi.',
       350000.00,
       'https://images.unsplash.com/photo-1540339832862-474599807836?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'LOUNGE'
  and s.name = 'Airport Lounge'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'Domestic Lounge Pass'
  );

insert into service_options (service_id, name, description, price, image_url, is_active)
select s.id,
       'International Lounge Pass',
       'Single-use international lounge access with refreshments and shower facilities.',
       550000.00,
       'https://images.unsplash.com/photo-1556388158-158ea5ccacbd?auto=format&fit=crop&w=800&q=80',
       true
from services s
where s.type = 'LOUNGE'
  and s.name = 'Airport Lounge'
  and not exists (
      select 1
      from service_options so
      where so.service_id = s.id
        and so.name = 'International Lounge Pass'
  );
