# State Machine & Business Rules

> Dùng làm guard condition cho business logic — validate trước mọi state transition.

---

## 1. FLIGHT_FARE

| Status | Mô tả |
|--------|-------|
| `AVAILABLE` | Còn chỗ |
| `SOLD_OUT` | Hết chỗ |

---

## 2. BOOKING

### Status
| State | Mô tả |
|-------|-------|
| `PENDING` | Booking đã tạo (có PNR), chờ thanh toán |
| `CONFIRMED` | Thanh toán thành công |
| `EXPIRED` | Hết thời gian giữ chỗ |
| `CANCELLED` | Người dùng hủy booking |
| `CANCEL_REQUESTED` | Đang yêu cầu hủy, chờ hoàn tiền |
| `COMPLETED` | Chuyến bay đã hoàn tất |

### Transitions
| Action | From | To |
|--------|------|----|
| Create booking | — | `PENDING` |
| Payment success | `PENDING` | `CONFIRMED` |
| Payment timeout | `PENDING` | `EXPIRED` *(cron job)* |
| User cancel | `CONFIRMED` | `CANCEL_REQUESTED` |
| Refund done | `CANCEL_REQUESTED` | `CANCELLED` |
| Cancel booking (no refund) | `CONFIRMED` | `CANCELLED` |
| Flight completed | `CONFIRMED` | `COMPLETED` |

### Rules
- `PENDING` → `EXPIRED` tự động khi quá `pnr_expires_at` *(cron job)*
- `EXPIRED` không thể khôi phục
- `CANCELLED` không thể quay lại `CONFIRMED`
- Booking chỉ được chỉnh sửa khi:
  - `status = PENDING` hoặc `CONFIRMED`
  - **và** chưa check-in
- Sau khi check-in → booking bị khóa hoàn toàn

---

## 3. TICKET

### Status
| State | Mô tả |
|-------|-------|
| `ISSUED` | Vé đã phát hành |
| `CHECKED_IN` | Hành khách đã check-in |
| `BOARDED` | Hành khách đã lên máy bay |
| `USED` | Hoàn tất chuyến bay |
| `NO_SHOW` | Không check-in trong thời hạn |
| `CANCELLED` | Vé bị hủy |

### Transitions
| Action | From | To |
|--------|------|----|
| Issue ticket | — | `ISSUED` |
| Check-in | `ISSUED` | `CHECKED_IN` |
| Boarding (scan QR) | `CHECKED_IN` | `BOARDED` |
| Flight completed | `BOARDED` | `USED` |
| No-show detected | `ISSUED` | `NO_SHOW` *(cron job)* |
| Cancel ticket | `ISSUED` | `CANCELLED` |

### Rules
- Ticket chỉ được tạo khi `booking.status = CONFIRMED`
- `NO_SHOW`: không được hoàn tiền, tự động set khi quá thời gian check-in *(cron job)*
- `BOARDED` chỉ hợp lệ nếu ticket đang ở `CHECKED_IN`

---

## 4. SEAT

### Status
| State | Mô tả |
|-------|-------|
| `AVAILABLE` | Ghế còn trống |
| `RESERVED` | Đang được giữ tạm (trong 15 phút) |
| `BOOKED` | Đã thanh toán |

### Transitions
| Action | From | To |
|--------|------|----|
| Select seat | `AVAILABLE` | `RESERVED` |
| Payment success | `RESERVED` | `BOOKED` |
| Timeout | `RESERVED` | `AVAILABLE` *(cron job)* |
| Cancel booking | `BOOKED` | `AVAILABLE` |

### Rules
- Một ghế tại một thời điểm chỉ thuộc về **một booking duy nhất**
- Khi `RESERVED`: phải gắn `booking_id` + có `pnr_expires_at`
- Quá `pnr_expires_at` → tự động về `AVAILABLE` *(cron job)*
- **Không cho phép:**
  - `RESERVED` → `RESERVED` (booking khác)
  - `BOOKED` → `RESERVED`

---

## 5. PAYMENT

### Status
| State | Mô tả |
|-------|-------|
| `PENDING` | Đang chờ thanh toán |
| `SUCCESS` | Thanh toán thành công |
| `FAILED` | Thanh toán thất bại |
| `REFUNDED` | Đã hoàn tiền toàn bộ |
| `PARTIALLY_REFUNDED` | Hoàn tiền từng passenger |

### Transitions
| Action | From | To |
|--------|------|----|
| Create payment | — | `PENDING` |
| Payment success | `PENDING` | `SUCCESS` |
| Payment failed | `PENDING` | `FAILED` |
| Refund full | `SUCCESS` | `REFUNDED` |
| Refund partial | `SUCCESS` | `PARTIALLY_REFUNDED` |

---

## 6. FLIGHT

### Status
| State | Mô tả |
|-------|-------|
| `SCHEDULED` | Chuyến bay dự kiến |
| `DELAYED` | Bị trễ |
| `CANCELLED` | Bị hủy |
| `DEPARTED` | Đã cất cánh |
| `LANDED` | Đã hạ cánh |

### Transitions
| Action | From | To |
|--------|------|----|
| Delay | `SCHEDULED` | `DELAYED` |
| Cancel | `SCHEDULED` | `CANCELLED` |
| Depart | `SCHEDULED` / `DELAYED` | `DEPARTED` |
| Land | `DEPARTED` | `LANDED` |

---

## 7. REFUND

### Status
| State | Mô tả |
|-------|-------|
| `PENDING` | Vừa tạo request |
| `PROCESSING` | Đang xử lý (gateway / admin) |
| `DONE` | Hoàn tiền thành công |
| `FAILED` | Hoàn tiền thất bại |

### Transitions
| Action | From | To |
|--------|------|----|
| Create refund | — | `PENDING` |
| Start processing | `PENDING` | `PROCESSING` |
| Refund success | `PROCESSING` | `DONE` |
| Refund failed | `PROCESSING` | `FAILED` |

### Rules
- Refund chỉ được tạo khi:
  - `booking.status = CONFIRMED` **hoặc** `ticket.status = ISSUED`
- `refund_amount <= payment.amount`
- Khi `DONE` → cập nhật đồng thời: `payment` + `ticket` + `seat`

---

## 8. Tổng quan Cron Jobs

| Job | Trigger | Action |
|-----|---------|--------|
| Expire booking | `pnr_expires_at` quá hạn | `PENDING` → `EXPIRED` |
| Release seat | `pnr_expires_at` quá hạn | `RESERVED` → `AVAILABLE` |
| No-show detection | Sau thời gian đóng check-in | `ISSUED` → `NO_SHOW` |
| Complete flight | Sau `LANDED` | `CONFIRMED` → `COMPLETED`, `BOARDED` → `USED` |
