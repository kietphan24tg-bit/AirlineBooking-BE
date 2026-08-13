# Payment Rules

Tai lieu nay la source of truth cho payment window va session rules cua 3 payment method dang duoc quan tam:

- `PAY_QR` (`PAYOS`)
- `PAY_LATER`
- `STRIPE_CARD`

Code chinh:

- `module-payment/src/main/java/com/example/modulepayment/application/PaymentService.java`
- `module-payment/src/main/java/com/example/modulepayment/application/PaymentResultService.java`

## 1. Hai dong ho can tach ro

### `pnrExpiresAt`

`pnrExpiresAt` la booking gate chung.

No tra loi:

- Booking con duoc phep thanh toan khong?
- Co con duoc tao / reuse payment method nua khong?

Neu `pnrExpiresAt <= now` thi booking duoc xem la het han thanh toan.

### `sessionExpiresAt`

`sessionExpiresAt` la dong ho cua tung payment session cu the.

No tra loi:

- QR nay con bao lau?
- Checkout Stripe nay con bao lau?
- Session pay-later nay con bao lau?

Frontend countdown phai uu tien doc tu `payment.expireAt` / `payment.sessionExpiresAt` do backend tra ve.

## 2. Cap chung cua booking payment

Tat ca payment method deu bi gioi han boi cap chung cua booking:

- `maxPaymentHoldUntil = booking.createdAt + 60 minutes`
- `pnrExpiresAt` khong duoc extend vuot qua `maxPaymentHoldUntil`
- Neu method target expire vuot cap, backend dung `maxPaymentHoldUntil`
- Cap nay chan viec user doi qua lai method de giu ghe / fare vo han

Vi du:

- Booking tao luc 10:00 thi cap chung la 11:00
- Pay Later tao luc 10:50 khong duoc giu toi 11:50
- PayOS QR tao luc 10:58 khong duoc giu toi 11:03

## 3. Rule tong quat theo method

### `PAY_QR` / `PAYOS`

`PAY_QR` la short-lived payment session.

Rule hien tai:

1. Booking phai con song, tuc la `pnrExpiresAt > now`
2. QR co `sessionExpiresAt` rieng
3. Khi tao hoac reuse QR, booking se duoc nang len it nhat bang session QR neu hien tai con ngan hon
4. Neu booking da dai hon do `PAY_LATER` hoac `STRIPE`, QR khong duoc shrink booking xuong
5. QR khong duoc extend booking vuot qua cap chung `booking.createdAt + 60 minutes`

#### Tao / reuse `PAY_QR`

Neu booking con song:

1. Khong co QR cu:
   - `sessionExpiresAt = min(now + 5 minutes, maxPaymentHoldUntil)`
2. Co QR cu va con `> 5 minutes`:
   - giu nguyen `sessionExpiresAt`
3. Co QR cu va con `<= 5 minutes`:
   - `sessionExpiresAt = min(previousSessionExpiresAt + 5 minutes, maxPaymentHoldUntil)`

Sau do:

- `payment.sessionExpiresAt = qrSessionExpireAt`
- `payment.expireAt` tra ve cho FE = `qrSessionExpireAt`
- `booking.pnrExpiresAt` duoc nang len toi thieu bang `qrSessionExpireAt` neu dang ngan hon
- `booking.pnrExpiresAt` khong duoc vuot qua cap chung cua booking

Y nghia production:

- Neu booking dang con 3 phut ma user vao `PAY_QR`, QR van co the duoc tao voi cua so toi da 5 phut
- Booking se duoc keo len du de QR session do song het neu chua cham cap chung
- Neu booking da dang co 1 gio do `PAY_LATER`, QR van chi co session 5 phut rieng, booking thi giu theo window dai hon
- Neu booking gan het cap 60 phut, QR chi duoc song toi cap do

### `PAY_LATER`

`PAY_LATER` la method xin them thoi gian cho booking.

Rule:

1. Co the mo rong `pnrExpiresAt` len hold window 60 phut, nhung khong vuot cap chung cua booking
2. Reuse session cu neu con hop le
3. Neu co session `PAY_LATER` cu dang `PENDING` hoac `SUSPENDED` va booking hold van con, he thong uu tien resume session cu
4. Khi reuse, `sessionExpiresAt` cu duoc giu nguyen neu van con hop le, khong reset vo han moi lan user quay lai
5. Neu session cu khong reuse duoc va backend tao session moi, session moi van bi cap boi `booking.createdAt + 60 minutes`

Y nghia production:

- `PAY_LATER` thay doi booking lifetime
- `PAY_LATER` giu booking lau hon booking mac dinh, nhung khong giu vo han

### `STRIPE_CARD`

`STRIPE_CARD` la redirect checkout session dai hon QR, nhung bi rang buoc chat hon.

Rule:

1. Chi cho tao khi booking con toi thieu 30 phut
2. Co the mo rong `pnrExpiresAt` theo checkout window Stripe
3. Reuse checkout session cu neu con hop le
4. Booking bi cap theo cap chung `booking.createdAt + 60 minutes`

Y nghia production:

- Stripe can du thoi gian cho redirect, auth, 3DS, va checkout completion
- Booking duoi 30 phut se thay Stripe bi disable

## 4. Quan he giua 3 method

### `PAY_LATER -> PAY_QR`

- `PAY_LATER` co the da keo booking ra toi cap chung 60 phut
- Khi vao `PAY_QR`, booking window dai hon van duoc giu nguyen
- QR van chi co session rieng khoang 5 phut va khong vuot cap chung

### `STRIPE_CARD -> PAY_QR`

- Neu Stripe da keo booking ra dai hon, `PAY_QR` duoc chay ben trong window do
- QR khong shrink booking xuong 5 phut
- QR chi co session rieng cua no va khong vuot cap chung

### `PAY_QR -> PAY_LATER`

- QR cu se bi cancel theo method change logic
- Neu co `PAY_LATER` cu hop le, he thong resume no
- Neu khong co, tao session pay-later moi va nang booking len 60 phut nhung khong vuot cap chung

### `PAY_QR -> PAY_QR`

- Neu QR cu con `> 5 phut`: reuse session cu
- Neu QR cu con `<= 5 phut`: tao / refresh theo rule `+5 phut`, nhung khong vuot cap chung

### `STRIPE_CARD -> method khac -> STRIPE_CARD`

- Stripe cu bi cancel / expire khi chuyen sang method khac
- Khi quay lai Stripe, backend chi tao checkout moi neu con du it nhat 30 phut va chua vuot cap chung

## 5. Rule booking lifetime va payment session lifetime

### Booking lifetime

Booking lifetime do `pnrExpiresAt` quyet dinh.

- `PAY_QR`: co the nang booking len du session QR, nhung khong co semantics giu cho lau
- `PAY_LATER`: nang booking len hold window 60 phut
- `STRIPE_CARD`: co the nang booking theo checkout window Stripe
- Tat ca method deu khong duoc vuot cap chung `booking.createdAt + 60 minutes`

### Payment session lifetime

Payment session lifetime do `sessionExpiresAt` quyet dinh.

- `PAY_QR`: session ngan, 5 phut va co the `+5 phut`
- `PAY_LATER`: session dai hon va duoc reuse / resume
- `STRIPE_CARD`: checkout session rieng

## 6. API/FE can hieu gi

### GET payment methods

Frontend khong nen tu suy dien tat ca method deu co cung mot rule.

- `PAY_QR`: available neu booking con song va chua het cap chung
- `PAY_LATER`: available theo hold rule va cap chung
- `STRIPE_CARD`: available khi con toi thieu 30 phut, dung currency / config, va chua het cap chung

### POST create payment

Response payment phai duoc hieu nhu sau:

- `payment.expireAt`: expire cuoi cung cua session hien tai
- `booking.pnrExpiresAt`: gate cua booking
- `action`: QR hoac redirect URL tuy method

### Result / pending page

Pending page cua QR phai dem nguoc theo `payment.expireAt` / `payment.sessionExpiresAt`, khong tu gan cung la `pnrExpiresAt`.

## 7. Checklist cho QA

### `PAY_QR`

- Booking con 2-3 phut, vao `PAY_QR` -> QR phai hien toi da khoang 5 phut neu chua cham cap chung
- Booking da co hold 60 phut tu `PAY_LATER`, vao `PAY_QR` -> QR van khoang 5 phut, booking khong bi rut xuong
- QR cu con tren 5 phut -> vao lai `PAY_QR` phai reuse
- QR cu con duoi hoac bang 5 phut -> vao lai `PAY_QR` phai duoc refresh them 5 phut neu chua cham cap chung
- Booking gan cap 60 phut -> QR khong duoc extend booking vuot cap

### `PAY_LATER`

- Tao moi -> booking len toi da 60 phut tu `booking.createdAt`
- Chuyen sang method khac roi quay lai -> session cu duoc resume neu hold van con
- Reuse khong duoc reset session vo han neu session cu van hop le
- Tao lai khi session cu khong reuse duoc -> session moi khong duoc vuot cap 60 phut tu `booking.createdAt`

### `STRIPE_CARD`

- Booking duoi 30 phut -> Stripe bi disable
- Booking tren 30 phut -> Stripe tao duoc checkout session neu chua vuot cap chung
- Chuyen sang `PAY_QR` sau Stripe -> booking window dai hon van duoc giu
- Stripe khong duoc vuot cap chung 60 phut tu `booking.createdAt`

## 8. Ket luan

Rule can nho nhat:

1. `pnrExpiresAt` la booking gate chung
2. `sessionExpiresAt` la dong ho cua tung payment session
3. `PAY_QR` co session rieng va khong duoc bi cat xuong boi booking window ngan cu
4. `PAY_LATER` la method giu cho 60 phut nhung khong reset vo han
5. `STRIPE_CARD` chi cho tao khi con toi thieu 30 phut
6. Moi method deu bi cap boi `booking.createdAt + 60 minutes`
