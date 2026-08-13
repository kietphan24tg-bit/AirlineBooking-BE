# Seat Selection And Ancillary APIs

Tai lieu nay chot huong API cho man `chon ghe` va `dich vu bo sung`, de frontend co contract ro rang de render UI.

## 1. Ket luan nhanh

- Man chon ghe nen goi bang `flightId` va `fareId`.
- Khong can gui ca object `flight` hay `fare` trong request seat map.
- `fareId` du de backend suy ra:
  - `fareClass`
  - `seatSelectionPolicy`
  - nhom ghe duoc chon
  - ghe nao free, ghe nao phai tra them
- Dich vu bo sung nen co `GET` API rieng de frontend lay catalog.
- Catalog ancillary hien tai la seed data co dinh, nen khong can request body hay filter phuc tap o phase dau.
- Tuy catalog dang "co dinh", van nen expose qua API thay vi hardcode frontend, de sau nay thay doi seed/price/icon khong can release lai FE.

## 2. Seat Selection API

### Endpoint

`GET /api/flights/{flightId}/seats?fareId={fareId}`

### Vi sao can ca `flightId` va `fareId`

- `flightId` xac dinh seat inventory thuc te cua chuyen bay.
- `fareId` xac dinh fare ma user da chon trong search/revalidate.
- Tu `fareId`, backend suy ra `fareClassId` va policy chon ghe.
- Backend dong thoi validate `fareId` co thuoc `flightId` hay khong.

### Flow frontend de dung

1. User search flight.
2. User chon 1 fare.
3. Frontend luu `flightId` + `fareId`.
4. Khi mo man chon ghe, frontend goi `GET /api/flights/{flightId}/seats?fareId={fareId}`.
5. Frontend render map ghe dua tren response.
6. Khi user chot ghe cho tung passenger, frontend moi goi API save booking seats sau.

### Response shape de frontend render

```json
{
  "flightId": 101,
  "fareId": 1001,
  "fareClassId": 3,
  "fareCode": "ECONOMY_FLEX",
  "seatSelectionPolicy": "PAID",
  "includedSeatCategoryCode": null,
  "currency": "VND",
  "layout": {
    "columns": ["A", "B", "C", "D", "E", "F"],
    "aisleAfterColumns": ["C"],
    "seatClassZones": [
      {
        "seatClass": "BUSINESS",
        "rowStart": 1,
        "rowEnd": 2
      },
      {
        "seatClass": "ECONOMY",
        "rowStart": 3,
        "rowEnd": 30
      }
    ]
  },
  "legend": {
    "statuses": ["AVAILABLE", "RESERVED", "BOOKED", "UNAVAILABLE"],
    "seatTypes": ["WINDOW", "MIDDLE", "AISLE"],
    "seatCategories": ["STANDARD", "PREFERRED", "EXIT_ROW", "EXTRA_LEGROOM"]
  },
  "rows": [
    {
      "rowNumber": 12,
      "seatClass": "ECONOMY",
      "cells": [
        {
          "columnLetter": "A",
          "seatId": 501,
          "seatNumber": "12A",
          "seatClass": "ECONOMY",
          "seatType": "WINDOW",
          "seatCategory": "STANDARD",
          "status": "AVAILABLE",
          "selectable": true,
          "included": false,
          "extraPrice": 20000,
          "currency": "VND",
          "reason": null
        },
        {
          "columnLetter": "D",
          "seatId": 502,
          "seatNumber": "12D",
          "seatClass": "ECONOMY",
          "seatType": "AISLE",
          "seatCategory": "STANDARD",
          "status": "AVAILABLE",
          "selectable": false,
          "included": false,
          "extraPrice": null,
          "currency": null,
          "reason": "SEAT_NOT_ALLOWED_FOR_FARE"
        }
      ]
    },
    {
      "rowNumber": 13,
      "seatClass": "ECONOMY",
      "cells": [
        {
          "columnLetter": "B",
          "seatId": 503,
          "seatNumber": "13B",
          "seatClass": "ECONOMY",
          "seatType": "MIDDLE",
          "seatCategory": "STANDARD",
          "status": "RESERVED",
          "selectable": false,
          "included": false,
          "extraPrice": null,
          "currency": null,
          "reason": "SEAT_UNAVAILABLE"
        }
      ]
    }
  ]
}
```

### Y nghia field quan trong

- `seatSelectionPolicy`
  - `NOT_ALLOWED`: fare nay khong duoc chon ghe truoc.
  - `CHECKIN_ONLY`: chi duoc chon luc check-in.
  - `PAID`: duoc chon truoc, co the phat sinh phu thu.
  - `INCLUDED`: duoc chon truoc, mot so ghe duoc mien phi theo hang ve.
- `includedSeatCategoryCode`
  - Dung khi `seatSelectionPolicy = INCLUDED`.
  - Cho frontend hien note kieu: "Fare nay duoc mien phi den STANDARD".
- `selectable`
  - `true` thi cho click.
  - `false` thi render disabled.
- `layout.columns`
  - Thu tu cot chuan de frontend render grid.
- `layout.aisleAfterColumns`
  - Danh dau cot nao xong thi chen khoang trong / loi di.
- `rows[].rowNumber`
  - So hang ghe de frontend render tung dong.
- `rows[].cells`
  - Danh sach ghe thuc te trong row do.
- `cells[].columnLetter`
  - Khoa de frontend dat ghe vao dung cot.
- `included`
  - `true` thi ghe nay khong tinh them tien.
- `extraPrice`
  - So tien phai tra them cho ghe do.
  - Neu `included = true` thi gia tri se la `0`.
- `reason`
  - `SEAT_UNAVAILABLE`
  - `SEAT_NOT_ALLOWED_FOR_FARE`
  - `CHECKIN_ONLY`
  - `NOT_ALLOWED`

### Frontend render guide

- Group seat theo `seatClass` truoc, neu can tach zone.
- Render theo `layout.columns`, khong tu sort lai bang `seatNumber`.
- Sau moi cot nam trong `layout.aisleAfterColumns`, chen 1 khoang trong / aisle.
- Render `rows[]` theo thu tu backend tra ve.
- Mau trang thai toi thieu:
  - `AVAILABLE + selectable`: cho click
  - `AVAILABLE + !selectable`: disabled
  - `RESERVED/BOOKED/UNAVAILABLE`: occupied/blocked
- Badge gia:
  - `included = true`: hien `Included` hoac `0 VND`
  - `extraPrice > 0`: hien phu thu
- Tooltip / legend nen dua tren `seatType`, `seatCategory`, `reason`.

### Phan frontend nen bo sung sau

Response hien tai da du de render UI map co row/column/aisle. Neu muon render dep hon o phase sau, co the bo sung them:

- `deck`
- `zoneCode`
- `isAisleBreak`
- `coordinates`
- them metadata legend chi tiet hon cho mau/icon

Frontend khong can tu tach `seatNumber` thanh row/column o phase nay nua.

## 3. API save ghe theo booking

Seat map API chi de doc. API ghi du lieu nen tach rieng:

`POST /api/bookings/{bookingId}/seats`

Request de xuat:

```json
{
  "selections": [
    {
      "passengerId": 2001,
      "seatId": 501
    },
    {
      "passengerId": 2002,
      "seatId": 502
    }
  ]
}
```

Quy tac:

- Chỉ ap dung cho passenger co ghe: `ADULT`, `CHILD`.
- `INFANT` khong chon ghe.
- Backend phai validate ghe thuoc dung booking flight/fare.
- Backend phai tinh them `seat surcharge` vao tong booking neu co.
- Neu co race condition, tra `409`.

## 4. Ancillary Catalog API

### Endpoint de xuat

`GET /api/services`

Hoac neu muon giu namespace theo flight step:

`GET /api/flights/ancillaries`

Khuyen nghi dung `GET /api/services` vi catalog ancillary hien tai khong phu thuoc vao `flightId`.

### Co can request gi khong

Phase dau: khong.

Chi can:

- khong request body
- khong query bat buoc
- co the them query optional sau nay nhu:
  - `activeOnly=true`
  - `passengerType=CHILD`
  - `tripType=DOMESTIC`

### Vi sao van nen co API du catalog dang co dinh

- Frontend khong hardcode ten/gia/icon.
- Backend giu source of truth theo DB seed.
- Sau nay doi gia, an option, them option moi thi FE khong can deploy lai.
- De test/staging/prod dong bo contract.

### Response de xuat

```json
{
  "currency": "VND",
  "services": [
    {
      "serviceId": 1,
      "serviceCode": "BAGGAGE",
      "serviceName": "Baggage Add-on",
      "iconUrl": "https://img.icons8.com/fluency/96/suitcase.png",
      "selectionMode": "SINGLE",
      "options": [
        {
          "serviceOptionId": 11,
          "optionName": "Extra 15kg",
          "description": "Add one checked baggage allowance up to 15kg.",
          "price": 180000,
          "currency": "VND",
          "imageUrl": "https://...",
          "active": true
        }
      ]
    }
  ]
}
```

### Field frontend can rely on

- `serviceCode`
  - `BAGGAGE`
  - `MEAL`
  - `INSURANCE`
  - `PRIORITY_BOARDING`
  - `LOUNGE`
- `selectionMode`
  - Phase dau nen de `SINGLE`
  - Y nghia: moi passenger, moi service group, chi duoc chon 1 option
- `serviceOptionId`
  - Frontend phai luu id nay de save booking services dung theo option

## 5. API save ancillary vao booking

Catalog API chi de doc. API luu lua chon ancillary nen tach rieng:

`POST /api/bookings/{bookingId}/services`

Request de xuat:

```json
{
  "selections": [
    {
      "passengerId": 2001,
      "serviceOptionId": 11,
      "quantity": 1
    },
    {
      "passengerId": 2001,
      "serviceOptionId": 21,
      "quantity": 1
    }
  ]
}
```

Quy tac phase dau:

- Moi `serviceCode` chi duoc chon 1 option tren 1 passenger.
- `quantity` tam thoi mac dinh = `1`.
- Gia chot theo `service_option.price` tu backend, frontend khong gui gia.
- Backend nen luu theo `service_option_id`, khong chi `service_id`.

## 6. Gap hien tai trong schema

Schema render catalog thi on, nhung save booking services chua du chinh xac:

- `service_options` da co du lieu de render UI.
- `booking_services` hien moi luu:
  - `booking_id`
  - `passenger_id`
  - `service_id`
  - `quantity`
  - `price`
- Chua co `service_option_id`.

Vi vay de support dung UI option-based, nen them:

- cot `service_option_id` vao `booking_services`
- unique constraint hop ly cho `(booking_id, passenger_id, service_id)` hoac `(booking_id, passenger_id, service_option_id)` tuy cach enforce

Khuyen nghi:

- luu ca `service_id` va `service_option_id`
- `price` la snapshot gia tai thoi diem booking

## 7. Thu tu implement de xuat

1. Giu nguyen va document hoa `GET /api/flights/{flightId}/seats?fareId=...`.
2. Tao `GET /api/services` doc catalog tu `services` + `service_options`.
3. Them migration bo sung `service_option_id` vao `booking_services`.
4. Cap nhat entity/repository cho booking services.
5. Implement `POST /api/bookings/{bookingId}/services`.
6. Sau do moi tinh total ancillary vao invoice/booking summary.

## 8. Cac quyet dinh nen chot voi frontend

- Frontend se goi seat map truoc hay sau khi tao booking.
- Man seat map co can view theo tung passenger hay chi 1 map chung.
- Co can render so do khoang trong/lối di o phase dau hay chi grid theo `seatNumber`.
- Ancillary co can filter theo `passengerType` ngay tu backend khong.
- Co cho phep bo chon service da chon bang cach gui `null`/xoa item hay frontend gui full replacement list.

## 9. Recommendation

- Seat map: dung API hien tai, khong doi contract input.
- Ancillary catalog: tao `GET /api/services`, khong can request body.
- Save ancillary: bat buoc them `service_option_id` truoc khi code API ghi.
- Frontend phase dau co the ve duoc UI tu contract hien tai ma chua can seat coordinates phuc tap.
