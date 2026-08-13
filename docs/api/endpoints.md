# API Endpoint Reference

## Response Format

**Success**
```json
{ "success": true, "data": { ... }, "message": "..." }
```

**Error**
```json
{
  "success": false,
  "error": { "code": "ERROR_CODE", "message": "...", "details": {} },
  "statusCode": 404,
  "timestamp": "...",
  "path": "...",
  "requestId": "..."
}
```

---

## 1. Auth — `/api/auth/*`

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/auth/register` | Đăng ký tài khoản | ❌ |
| POST | `/auth/send-verify-otp` | Gửi OTP xác thực email | ❌ |
| POST | `/auth/verify-email` | Xác thực OTP | ❌ |
| POST | `/auth/login` | Đăng nhập → trả `accessToken` | ❌ |
| POST | `/auth/refresh` | Làm mới `accessToken` | ❌ |
| POST | `/auth/logout` | Đăng xuất | ✅ |
| POST | `/auth/forgot-password` | Gửi OTP reset mật khẩu | ❌ |
| POST | `/auth/reset-password` | Đặt lại mật khẩu bằng OTP | ❌ |
| POST | `/auth/change-password` | Đổi mật khẩu | ✅ |

---

## 2. Users — `/api/users/*`

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/users/me` | Lấy thông tin cá nhân | ✅ |
| PATCH | `/users/me` | Cập nhật hồ sơ | ✅ |

---

## 3. Flights — `/api/flights/*`

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/flights/search` | Tìm kiếm chuyến bay | ❌ |
| POST | `/flights/revalidate-fare` | Tính lại giá fare, kiểm tra đủ ghế cho `adult + child`, trả `revalidateId` nhưng chưa giữ ghế | ❌ |
| GET | `/flights/:id/seats?fareId=...` | Lấy sơ đồ ghế theo `flightId` + `fareId`, trả quyền chọn ghế và phụ thu theo fare | ❌ |
| GET | `/services` | Lấy catalog dịch vụ bổ sung cố định (`services` + `service_options`) để render UI | ❌ |

### Search Query (`GET /flights/search`)

| Param | Example | Required | Notes |
|-------|---------|----------|-------|
| `tripType` | `ONE_WAY` / `ROUND_TRIP` | ✅ | `ROUND_TRIP` bắt buộc có `returnDate` |
| `from` | `SGN` | ✅ | Mã sân bay đi |
| `to` | `HAN` | ✅ | Mã sân bay đến, phải khác `from` |
| `departureDate` | `2026-05-01` | ✅ | Ngày đi |
| `returnDate` | `2026-05-10` | ➖ | Chỉ dùng cho `ROUND_TRIP` |
| `adult` | `1` | ✅ | Min `1`, max `9` |
| `child` | `0` | ➖ | Min `0`, max `9`; trẻ 2-11 tuổi, có ghế |
| `infant` | `0` | ➖ | Min `0`, max `9`, phải `<= adult`; trẻ dưới 2 tuổi, không có ghế |
| `promoCode` | `SUMMER20` | ➖ | Validate nhẹ |
| `cursor` | `v1|...` | ➖ | Legacy cursor |
| `outboundCursor` | `v1|...` | ➖ | Cursor riêng chiều đi |
| `inboundCursor` | `v1|...` | ➖ | Cursor riêng chiều về |
| `limit` | `20` | ➖ | Mặc định `20`, min `1`, max `100` |

### Seat Map (`GET /flights/:id/seats?fareId=...`)

- Bắt buộc truyền `fareId`.
- Không cần gửi full object `flight` hay `fare`.
- Backend dùng `fareId` để suy ra `fareClass`, `seatSelectionPolicy`, quyền chọn ghế và phụ thu ghế.
- Các field frontend cần dùng:
  - `seatSelectionPolicy`
  - `includedSeatCategoryCode`
  - `layout.columns`
  - `layout.aisleAfterColumns`
  - `rows[].rowNumber`
  - `rows[].cells[].columnLetter`
  - `rows[].cells[].selectable`
  - `rows[].cells[].included`
  - `rows[].cells[].extraPrice`
  - `rows[].cells[].reason`

### Ancillary Catalog (`GET /services`)

- Phase đầu không cần request body hay query bắt buộc.
- Trả về catalog cố định từ seed data.
- Frontend không nên hardcode tên/gía/icon dịch vụ trong app.
- Mỗi item nên gồm:
  - `serviceId`, `serviceCode`, `serviceName`, `iconUrl`
  - `options[]`
  - `serviceOptionId`, `optionName`, `description`, `price`, `currency`, `imageUrl`

---

## 4. Bookings — `/api/bookings/*`

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/bookings` | Tạo booking từ `revalidateId`, validate tuổi hành khách, reserve ghế cho `adult + child` | ✅ |
| GET | `/bookings/my` | Danh sách booking của tôi | ✅ |
| GET | `/bookings/:id` | Chi tiết booking | ✅ |
| POST | `/bookings/:id/seats` | Lưu ghế đã chọn cho từng passenger | ✅ |
| POST | `/bookings/:id/services` | Lưu dịch vụ bổ sung theo `serviceOptionId` cho từng passenger | ✅ |
| GET | `/bookings/:id/invoice` | Lấy thông tin thanh toán + danh sách phương thức | ✅ |
| POST | `/bookings/:id/apply-promotion` | Áp mã giảm giá | ✅ |
| POST | `/bookings/:id/cancel` | Hủy booking | ✅ |

### Booking Error Codes

| Code | HTTP | Meaning |
|------|------|---------|
| `REVALIDATE_EXPIRED` | 410 | `revalidateId` hết hạn |
| `INVALID_PASSENGER_AGE` | 400 | `passengerType` không khớp `dateOfBirth` tại ngày bay |
| `SEAT_NOT_AVAILABLE` | 409 | Hết chỗ hoặc ghế vừa bị lấy |
| `SEAT_ALREADY_RESERVED` | 409 | Ghế vừa bị người khác đặt |
| `BOOKING_EXPIRED` | 410 | Booking hết hạn trước khi thanh toán |

---

## 5. Payments — `/api/payments/*`

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/payments` | Lấy các phương thức thanh toán | ❌ |
| POST | `/payments` | Khởi tạo thanh toán | ✅ |
| GET | `/payments/:id/status` | Poll trạng thái thanh toán | ✅ |
| POST | `/payments/webhook` | Webhook từ cổng thanh toán | ❌ |

---

## 6. Check-in — `/api/checkin/*`

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/checkin` | Check-in online | ✅ |
| GET | `/checkin/boarding-pass/:bookingId` | Lấy boarding pass | ✅ |

---

## 7. Tickets — `/api/tickets/*`

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/tickets` | Danh sách vé của tôi | ✅ |
| GET | `/tickets/:ticketNumber` | Chi tiết vé | ✅ |
| GET | `/tickets/lookup` | Tra cứu vé công khai bằng PNR + tên | ❌ |

---

## Booking Status

| Status | Meaning |
|--------|---------|
| `PENDING_PAYMENT` | Vừa tạo, chờ thanh toán |
| `CONFIRMED` | Thanh toán thành công |
| `CHECKED_IN` | Đã check-in |
| `EXPIRED` | Hết hạn thanh toán |
| `CANCELLED` | Đã hủy |

---

## Related Docs

- `docs/api/seat-selection-and-ancillaries.md`
- `docs/service-options-wireframe.md`
