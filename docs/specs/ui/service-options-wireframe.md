# Service Options Wireframe

Tai lieu nay dung seed data trong [V41__seed_services_and_service_options.sql](/d:/Workspace/CNPM/AirlineBooking/airline-booking/database/src/main/resources/db/migration/V41__seed_services_and_service_options.sql) de mo ta wireframe day du cho man hinh dich vu bo sung.

## Seed Data Day Du

### Services

- `Baggage Add-on` - `BAGGAGE`
- `Pre-order Meals` - `MEAL`
- `Travel Insurance` - `INSURANCE`
- `Priority Boarding` - `PRIORITY_BOARDING`
- `Airport Lounge` - `LOUNGE`

### Service Options

#### Baggage Add-on

- `Extra 15kg` - `180,000 VND`
- `Extra 20kg` - `250,000 VND`
- `Extra 25kg` - `320,000 VND`
- `Extra 30kg` - `390,000 VND`
- `Extra 40kg` - `520,000 VND`

#### Pre-order Meals

- `Beef Pho Meal` - `90,000 VND`
- `Chicken Rice Meal` - `95,000 VND`
- `Vegetarian Delight` - `95,000 VND`
- `Halal Chicken Meal` - `110,000 VND`
- `Kids Pasta Set` - `85,000 VND`
- `Premium Combo Meal` - `150,000 VND`

#### Travel Insurance

- `Domestic Basic Cover` - `65,000 VND`
- `Domestic Premium Cover` - `99,000 VND`
- `International Basic Cover` - `160,000 VND`
- `International Premium Cover` - `245,000 VND`

#### Priority Boarding

- `Priority Boarding Standard` - `80,000 VND`
- `Priority Boarding Plus` - `120,000 VND`

#### Airport Lounge

- `Domestic Lounge Pass` - `350,000 VND`
- `International Lounge Pass` - `550,000 VND`

## Wireframe Day Du

```text
+--------------------------------------------------------------------------------------------------+
| DICH VU BO SUNG                                                                                  |
| Booking #PNR123                                                                  2 hanh khach    |
+--------------------------------------------------------------------------------------------------+

+--------------------------------+  +-------------------------------------------------------------+
| Hanh khach                     |  | Tom tat                                                     |
| (*) NGUYEN VAN A - ADULT       |  | Extra 20kg                                     250,000 VND |
| ( ) TRAN THI B - CHILD         |  | Beef Pho Meal                                   90,000 VND |
|                                |  | Priority Boarding Standard                      80,000 VND |
|                                |  | Domestic Basic Cover                            65,000 VND |
|                                |  | Domestic Lounge Pass                           350,000 VND |
|                                |  | ----------------------------------------------------------- |
|                                |  | Tam tinh                                       835,000 VND |
|                                |  | [Bo qua]                           [Luu dich vu / Tiep tuc] |
+--------------------------------+  +-------------------------------------------------------------+

+==================================================================================================+
| HANH LY KY GUI                                                                                   |
| [icon] Baggage Add-on                                                                            |
| Checked baggage for this passenger                                                               |
+--------------------------------------------------------------------------------------------------+
| +-------------------+   +-------------------+   +-------------------+   +-------------------+    |
| | Extra 15kg        |   | Extra 20kg        |   | Extra 25kg        |   | Extra 30kg        |    |
| | 180,000 VND       |   | 250,000 VND       |   | 320,000 VND       |   | 390,000 VND       |    |
| | Add one checked   |   | Add one checked   |   | Add one checked   |   | Add one checked   |    |
| | baggage up to     |   | baggage up to     |   | baggage up to     |   | baggage up to     |    |
| | 15kg              |   | 20kg              |   | 25kg              |   | 30kg              |    |
| | [Chon]            |   | [Da chon]         |   | [Chon]            |   | [Chon]            |    |
| +-------------------+   +-------------------+   +-------------------+   +-------------------+    |
|                                                                                                  |
| +-------------------+                                                                            |
| | Extra 40kg        |                                                                            |
| | 520,000 VND       |                                                                            |
| | Add one checked   |                                                                            |
| | baggage up to     |                                                                            |
| | 40kg              |                                                                            |
| | [Chon]            |                                                                            |
| +-------------------+                                                                            |
+==================================================================================================+

+==================================================================================================+
| SUAT AN                                                                                          |
| [icon] Pre-order Meals                                                                           |
| Meal selection for this passenger                                                                |
+--------------------------------------------------------------------------------------------------+
| +--------------------------+  +--------------------------+  +--------------------------+         |
| | Beef Pho Meal            |  | Chicken Rice Meal        |  | Vegetarian Delight       |         |
| | 90,000 VND               |  | 95,000 VND               |  | 95,000 VND               |         |
| | Hot Vietnamese beef pho  |  | Hot chicken rice meal    |  | Vegetarian meal with     |         |
| | served with beverage     |  | with seasonal side dish  |  | fresh vegetables and rice|         |
| | [Da chon]                |  | [Chon]                   |  | [Chon]                   |         |
| +--------------------------+  +--------------------------+  +--------------------------+         |
|                                                                                                  |
| +--------------------------+  +--------------------------+  +--------------------------+         |
| | Halal Chicken Meal       |  | Kids Pasta Set           |  | Premium Combo Meal       |         |
| | 110,000 VND              |  | 85,000 VND               |  | 150,000 VND              |         |
| | Halal-certified chicken  |  | Child-friendly pasta set |  | Main course, dessert,    |         |
| | meal with vegetables     |  | with juice and dessert   |  | and beverage             |         |
| | [Chon]                   |  | [Chon]                   |  | [Chon]                   |         |
| +--------------------------+  +--------------------------+  +--------------------------+         |
+==================================================================================================+

+==================================================================================================+
| BAO HIEM CHUYEN BAY                                                                               |
| [icon] Travel Insurance                                                                          |
| Protection plans for this passenger                                                              |
+--------------------------------------------------------------------------------------------------+
| +------------------------------+  +------------------------------+                               |
| | Domestic Basic Cover         |  | Domestic Premium Cover       |                               |
| | 65,000 VND                   |  | 99,000 VND                   |                               |
| | Medical and trip protection  |  | Expanded protection for      |                               |
| | for domestic itineraries     |  | delays, baggage, and medical |                               |
| | [Da chon]                    |  | [Chon]                       |                               |
| +------------------------------+  +------------------------------+                               |
|                                                                                                  |
| +------------------------------+  +------------------------------+                               |
| | International Basic Cover    |  | International Premium Cover  |                               |
| | 160,000 VND                  |  | 245,000 VND                  |                               |
| | Essential overseas medical   |  | Comprehensive protection     |                               |
| | and baggage protection       |  | incl. delays and missed conn.|                               |
| | [Chon]                       |  | [Chon]                       |                               |
| +------------------------------+  +------------------------------+                               |
+==================================================================================================+

+==================================================================================================+
| UU TIEN LEN MAY BAY                                                                              |
| [icon] Priority Boarding                                                                         |
+--------------------------------------------------------------------------------------------------+
| +------------------------------+  +------------------------------+                               |
| | Priority Boarding Standard   |  | Priority Boarding Plus       |                               |
| | 80,000 VND                   |  | 120,000 VND                  |                               |
| | Board earlier than general   |  | Priority boarding with       |                               |
| | passengers                   |  | fast-lane handling at gate   |                               |
| | [Da chon]                    |  | [Chon]                       |                               |
| +------------------------------+  +------------------------------+                               |
+==================================================================================================+

+==================================================================================================+
| PHONG CHO SAN BAY                                                                                |
| [icon] Airport Lounge                                                                            |
+--------------------------------------------------------------------------------------------------+
| +------------------------------+  +------------------------------+                               |
| | Domestic Lounge Pass         |  | International Lounge Pass    |                               |
| | 350,000 VND                  |  | 550,000 VND                  |                               |
| | Single-use domestic lounge   |  | Single-use lounge with       |                               |
| | access with snacks and Wi-Fi |  | refreshments and shower      |                               |
| | [Da chon]                    |  | [Chon]                       |                               |
| +------------------------------+  +------------------------------+                               |
+==================================================================================================+

+--------------------------------------------------------------------------------------------------+
| Ghi chu UI                                                                                        |
| - Moi service group render thanh 1 section                                                        |
| - Moi service option render thanh 1 card                                                          |
| - Nhom BAGGAGE / MEAL / INSURANCE / PRIORITY_BOARDING / LOUNGE deu dung chinh seed V41          |
| - Mac dinh moi nhom chi nen cho chon 1 option / 1 hanh khach neu chua ho tro quantity phuc tap  |
+--------------------------------------------------------------------------------------------------+
```

## State Du Lieu Mau De Render Wireframe

```json
{
  "bookingId": "PNR123",
  "currency": "VND",
  "selectedPassengerId": "P1",
  "passengers": [
    {
      "passengerId": "P1",
      "fullName": "NGUYEN VAN A",
      "passengerType": "ADULT"
    },
    {
      "passengerId": "P2",
      "fullName": "TRAN THI B",
      "passengerType": "CHILD"
    }
  ],
  "selectedOptions": [
    {
      "passengerId": "P1",
      "serviceName": "Baggage Add-on",
      "optionName": "Extra 20kg",
      "price": 250000
    },
    {
      "passengerId": "P1",
      "serviceName": "Pre-order Meals",
      "optionName": "Beef Pho Meal",
      "price": 90000
    },
    {
      "passengerId": "P1",
      "serviceName": "Travel Insurance",
      "optionName": "Domestic Basic Cover",
      "price": 65000
    },
    {
      "passengerId": "P1",
      "serviceName": "Priority Boarding",
      "optionName": "Priority Boarding Standard",
      "price": 80000
    },
    {
      "passengerId": "P1",
      "serviceName": "Airport Lounge",
      "optionName": "Domestic Lounge Pass",
      "price": 350000
    }
  ],
  "subtotal": 835000
}
```

## Mapping Seed Data Sang UI

- `Baggage Add-on`
  - Extra 15kg - 180,000 VND
  - Extra 20kg - 250,000 VND
  - Extra 25kg - 320,000 VND
  - Extra 30kg - 390,000 VND
  - Extra 40kg - 520,000 VND

- `Pre-order Meals`
  - Beef Pho Meal - 90,000 VND
  - Chicken Rice Meal - 95,000 VND
  - Vegetarian Delight - 95,000 VND
  - Halal Chicken Meal - 110,000 VND
  - Kids Pasta Set - 85,000 VND
  - Premium Combo Meal - 150,000 VND

- `Travel Insurance`
  - Domestic Basic Cover - 65,000 VND
  - Domestic Premium Cover - 99,000 VND
  - International Basic Cover - 160,000 VND
  - International Premium Cover - 245,000 VND

- `Priority Boarding`
  - Priority Boarding Standard - 80,000 VND
  - Priority Boarding Plus - 120,000 VND

- `Airport Lounge`
  - Domestic Lounge Pass - 350,000 VND
  - International Lounge Pass - 550,000 VND

## Luu Y Backend

Schema hien tai render UI duoc, nhung luu booking chua that su option-based:

- `service_options` da du de render UI
- `booking_services` hien chi luu `service_id`, `quantity`, `price`
- chua co `service_option_id`

Neu muon luu dung theo wireframe nay, nen them `service_option_id` vao `booking_services`.
