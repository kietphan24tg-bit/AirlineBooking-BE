# Business Logic & Flow Rules

> Chi tiết guard condition và luồng xử lý cho từng nghiệp vụ.

---

## 1. BOOKING

### Tạo Booking
- Trigger: sau khi user nhập xong thông tin passenger → nhấn "Tiến hành thanh toán"
- `POST /api/flights/revalidate-fare` tính lại giá, kiểm tra đủ ghế cho `adult + child`, trả `revalidateId`, chưa reserve ghế
- `POST /api/bookings` validate tuổi passenger theo `dateOfBirth` và `flightDate`, tạo booking với `status = PENDING` + sinh `pnr_expires_at` (current_time + 15 phút)
- Ghế được chuyển sang `RESERVED` ở bước tạo booking cho `adult + child`; `infant` không giữ ghế

### Guard — Cho phép thanh toán khi
```
backend:  current_time < pnr_expires_at  AND  booking.status = PENDING
frontend: if time_left < 5 phút → disable nút "Thanh toán" hoặc show warning
```
> Backend là source of truth. FE chỉ là UX control.

### Hết hạn
- Cron job kiểm tra `pnr_expires_at`:
  - `booking.status`: `PENDING` → `EXPIRED`
  - `seat.status`: `RESERVED` → `AVAILABLE`
- Booking `EXPIRED` không thể khôi phục

---

## 2. SEAT

| Hành động | Điều kiện | Kết quả |
|-----------|-----------|---------|
| Chọn ghế (sau booking) | `seat.status = AVAILABLE` | `RESERVED` |
| Thanh toán thành công | `booking.status = CONFIRMED` | `BOOKED` |
| Hết `pnr_expires_at` | *(cron job)* | `AVAILABLE` |
| Hủy booking | booking bị CANCEL | `AVAILABLE` |

### Rules
- Chỉ được `RESERVED` khi ghế đang `AVAILABLE`
- Ghế `RESERVED` phải gắn `booking_id` + `pnr_expires_at`
- Không cho phép: `RESERVED → RESERVED` (booking khác), `BOOKED → RESERVED`
- Số ghế cần check/giữ = `adult + child`; `infant` là lap infant nên không chiếm ghế

## 2.1 PASSENGER TYPE & PRICING

- `ADULT`: từ 12 tuổi trở lên, tính 100% giá người lớn, có ghế
- `CHILD`: từ 2 đến dưới 12 tuổi, tính 75% giá người lớn, có ghế
- `INFANT`: dưới 2 tuổi, tính 10% giá người lớn, không có ghế
- `INFANT` phải `<= ADULT`
- Validate tuổi thật thực hiện khi tạo booking dựa trên `dateOfBirth` và `flightDate`

---

## 3. CANCEL FLIGHT

### Điều kiện cho phép hủy
- Booking phải ở trạng thái `CONFIRMED`
- **Không cho hủy khi:**
  - `ticket.status = CHECKED_IN`
  - `ticket.status = NO_SHOW`
  - `booking.status = COMPLETED`

### Flow hủy booking
```
User yêu cầu hủy (dùng PNR)
  → kiểm tra guard conditions
  → booking: CONFIRMED → CANCEL_REQUESTED
  → tạo Refund request (nếu fare_class.is_refundable = true)
  → Refund DONE:
      booking:  CANCEL_REQUESTED → CANCELLED
      seat:     BOOKED           → AVAILABLE
      payment:  SUCCESS          → REFUNDED
      ticket:   ISSUED           → CANCELLED
  → Gửi email thông báo cho user
```

### State changes khi hủy thành công
| Entity | From | To |
|--------|------|----|
| Booking | `CONFIRMED` | `CANCEL_REQUESTED` → `CANCELLED` |
| Seat | `BOOKED` | `AVAILABLE` |
| Payment | `SUCCESS` | `REFUNDED` |
| Ticket | `ISSUED` | `CANCELLED` |

---

## 4. REFUND

### Điều kiện tạo Refund
- `booking.status = CANCEL_REQUESTED`
- `fare_class.is_refundable = true` (nếu `false` → từ chối hoàn tiền)
- **Reject khi:**
  - `ticket.status = CHECKED_IN`
  - `ticket.status = NO_SHOW`

### Tính số tiền hoàn
```
refund_amount = sum(ticket_price + services_amount) - cancellation_fee
```
> `cancellation_fee` phụ thuộc vào `fare_class`

### Flow
```
CONFIRMED → CANCEL_REQUESTED
  → check fare_class.is_refundable
      false → reject, không tạo Refund
      true  → tạo Refund (PENDING)
                → PROCESSING
                → DONE:
                    ticket  → CANCELLED
                    seat    → AVAILABLE
                    payment → REFUNDED
                    booking → CANCELLED
                → FAILED → giữ CANCEL_REQUESTED, retry hoặc xử lý thủ công
```

---

## 5. PAYMENT — Sau Refund DONE

| Entity | From | To |
|--------|------|-----|
| `ticket.status` | `ISSUED` | `CANCELLED` |
| `seat.status` | `BOOKED` | `AVAILABLE` |
| `payment.status` | `SUCCESS` | `REFUNDED` |
| `booking.status` | `CANCEL_REQUESTED` | `CANCELLED` |

> Tất cả cập nhật trên phải trong **một transaction duy nhất** để đảm bảo tính nhất quán.

---

## 6. Notification Triggers

| Sự kiện | Kênh | Nội dung |
|---------|------|---------|
| Booking `CONFIRMED` | Email | Xác nhận đặt vé + mã PNR |
| Booking `EXPIRED` | Email | Thông báo hết hạn giữ chỗ |
| Refund `DONE` | Email | Xác nhận hoàn tiền + số tiền |
| Booking `CANCELLED` | Email | Xác nhận hủy chuyến |
| Flight `DELAYED` / `CANCELLED` | Email | Thông báo thay đổi chuyến bay |
| Check-in mở (T-24h) | Email | Nhắc check-in online |
