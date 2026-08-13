# Payment Playwright Verification

Ngay test: `2026-06-03`

Muc tieu:

- xac minh runtime behavior cua `PAY_QR`
- khong chi dua vao unit test
- tach ro case nao dang loi that su

Frontend test runner:

- `http://localhost:5173`

Backend duoc FE local goi den:

- `http://r5fyz2laprvyctuy8ppjfdvg.4.190.168.109.sslip.io`

Script da chay:

- `AirlineBooking-fe/tmp/playwright-checkout-qr.mjs`

## Scenario 1: Fresh booking -> PAY_QR

Flow:

1. search flight `SGN -> HAN`, `2026-06-04`
2. tao booking moi
3. vao checkout
4. chon `Mobile Banking VietQR`
5. submit payment

Ket qua:

- request:
  - `bookingId = 115`
  - `method = PAYOS_QR`
- payment moi:
  - `paymentId = 129`
- backend response:
  - `expireAt = 2026-06-03T14:54:34.573495295Z`
- UI hien:
  - `Time left: 03:26`

Do lai thoi gian runtime:

- `expireAt` trong response:
  - `2026-06-03T14:58:36.029533762Z`
- `Date` header tu backend:
  - `Wed, 03 Jun 2026 14:53:36 GMT`
- thoi diem client nhan response:
  - `2026-06-03T14:55:09.297Z`
- chenh lech `expireAt - client_observed_at`:
  - `207 giay` (~`03:27`)

Danh gia:

- **FAIL ve runtime countdown nhin tu client**
- day la payment moi, khong phai payment cu
- frontend khong tu tinh sai
- backend thuc ra dang set `expireAt` = **server time + 5 phut**
- nhung **clock cua backend/server dang cham khoang 93 giay** so voi clock client
- vi vay khi client nhan duoc response thi session nhin thay chi con ~`03:26`

## Scenario 2: Fresh booking -> PAY_LATER -> tao PAY_QR tren cung booking

Flow:

1. tao booking moi
2. vao checkout
3. chon `PAY_LATER`
4. lay `bookingId` tu response
5. goi lai `POST /api/payments` voi cung booking va method `PAYOS_QR`

Ket qua `PAY_LATER`:

- `bookingId = 116`
- `paymentId = 130`
- `expireAt = 2026-06-03T15:49:39.075894586Z`

Ket qua `PAY_QR` sau `PAY_LATER`:

- `paymentId = 131`
- `expireAt = 2026-06-03T14:54:54.183426128Z`

Do lai thoi gian runtime:

- thoi diem client nhan response:
  - `2026-06-03T14:55:28.018Z`
- `expireAt - client_observed_at`:
  - ~`208 giay` (~`03:28`)

Danh gia:

- **van cung bi clock skew**
- `PAY_LATER` da mo rong booking len khoang `60 phut`
- nhung `PAY_QR` sau do van chi con khoang `03:2x` khi do bang dong ho client
- nghia la day khong phai bug rieng cua transition `PAY_LATER -> PAY_QR`
- day la van de dong ho server / container

## Ket luan

Ket luan moi sau khi do `Date` header va thoi diem client nhan response:

- backend payment logic dang tao `expireAt` theo **dong ho cua server**
- server dang cham khoang **93 giay**
- vi vay moi session `PAY_QR` 5 phut khi nhin tu client chi con khoang `03:26`

Noi cach khac:

- day **khong phai bug business rule cua `PAY_QR` nua**
- day la bug **ha tang / server clock skew**

## Y nghia ky thuat

Can xu ly o ha tang:

1. dong bo gio cua server/VM/container bang NTP
2. kiem tra timezone va system clock tren host chay backend
3. redeploy sau khi sua clock
4. chay lai script Playwright nay de xac minh `expireAt - client_observed_at` gan `300 giay`

## Recommendation

Khong can mo rong them matrix test truoc khi sua ha tang.

Thu tu hop ly:

1. sua server clock skew
2. chay lai script Playwright
3. neu sau do van khong len gan `05:00` moi tiep tuc quay lai debug logic code
