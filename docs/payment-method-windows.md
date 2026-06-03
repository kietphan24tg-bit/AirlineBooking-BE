# Payment Windows va Session Rules

Tai lieu nay tom tat rule hien tai cua cac payment method trong backend, tap trung vao:

- `pnrExpiresAt`
- `sessionExpiresAt`
- `PAY_QR` (`PAYOS`)
- `PAY_LATER`
- `STRIPE_CARD`

File code chinh:

- `module-payment/src/main/java/com/example/modulepayment/application/PaymentService.java`

## 1. Hai dong ho can tach ro

### `pnrExpiresAt`

`pnrExpiresAt` la booking gate chung.

No tra loi cau hoi:

- Booking nay con duoc phep thanh toan khong?
- Co duoc tao / reuse payment method nua khong?

Neu `pnrExpiresAt <= now` thi booking duoc xem la het han cho thanh toan.

### `sessionExpiresAt`

`sessionExpiresAt` la dong ho cua tung payment session cu the.

No tra loi cau hoi:

- QR nay con bao lau?
- Checkout session nay con bao lau?
- Pay-later session nay con bao lau?

Frontend countdown cua tung payment phai uu tien doc tu `payment.sessionExpiresAt`.

## 2. Rule tong quat theo method

### `PAY_QR` / `PAYOS`

`PAY_QR` la short-lived payment session.

Rule:

1. Khong tu mo rong `pnrExpiresAt`
2. Co `sessionExpiresAt` rieng cho QR
3. Expire tra ve cho FE la:

```text
min(qrSessionExpireAt, booking.pnrExpiresAt)
```

Y nghia:

- QR chi song ben trong booking window hien co
- QR khong duoc phep lam booking song dai hon
- Neu method truoc da keo `pnr` ra xa hon thi QR co the hoat dong ben trong window do

#### Tao / reuse `PAY_QR`

Neu booking con song:

1. Khong co QR cu:
   - `qrSessionExpireAt = now + 5 minutes`
2. Co QR cu va con `> 5 minutes`:
   - giu nguyen `sessionExpiresAt`
3. Co QR cu va con `<= 5 minutes`:
   - `sessionExpiresAt = previousSessionExpiresAt + 5 minutes`

Sau do expire cuoi cung tra ve cho payment la:

```text
expireAt = min(sessionExpiresAt, pnrExpiresAt)
```

Luu y:

- Hien tai da bo cap rieng cho QR
- Viec chong abuse duoc dua vao cooldown va rate limit create payment

### `PAY_LATER`

`PAY_LATER` la method duoc phep xin them thoi gian cho booking.

Rule:

1. Co the mo rong `pnrExpiresAt` len toi cua so hold 1 gio
2. Reuse payment/session cu neu con hop le
3. Khong reset vo han moi lan user quay lai

Y nghia:

- `PAY_LATER` thay doi booking lifetime
- Khi chuyen tu method khac sang `PAY_LATER`, neu session pay-later cu con hop le thi he thong uu tien resume session cu

### `STRIPE_CARD`

`STRIPE_CARD` la checkout session dai hon QR, nhung bi rang buoc chat hon.

Rule:

1. Chi cho tao neu booking con it nhat `30 minutes`
2. Co the mo rong `pnrExpiresAt` theo rule Stripe hien tai
3. Reuse checkout session cu neu con hop le

Y nghia:

- Stripe can du thoi gian cho redirect / auth / checkout completion
- Booking duoi 30 phut se thay Stripe bi disable

## 3. Logic production hien tai nen hieu nhu sau

### Booking lifetime

Booking lifetime duoc quyet dinh boi `pnrExpiresAt`.

Method co the tac dong nhu sau:

- `PAY_QR`: khong mo rong booking lifetime
- `PAY_LATER`: co mo rong booking lifetime
- `STRIPE_CARD`: co the mo rong booking lifetime trong gioi han cua no

### Payment session lifetime

Payment session lifetime duoc quyet dinh boi `sessionExpiresAt`.

Method co the tac dong nhu sau:

- `PAY_QR`: session ngan, 5 phut va co the +5 phut khi gan het han
- `PAY_LATER`: session dai hon va duoc reuse
- `STRIPE_CARD`: checkout session rieng

## 4. Cac tinh huong can nho

### Case A: Chi dung `PAY_QR`

- Booking khong duoc keo dai them
- QR chi song trong booking window hien co

### Case B: Truoc do da vao `PAY_LATER`

- `pnrExpiresAt` co the da duoc mo rong den 1 gio
- Sau do vao `PAY_QR` van hop le neu booking con song
- Nhung QR van la QR session ngan, khong phai session 1 gio

### Case C: Truoc do da vao `STRIPE_CARD`

- Neu Stripe da mo rong `pnr`, QR co the dung trong window do
- Nhung expire tra ve cua QR van bi chan boi `min(sessionExpiresAt, pnrExpiresAt)`

### Case D: QR cu con <= 5 phut nhung `pnr` ngan hon

Vi du:

- QR cu du kien +5 phut nua
- Nhung booking chi con 3 phut

Khi do:

- session QR co the duoc tinh tang theo rule noi bo
- nhung expire tra ve cho FE van chi toi muc `pnrExpiresAt`
- He thong co the reuse QR cu thay vi tao payment moi, neu expire cuoi cung khong thay doi

## 5. Tai sao rule nay hop ly cho production

Rule nay giu duoc 4 dieu:

1. De giai thich cho user va QA
2. Khong de `PAY_QR` vo tinh bien thanh cach xin them gio
3. Van cho `PAY_LATER` va `STRIPE` giu dung vai tro business cua chung
4. Tach ro booking validity va payment-session validity

## 6. Checklist cho frontend

Frontend nen hieu:

1. Countdown cua QR dua theo `payment.expireAt` / `payment.sessionExpiresAt` tra ve tu backend
2. Khong tu suy dien QR luon la 5 phut tuyet doi
3. Neu method bi disable thi ly do co the den tu booking gate (`pnr`) hoac method rule rieng

## 7. Checklist cho backend

Khi sua logic payment sau nay, can giu:

1. `pnrExpiresAt` la booking gate chung
2. `PAY_QR` khong tu mo rong `pnr`
3. `PAY_QR` expire tra ve phai bi chan boi `pnr`
4. `PAY_LATER` va `STRIPE` chi mo rong booking theo rule rieng cua chung
5. Reuse payment phai xet theo method cung loai, khong lay session cua method khac
