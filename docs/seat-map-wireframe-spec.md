# Seat Map Frontend Spec

Tai lieu nay la ban spec de frontend render man `chon ghe` chinh xac theo contract backend hien tai.

Muc tieu:

- Chot `response` frontend duoc phep rely on
- Chot `flow` tu luc vao man den luc chon ghe
- Chot `wireframe` desktop/mobile
- Chot `render rules` cho tung trang thai ghe

Philosophy UI:

- `Seat map` dung de scan nhanh
- `Detail panel` dung de ra quyet dinh
- Khong hien `0 VND` hoac `$0`
- Neu ghe khong tinh them tien thi hien `Free` hoac `Included`

## 1. UI Strategy

Pattern duoc chot:

1. Ban dau seat map chi hien thong tin ngan
2. User click vao ghe
3. Panel ben phai (desktop) hoac bottom sheet (mobile) hien chi tiet day du
4. User bam `Select seat` de xac nhan

Ly do:

- Nhe giao dien
- De scan nhanh cac ghe con trong
- Giong pattern thuc te cua hang khong va OTA
- Frontend khong phai nhoi qua nhieu text vao tung seat cell

## 2. API Response Contract

Frontend nen goi:

`GET /api/flights/{flightId}/seats?fareId={fareId}`

Response seed mau de render giao dien:

```json
{
  "flightId": 11,
  "fareId": 101,
  "fareClassId": 1,
  "fareCode": "ECONOMY",
  "seatSelectionPolicy": "PAID",
  "policyDisplay": {
    "code": "PAID",
    "label": "Seat selection with surcharge",
    "description": "Some seats require an additional fee depending on their type or location."
  },
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
    "seatCategories": ["STANDARD", "PREFERRED", "EXIT_ROW", "EXTRA_LEGROOM"],
    "statusItems": [
      {
        "code": "AVAILABLE",
        "label": "Available",
        "description": "Seat is open for selection if your fare allows it."
      },
      {
        "code": "UNAVAILABLE",
        "label": "Unavailable",
        "description": "Seat is blocked from sale or temporarily disabled."
      }
    ],
    "seatTypeItems": [
      {
        "code": "WINDOW",
        "label": "Window",
        "description": "Seat next to the aircraft window."
      },
      {
        "code": "MIDDLE",
        "label": "Middle",
        "description": "Center seat within a seat block."
      },
      {
        "code": "AISLE",
        "label": "Aisle",
        "description": "Seat with direct access to the aisle."
      }
    ],
    "seatCategoryItems": [
      {
        "code": "STANDARD",
        "label": "Standard",
        "description": "Regular seat with no special location benefits."
      },
      {
        "code": "EXIT_ROW",
        "label": "Exit row",
        "description": "Seat located at an exit row, usually with more space."
      }
    ],
    "pricingItems": [
      {
        "code": "FREE",
        "label": "Free",
        "description": "Seat can be selected without any extra charge."
      },
      {
        "code": "PAID",
        "label": "Paid",
        "description": "Seat requires an additional surcharge."
      },
      {
        "code": "UNAVAILABLE",
        "label": "Unavailable",
        "description": "Seat is blocked, reserved, or already booked."
      },
      {
        "code": "NOT_ALLOWED",
        "label": "Not allowed",
        "description": "Seat cannot be chosen for this fare or at this stage."
      }
    ]
  },
  "rows": [
    {
      "rowNumber": 6,
      "seatClass": "ECONOMY",
      "cells": [
        {
          "columnLetter": "A",
          "seatId": 501,
          "seatNumber": "6A",
          "seatClass": "ECONOMY",
          "seatType": "WINDOW",
          "seatTypeDisplay": {
            "code": "WINDOW",
            "label": "Window",
            "description": "Seat next to the aircraft window."
          },
          "seatCategory": "STANDARD",
          "seatCategoryDisplay": {
            "code": "STANDARD",
            "label": "Standard",
            "description": "Regular seat with no special location benefits."
          },
          "status": "AVAILABLE",
          "statusDisplay": {
            "code": "AVAILABLE",
            "label": "Available",
            "description": "Seat is open for selection if your fare allows it."
          },
          "selectable": true,
          "included": false,
          "extraPrice": 20000,
          "currency": "VND",
          "reason": null,
          "displayPrice": {
            "type": "PAID",
            "text": "+20000 VND",
            "amount": 20000,
            "currency": "VND",
            "description": "Additional surcharge applied for this seat."
          },
          "pricingBadge": {
            "code": "PAID",
            "label": "Paid",
            "description": "This seat requires an extra surcharge."
          }
        },
        {
          "columnLetter": "B",
          "seatId": 502,
          "seatNumber": "6B",
          "seatClass": "ECONOMY",
          "seatType": "MIDDLE",
          "seatTypeDisplay": {
            "code": "MIDDLE",
            "label": "Middle",
            "description": "Center seat within a seat block."
          },
          "seatCategory": "STANDARD",
          "seatCategoryDisplay": {
            "code": "STANDARD",
            "label": "Standard",
            "description": "Regular seat with no special location benefits."
          },
          "status": "AVAILABLE",
          "statusDisplay": {
            "code": "AVAILABLE",
            "label": "Available",
            "description": "Seat is open for selection if your fare allows it."
          },
          "selectable": true,
          "included": false,
          "extraPrice": 0,
          "currency": "VND",
          "reason": null,
          "displayPrice": {
            "type": "FREE",
            "text": "Free",
            "amount": 0,
            "currency": "VND",
            "description": "This seat has no additional surcharge."
          },
          "pricingBadge": {
            "code": "FREE",
            "label": "Free",
            "description": "This seat does not add any surcharge for the selected fare."
          }
        },
        {
          "columnLetter": "C",
          "seatId": 503,
          "seatNumber": "6C",
          "seatClass": "ECONOMY",
          "seatType": "AISLE",
          "seatTypeDisplay": {
            "code": "AISLE",
            "label": "Aisle",
            "description": "Seat with direct access to the aisle."
          },
          "seatCategory": "STANDARD",
          "seatCategoryDisplay": {
            "code": "STANDARD",
            "label": "Standard",
            "description": "Regular seat with no special location benefits."
          },
          "status": "AVAILABLE",
          "statusDisplay": {
            "code": "AVAILABLE",
            "label": "Available",
            "description": "Seat is open for selection if your fare allows it."
          },
          "selectable": false,
          "included": false,
          "extraPrice": null,
          "currency": null,
          "reason": "SEAT_NOT_ALLOWED_FOR_FARE",
          "displayPrice": {
            "type": "NOT_ALLOWED",
            "text": "Not allowed",
            "amount": null,
            "currency": null,
            "description": "This seat is not eligible for the selected fare."
          },
          "pricingBadge": {
            "code": "NOT_ALLOWED",
            "label": "Not allowed",
            "description": "This seat is not eligible for the selected fare."
          }
        }
      ]
    }
  ]
}
```

## 3. Field Mapping Cho Frontend

Frontend chi nen rely on cac field sau:

- Header:
  - `fareCode`
  - `policyDisplay.label`
  - `policyDisplay.description`

- Layout:
  - `layout.columns`
  - `layout.aisleAfterColumns`
  - `layout.seatClassZones`

- Seat cell:
  - `seatNumber`
  - `displayPrice.text`
  - `pricingBadge.code`
  - `statusDisplay.code`
  - `selectable`

- Detail panel:
  - `seatNumber`
  - `displayPrice.text`
  - `seatTypeDisplay.label`
  - `seatCategoryDisplay.label`
  - `seatTypeDisplay.description`
  - `seatCategoryDisplay.description`
  - `statusDisplay.label`
  - `included`
  - `extraPrice`
  - `currency`

Frontend khong nen:

- Tu suy luan `0 => Free`
- Tu suy luan `reason => text`
- Tu format text chinh cho free/paid/not allowed

## 4. Flow Chi Tiet

### 4.1 Data Flow

1. User chon fare o man search/revalidate
2. Frontend luu `flightId` va `fareId`
3. Khi vao man chon ghe, frontend goi:
   - `GET /api/flights/{flightId}/seats?fareId={fareId}`
4. Frontend render seat map
5. User click 1 ghe
6. Frontend cap nhat `selectedSeat`
7. Panel chi tiet render theo `selectedSeat`
8. User bam `Select seat`
9. Frontend luu `seatId` vao state booking/passenger step
10. Sau do moi goi API save booking seat o step tiep theo

### 4.2 Interaction Flow

#### First load

- Seat map hien ra voi legend
- Chua co ghe nao duoc chon
- Panel ben phai hien empty state

#### User click ghe `AVAILABLE + selectable`

- Seat cell vao state `selected`
- Panel ben phai hien chi tiet day du
- CTA `Select seat` duoc enable

#### User click ghe `UNAVAILABLE`

- Khong cho selected
- Co the show tooltip nhe: `Unavailable`

#### User click ghe `NOT_ALLOWED`

- Khong cho selected
- Co the show tooltip nhe: `Not allowed for this fare`

#### User confirm

- Frontend luu:
  - `seatId`
  - `seatNumber`
  - `displayPrice`
  - `seatTypeDisplay`
  - `seatCategoryDisplay`

## 5. Wireframe Desktop

```text
+--------------------------------------------------------------------------------------------------+
| [<-] Chon ghe                                                                                     |
| Flight #21008 • Economy                                                                           |
| Seat selection with surcharge                                                                     |
| Some seats are free, others require an extra fee depending on seat type and location.            |
+--------------------------------------------------------------------------------------------------+

+--------------------------------------------------------------------+----------------------------+
| Legend                                                             | Selected seat             |
|                                                                    |                            |
| Availability                                                       | [empty state]             |
| [green] Available   [gray] Unavailable   [rose] Not allowed        | Select a seat to view     |
|                                                                    | details.                  |
| Pricing                                                            |                            |
| [mint] Free         [amber] Paid                                   | Fare policy               |
|                                                                    | Seat selection with       |
|                                                                    | surcharge                 |
+--------------------------------------------------------------------+----------------------------+

+--------------------------------------------------------------------+----------------------------+
| BUSINESS (Rows 1-2)                                                | Khi user chon ghế 6B      |
|                                                                    |                            |
|        A           B           C       AISLE      D         E       | Seat 6B                   |
| Row 1 [Not allow] [Not allow] [Not allow]       [Not allow][N/A]   | Free                      |
| Row 2 [Not allow] [Not allow] [Not allow]       [Not allow][N/A]   |                            |
|                                                                    | Middle                    |
| ECONOMY (Rows 3-30)                                                | Standard                  |
|                                                                    |                            |
|        A           B           C       AISLE      D         E       | Center seat within a      |
| Row 6 [+20k]      [Free]      [Not all.]        [+20k]    [Free]   | seat block.               |
| Row 7 [+20k]      [Free]      [+20k]            [+20k]    [Free]   |                            |
| Row 8 [Unavail.]  [Free]      [+20k]            [+20k]    [Free]   | Status                    |
|                                                                    | Available                 |
|                                                                    |                            |
|                                                                    | Included in fare          |
|                                                                    | No                        |
|                                                                    |                            |
|                                                                    | Additional surcharge      |
|                                                                    | 0 VND                     |
|                                                                    |                            |
|                                                                    | [ Select seat ]           |
+--------------------------------------------------------------------+----------------------------+
```

## 6. Wireframe Mobile

Mobile khong dung panel ben phai. Dung `bottom sheet`.

```text
+----------------------------------+
| Chon ghe                         |
| Flight #21008 • Economy          |
| Seat selection with surcharge    |
+----------------------------------+

| Legend: Available • Free • Paid  |

| BUSINESS                         |
| Row 1 [N/A] [N/A] [N/A]          |
| Row 2 [N/A] [N/A] [N/A]          |

| ECONOMY                          |
| Row 6 [+20k] [Free] [+20k]       |
| Row 7 [+20k] [Free] [+20k]       |
| Row 8 [Unav] [Free] [+20k]       |

+----------------------------------+
| Bottom sheet when seat selected  |
| Seat 6B                          |
| Free                             |
| Middle                           |
| Standard                         |
| Available                        |
|                                  |
| [ Select seat ]                  |
+----------------------------------+
```

## 7. Seat Cell Spec

Seat cell chi nen hien 2 tang thong tin:

1. `seatNumber`
2. `displayPrice.text`

Example:

### Free seat

```text
+-----------+
| 6B        |
| Free      |
+-----------+
```

### Paid seat

```text
+-----------+
| 6A        |
| +20,000đ  |
+-----------+
```

### Selected seat

```text
+===========+
| 6B        |
| Free      |
+===========+
```

### Unavailable seat

```text
+-----------+
| 3A        |
| Unavail.  |
+-----------+
```

### Not allowed seat

```text
+-----------+
| 1A        |
| Not allow |
+-----------+
```

## 8. Detail Panel Spec

Panel chi tiet la noi show full metadata.

Desktop panel:

```text
+------------------------------+
| Selected seat                |
|                              |
| Seat 6B                      |
| Free                         |
|                              |
| Middle                       |
| Standard                     |
|                              |
| Center seat within block.    |
|                              |
| Status                       |
| Available                    |
|                              |
| Fare policy                  |
| Seat selection with surcharge|
|                              |
| Included in fare             |
| No                           |
|                              |
| Additional surcharge         |
| 0 VND                        |
|                              |
| [ Select seat ]              |
+------------------------------+
```

Rule:

- Tieu de:
  - `Seat {seatNumber}`
- Gia:
  - `displayPrice.text`
- Loai ghe:
  - `seatTypeDisplay.label`
- Nhom ghe:
  - `seatCategoryDisplay.label`
- Mo ta:
  - uu tien `seatCategoryDisplay.description`
  - neu can co the them `seatTypeDisplay.description`
- Trang thai:
  - `statusDisplay.label`
- Fare policy:
  - `policyDisplay.label`
- CTA:
  - enable neu `selectable = true`
  - disable neu `selectable = false`

## 9. Render Rules Theo Trang Thai

### `AVAILABLE + selectable + displayPrice.type = FREE`

- Seat cell:
  - hien `seatNumber`
  - hien `Free` hoac `Included`
- Mau:
  - background sang, outline xanh/mint nhe
- Click:
  - duoc click

### `AVAILABLE + selectable + displayPrice.type = PAID`

- Seat cell:
  - hien `seatNumber`
  - hien `+amount`
- Mau:
  - background vang nhat, border amber
- Click:
  - duoc click

### `selectable = false + pricingBadge.code = UNAVAILABLE`

- Seat cell:
  - hien `Unavailable`
- Mau:
  - gray neutral
- Click:
  - khong chon

### `selectable = false + pricingBadge.code = NOT_ALLOWED`

- Seat cell:
  - hien `Not allowed`
- Mau:
  - gray/rose muted
- Click:
  - khong chon

## 10. Legend Rules

Legend chot de dung:

- Availability
  - Available
  - Unavailable
  - Not allowed

- Pricing
  - Free
  - Paid

Khong can dua `Window / Middle / Aisle` vao legend chinh o phase dau.
Ly do:

- Thong tin nay de trong panel da du
- Neu nhieu legend qua man hinh se roi

Neu desktop rong va muon hien them:

- Seat type
  - Window
  - Middle
  - Aisle

## 11. Empty State Spec

Khi chua chon ghe:

```text
+------------------------------+
| Selected seat                |
|                              |
| Select a seat to view        |
| details.                     |
|                              |
| You will see:                |
| seat number                  |
| seat type                    |
| seat category                |
| price                        |
| availability                 |
+------------------------------+
```

## 12. Frontend Implementation Rules

Frontend can follow exactly:

1. Render row theo `rows`
2. Render cot theo `layout.columns`
3. Match `cells[].columnLetter` vao tung cot
4. Sau cot nam trong `layout.aisleAfterColumns`, chen 1 khoang aisle
5. Moi `seatClassZone` render thanh 1 section title
6. Seat cell text chinh phai lay tu `displayPrice.text`
7. Detail panel text chinh phai lay tu `seatTypeDisplay`, `seatCategoryDisplay`, `statusDisplay`
8. Khong hardcode `0 => Free`
9. Khong hardcode `AVAILABLE => Available`
10. Khong format text gia chinh o frontend neu khong can

## 13. Copy Chot De Frontend Dung

Header:

- Title: `Chon ghe`
- Subtitle line 1: `Flight #{flightNumber} • {fareCode}`
- Subtitle line 2: `policyDisplay.label`
- Description: `policyDisplay.description`

Panel:

- Section title: `Selected seat`
- Empty state: `Select a seat to view details.`
- CTA: `Select seat`

Seat cell:

- Free: `Free`
- Included: `Included`
- Paid: `+amount`
- Unavailable: `Unavailable`
- Not allowed: `Not allowed`

## 14. Recommendation Chot

Frontend nen lam dung pattern sau:

- Desktop:
  - map trai
  - detail panel phai
- Mobile:
  - map full width
  - bottom sheet chi tiet
- Seat cell:
  - 2 dong toi da
- Detail panel:
  - moi hien full metadata

Day la pattern can bang nhat giua:

- de scan
- de chon
- de maintain
- sat thuc te san pham hang khong
