# Seat Map Frontend Handoff

Tai lieu nay la ban handoff chinh thuc cho frontend de implement man `chon ghe`.

Muc tieu:

- Backend va frontend dung cung 1 cach hieu
- Frontend khong phai suy luan them tu response
- Khong lap lai loi cu: ghe `Paid` nhung hien `Available/Unavailable`
- Co the implement ngay tu tai lieu nay

## 1. Ket luan Chot

Pattern UI phai dung:

1. Seat map chi de scan nhanh
2. Moi seat cell chi hien:
   - `seatNumber`
   - `displayPrice.text`
3. Chi khi click vao ghe `selectable = true` moi duoc set `selectedSeat`
4. Detail panel chi hien thong tin cua `selectedSeat`
5. Neu ghe `selectable = false`:
   - khong set `selectedSeat`
   - khong mo panel active
   - chi show tooltip/toast nhe

Quyet dinh quan trong:

- `displayPrice.text` la text chinh de render trong seat cell
- `statusDisplay.label` chi dung trong detail panel
- Khong dung `statusDisplay.label` lam text chinh trong seat cell

## 2. API Contract Frontend Duoc Phep Rely On

Frontend goi:

`GET /api/flights/{flightId}/seats?fareId={fareId}`

Frontend duoc rely on cac field sau.

### 2.1 Header

- `fareCode`
- `policyDisplay.label`
- `policyDisplay.description`

### 2.2 Layout

- `layout.columns`
- `layout.aisleAfterColumns`
- `layout.seatClassZones`
- `rows`

### 2.3 Seat Cell

- `seatNumber`
- `displayPrice.type`
- `displayPrice.text`
- `pricingBadge.code`
- `selectable`

### 2.4 Detail Panel

- `seatNumber`
- `displayPrice.text`
- `seatTypeDisplay.label`
- `seatTypeDisplay.description`
- `seatCategoryDisplay.label`
- `seatCategoryDisplay.description`
- `statusDisplay.label`
- `included`
- `extraPrice`
- `currency`
- `policyDisplay.label`

Frontend khong nen rely on:

- `extraPrice === 0` de tu suy ra `Free`
- `reason` de tu suy ra text hien thi
- `statusDisplay.label` de render text chinh trong seat cell

## 3. Response Example

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
      { "seatClass": "BUSINESS", "rowStart": 1, "rowEnd": 2 },
      { "seatClass": "ECONOMY", "rowStart": 3, "rowEnd": 30 }
    ]
  },
  "legend": {
    "pricingItems": [
      { "code": "FREE", "label": "Free" },
      { "code": "PAID", "label": "Paid" },
      { "code": "UNAVAILABLE", "label": "Unavailable" },
      { "code": "NOT_ALLOWED", "label": "Not allowed" }
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
          "seatTypeDisplay": {
            "code": "WINDOW",
            "label": "Window",
            "description": "Seat next to the aircraft window."
          },
          "seatCategoryDisplay": {
            "code": "STANDARD",
            "label": "Standard",
            "description": "Regular seat with no special location benefits."
          },
          "statusDisplay": {
            "code": "AVAILABLE",
            "label": "Available",
            "description": "Seat is open for selection if your fare allows it."
          },
          "selectable": true,
          "included": false,
          "extraPrice": 20000,
          "currency": "VND",
          "displayPrice": {
            "type": "PAID",
            "text": "+20000 VND",
            "amount": 20000,
            "currency": "VND",
            "description": "Additional surcharge applied for this seat."
          },
          "pricingBadge": {
            "code": "PAID",
            "label": "Paid"
          }
        },
        {
          "columnLetter": "B",
          "seatId": 502,
          "seatNumber": "6B",
          "seatTypeDisplay": {
            "code": "MIDDLE",
            "label": "Middle",
            "description": "Center seat within a seat block."
          },
          "seatCategoryDisplay": {
            "code": "STANDARD",
            "label": "Standard",
            "description": "Regular seat with no special location benefits."
          },
          "statusDisplay": {
            "code": "AVAILABLE",
            "label": "Available",
            "description": "Seat is open for selection if your fare allows it."
          },
          "selectable": true,
          "included": false,
          "extraPrice": 0,
          "currency": "VND",
          "displayPrice": {
            "type": "FREE",
            "text": "Free",
            "amount": 0,
            "currency": "VND",
            "description": "This seat has no additional surcharge."
          },
          "pricingBadge": {
            "code": "FREE",
            "label": "Free"
          }
        },
        {
          "columnLetter": "C",
          "seatId": 503,
          "seatNumber": "6C",
          "seatTypeDisplay": {
            "code": "AISLE",
            "label": "Aisle",
            "description": "Seat with direct access to the aisle."
          },
          "seatCategoryDisplay": {
            "code": "STANDARD",
            "label": "Standard",
            "description": "Regular seat with no special location benefits."
          },
          "statusDisplay": {
            "code": "AVAILABLE",
            "label": "Available",
            "description": "Seat is open for selection if your fare allows it."
          },
          "selectable": false,
          "included": false,
          "extraPrice": null,
          "currency": null,
          "displayPrice": {
            "type": "NOT_ALLOWED",
            "text": "Not allowed",
            "amount": null,
            "currency": null,
            "description": "This seat is not eligible for the selected fare."
          },
          "pricingBadge": {
            "code": "NOT_ALLOWED",
            "label": "Not allowed"
          }
        }
      ]
    }
  ]
}
```

## 4. UI Structure

### 4.1 Desktop

- cot trai: seat map
- cot phai: detail panel sticky

```text
+--------------------------------------------------------------------------------------------------+
| [<-] Chon ghe                                                                                     |
| Flight #22347 • ECONOMY                                                                           |
| Seat selection with surcharge                                                                     |
| Some seats are free, others require an extra fee depending on seat type and location.            |
+--------------------------------------------------------------------------------------------------+

+--------------------------------------------------------------------+----------------------------+
| Legend                                                             | Selected seat             |
| [Available] [Unavailable] [Not allowed] [Free] [Paid]              | Empty state / details     |
+--------------------------------------------------------------------+----------------------------+

+--------------------------------------------------------------------+----------------------------+
| BUSINESS (Rows 1-2)                                                | Seat 6B                   |
| ECONOMY (Rows 3-30)                                                | Free                      |
|                                                                    | Middle                    |
| Seat cells                                                         | Standard                  |
| [6A +20k] [6B Free] [6C Not allowed]                               | Available                 |
|                                                                    | [ Select seat ]           |
+--------------------------------------------------------------------+----------------------------+
```

### 4.2 Mobile

- map full width
- detail dung bottom sheet

```text
+----------------------------------+
| Chon ghe                         |
| Flight #22347 • ECONOMY          |
| Seat selection with surcharge    |
+----------------------------------+

| Legend: Available • Free • Paid  |
| Seat map                         |
| [6A +20k] [6B Free] [6C Not al.] |

| Bottom sheet when selected       |
| Seat 6B                          |
| Free                             |
| Middle                           |
| Standard                         |
| Available                        |
| [ Select seat ]                  |
+----------------------------------+
```

## 5. Seat Cell Rules

Seat cell chi hien 2 dong:

1. `seatNumber`
2. `displayPrice.text`

Examples:

### Free

```text
+-----------+
| 6B        |
| Free      |
+-----------+
```

### Paid

```text
+-----------+
| 6A        |
| +20,000đ  |
+-----------+
```

### Unavailable

```text
+-----------+
| 3A        |
| Unavail.  |
+-----------+
```

### Not allowed

```text
+-----------+
| 1A        |
| Not allow |
+-----------+
```

Khong dung:

- `Available` lam text trong seat cell
- `statusDisplay.label` lam text chinh trong seat cell

## 6. Detail Panel Rules

Detail panel chi hien thong tin cua `selectedSeat`.

Field mapping:

| Vung UI | Field |
| --- | --- |
| Heading | `Seat {seatNumber}` |
| Gia chinh | `displayPrice.text` |
| Seat type | `seatTypeDisplay.label` |
| Seat category | `seatCategoryDisplay.label` |
| Mo ta | `seatCategoryDisplay.description` uu tien cao hon |
| Status | `statusDisplay.label` |
| Included in fare | `included ? "Yes" : "No"` |
| Additional surcharge | `extraPrice + currency` |
| Fare policy | `policyDisplay.label` |
| CTA | enable chi khi `selectable = true` |

Rule bat buoc:

- `displayPrice.text` la gia/chinh sach hien thi lon
- `statusDisplay.label` la dong status rieng
- khong bao gio thay `displayPrice.text` bang `statusDisplay.label`

## 7. Interaction Flow

### 7.1 First Load

- fetch seat map
- render legend
- render seat map
- `selectedSeat = null`
- panel hien empty state

### 7.2 Click Seat

#### Case A: seat.selectable = true

- set `selectedSeat = seat`
- highlight seat
- render detail panel
- CTA enable

#### Case B: seat.selectable = false

- khong set `selectedSeat`
- giu panel o empty state hoac state truoc do
- show tooltip/toast:
  - `Unavailable`
  - hoac `Not allowed`

### 7.3 Confirm Seat

- chi cho bam khi co `selectedSeat`
- frontend luu:
  - `seatId`
  - `seatNumber`
  - `displayPrice`
  - `seatTypeDisplay`
  - `seatCategoryDisplay`

## 8. Rendering Matrix

### 8.1 Seat Cell

| Condition | Line 1 | Line 2 | Appearance | Click |
| --- | --- | --- | --- | --- |
| `selectable = true` and `displayPrice.type = FREE` | `seatNumber` | `Free` or `Included` | mint/green | allowed |
| `selectable = true` and `displayPrice.type = PAID` | `seatNumber` | `+amount` | amber | allowed |
| `selectable = false` and `pricingBadge.code = UNAVAILABLE` | `seatNumber` | `Unavailable` | gray | blocked |
| `selectable = false` and `pricingBadge.code = NOT_ALLOWED` | `seatNumber` | `Not allowed` | rose muted | blocked |

### 8.2 Detail Panel

| Condition | Panel |
| --- | --- |
| `selectedSeat = null` | empty state |
| `selectedSeat != null` and `selectedSeat.selectable = true` | show full details |
| disabled seat clicked | khong doi `selectedSeat` |

## 9. Pseudo-code

### 9.1 Seat View Model

```ts
type SeatCellViewModel = {
  title: string;
  subtitle: string;
  appearance: "free" | "paid" | "unavailable" | "not_allowed" | "selected";
  disabled: boolean;
};

function getSeatCellViewModel(seat: SeatResponse, isSelected: boolean): SeatCellViewModel {
  const title = seat.seatNumber;
  const subtitle = seat.displayPrice.text;

  if (isSelected) {
    return {
      title,
      subtitle,
      appearance: "selected",
      disabled: false,
    };
  }

  if (!seat.selectable && seat.pricingBadge.code === "UNAVAILABLE") {
    return { title, subtitle, appearance: "unavailable", disabled: true };
  }

  if (!seat.selectable && seat.pricingBadge.code === "NOT_ALLOWED") {
    return { title, subtitle, appearance: "not_allowed", disabled: true };
  }

  if (seat.displayPrice.type === "FREE") {
    return { title, subtitle, appearance: "free", disabled: false };
  }

  return { title, subtitle, appearance: "paid", disabled: false };
}
```

### 9.2 Interaction

```ts
function onSeatClick(seat: SeatResponse) {
  if (!seat.selectable) {
    showSeatHint(seat.displayPrice.text);
    return;
  }

  setSelectedSeat(seat);
}
```

### 9.3 Detail Panel

```ts
function getSeatDetailViewModel(
  seat: SeatResponse,
  policyDisplay: PolicyDisplayResponse
) {
  return {
    heading: `Seat ${seat.seatNumber}`,
    priceText: seat.displayPrice.text,
    seatType: seat.seatTypeDisplay.label,
    seatCategory: seat.seatCategoryDisplay.label,
    description: seat.seatCategoryDisplay.description || seat.seatTypeDisplay.description,
    status: seat.statusDisplay.label,
    includedInFare: seat.included ? "Yes" : "No",
    surchargeText:
      seat.extraPrice == null || seat.currency == null
        ? "-"
        : `${seat.extraPrice} ${seat.currency}`,
    farePolicy: policyDisplay.label,
    canSelect: seat.selectable,
  };
}
```

## 10. Anti-patterns Cam Dung

```ts
// Sai: dung status lam text chinh trong seat cell
seatCellText = seat.statusDisplay.label;

// Sai: mix mau theo pricing, text theo status
seatCellClass = seat.pricingBadge.code;
seatCellText = seat.statusDisplay.label;

// Sai: suy luan free tu extraPrice o frontend
seatCellText = seat.extraPrice === 0 ? "Free" : `${seat.extraPrice}`;

// Sai: disabled seat van duoc set selected
setSelectedSeat(seat);

// Sai: co reason thi ep ve unavailable
seatCellText = seat.reason ? "Unavailable" : seat.displayPrice.text;
```

## 11. Frontend Checklist

Frontend can tu test theo checklist nay:

1. Ghe `FREE` hien `Free` hoac `Included`, khong hien `Available`
2. Ghe `PAID` hien `+amount`, khong hien `Available`
3. Ghe `UNAVAILABLE` hien `Unavailable`
4. Ghe `NOT_ALLOWED` hien `Not allowed`
5. Seat cell chi co 2 dong text
6. Click ghe disabled khong doi `selectedSeat`
7. Click ghe disabled khong enable CTA
8. Panel gia lon lay tu `displayPrice.text`
9. Panel status rieng lay tu `statusDisplay.label`
10. Khong co o ghe nao vua mang mau `Paid` vua hien text `Unavailable`
11. Khong co panel nao hien gia lon la `Unavailable` nhung status lai `Available`

## 12. Copy Chot

### Header

- Title: `Chon ghe`
- Subtitle: `Flight #{flightNumber} • {fareCode}`
- Policy label: `policyDisplay.label`
- Policy description: `policyDisplay.description`

### Panel

- Empty state title: `Selected seat`
- Empty state body: `Select a seat to view details.`
- CTA: `Select seat`

### Seat cell

- FREE: `Free`
- INCLUDED: `Included`
- PAID: `+amount`
- UNAVAILABLE: `Unavailable`
- NOT_ALLOWED: `Not allowed`

## 13. Recommendation Chot

Frontend phai lam dung nhu sau:

- Desktop:
  - map trai
  - detail panel phai
- Mobile:
  - map full width
  - detail bottom sheet
- Seat cell:
  - chi 2 dong
- Detail panel:
  - chi render khi `selectedSeat` hop le

Neu frontend lam dung tai lieu nay, UI se khong bi quay lai loi cu.

## 14. Can Dieu Chinh Tren Giao Dien Hien Tai

Muc nay dung de doi chieu voi ban frontend dang render hien tai.

### 14.1 Nhung diem da dung

- Khong con loi `Paid` nhung hien `Available/Unavailable`
- Detail panel da tach rieng khoi seat map
- Seat duoc chon da co state highlight
- CTA `Select seat` chi xuat hien trong panel

### 14.2 Nhung diem chua dung va can sua

#### A. Seat cell dang thieu `displayPrice.text`

Van de hien tai:

- Trong o ghe moi chi thay `seatNumber`
- Chua hien dong thu 2 la `displayPrice.text`

Bat buoc sua:

- Moi seat cell phai hien:
  - dong 1: `seatNumber`
  - dong 2: `displayPrice.text`

Vi du:

- ghe free:
  - `6B`
  - `Free`
- ghe tra phi:
  - `3A`
  - `200.000 VND`
- ghe unavailable:
  - `1A`
  - `Unavailable`
- ghe not allowed:
  - `6C`
  - `Not allowed`

Khong chap nhan:

- seat cell chi hien `seatNumber`
- seat cell hien `Available`

#### B. O business row dang mo ho qua

Van de hien tai:

- Ghe business disabled dang chi co so ghe
- User nhin vao khong biet la unavailable hay not allowed

Bat buoc sua:

- Neu ghe business la disabled thi dong thu 2 van phai hien:
  - `Unavailable`
  - hoac `Not allowed`

#### C. Panel gia lon dung roi, nhung seat map chua scan duoc

Van de hien tai:

- Gia chi xuat hien trong detail panel
- User phai click moi biet ghe nao free, ghe nao paid

Bat buoc sua:

- User phai scan duoc gia ngay tren map
- Detail panel chi la noi xem them chi tiet, khong phai noi duy nhat de thay gia

#### D. Horizontal overflow van con chat

Van de hien tai:

- Cot cuoi van sat mep
- O mot so viewport, grid co cam giac bi cat ngang

Bat buoc sua:

- Dam bao cot cuoi khong bi cat
- Neu map rong hon container:
  - cho phep scroll ngang ro rang
  - nhung van phai co padding o mep phai

#### E. Khoang cach seat map va detail panel can on dinh hon

Van de hien tai:

- Tren mot so viewport, panel ben phai chiem kha nhieu rong
- Seat map bi ep lai

Bat buoc sua:

- Desktop:
  - panel ben phai co chieu rong co dinh hop ly
  - seat map uu tien khong gian chinh
- Goi y:
  - panel khoang `320px - 380px`
  - seat map la vung linh hoat con lai

### 14.3 Acceptance Criteria Ban Frontend Phai Dat

Frontend duoc xem la dung khi dat du cac dieu kien sau:

1. Moi seat cell deu co 2 dong:
   - `seatNumber`
   - `displayPrice.text`
2. Ghe `PAID` hien gia tren map, khong can click moi thay
3. Ghe `FREE` hien `Free` hoac `Included` tren map
4. Ghe `UNAVAILABLE` hien `Unavailable` tren map
5. Ghe `NOT_ALLOWED` hien `Not allowed` tren map
6. Click ghe disabled khong doi `selectedSeat`
7. Detail panel chi render thong tin cua ghe `selectable = true`
8. Gia lon trong panel lay tu `displayPrice.text`
9. Trang thai rieng trong panel lay tu `statusDisplay.label`
10. Khong co cot cuoi nao bi cat mat tren viewport desktop thong dung

### 14.4 UI Mau Can Dat

Seat card phai dat muc toi thieu nhu sau:

```text
+-----------+
| 3A        |
| 200.000đ  |
+-----------+
```

```text
+-----------+
| 6B        |
| Free      |
+-----------+
```

```text
+-----------+
| 1A        |
| Unavail.  |
+-----------+
```

```text
+-----------+
| 6C        |
| Not allow |
+-----------+
```

Neu frontend chua dat duoc muc nay, thi xem nhu man seat map chua hoan thanh.
