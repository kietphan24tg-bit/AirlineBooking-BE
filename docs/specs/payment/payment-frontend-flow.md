# Luồng thanh toán — hướng dẫn Frontend

## 1. Trang thanh toán (checkout)

1. `GET /api/payment-methods?bookingId={id}&onlySupported=true`
   - Kiểm tra booking còn được thanh toán; lấy danh sách phương thức (hiện tại chỉ PayOS được hỗ trợ khi tạo payment).
2. `POST /api/payments` với body `{ "bookingId", "method" }` (ví dụ `"PAYOS_QR"`).
   - Nhận `payment` + `action` (QR / `paymentUrl`).

## 2. Trang kết quả sau redirect PayOS

PayOS redirect về `returnUrl` / `cancelUrl` với query `paymentId` (backend đã append).

1. Đọc `paymentId` từ URL.
2. Gọi **`GET /api/payments/{paymentId}`** (Bearer token của user đăng nhập).
   - Response chuẩn của app-bootstrap bọc trong envelope: `{ "success": true, "data": { ... }, "message": "..." }`. Dùng **`data.status`**, **`data.id`**, v.v.
3. Theo `data.status` trong response:
   - **`PENDING`**: hiển thị “Đang xác nhận thanh toán…” và **polling** (gọi lại API mỗi 1–2 giây, tối đa ~30–60 giây) vì webhook có thể đến sau redirect.
   - **`SUCCESS`**: trang thành công.
   - **`FAILED`** / **`EXPIRED`**: trang lỗi hoặc hết hạn; cho phép quay lại checkout nếu booking vẫn payable.

## 3. Ghi chú

- Không dùng `GET /api/payment-methods` để suy ra “đã thanh toán xong” sau redirect; dùng **`GET /api/payments/{paymentId}`**.
- `paymentUrl` trong response status có thể là `null` (checkout URL không được lưu trong DB); dùng `transactionId` / `qrData` từ lần `POST /api/payments` nếu cần hiển thị lại.
