content = """# Use Case

## 1. Authentication & Profile
- **UC-01:** Đăng ký tài khoản
- **UC-02:** Xác thực email
- **UC-03:** Đăng nhập
- **UC-04:** Đăng xuất
- **UC-05:** Cập nhật profile

## 2. Flight Search
- **UC-07:** Tìm kiếm chuyến bay
- **UC-08:** Lọc & sắp xếp chuyến bay
- **UC-09:** Xem chi tiết chuyến bay
- **UC-10:** Tra cứu chuyến bay (không đặt vé)

## 3. Booking
- **UC-11:** Chọn chuyến bay & hạng vé
- **UC-12:** Nhập thông tin hành khách
- **UC-13:** Nhập thông tin liên hệ
- **UC-14:** Tạo Booking (PNR) 🔥
- **UC-15:** Chọn ghế
- **UC-16:** Chọn dịch vụ bổ sung
- **UC-17:** Áp dụng mã giảm giá
- **UC-18:** Xem tổng tiền

## 4. Payment
- **UC-19:** Thanh toán booking 🔥
- **UC-20:** Xử lý thanh toán thành công
- **UC-21:** Xử lý thanh toán thất bại

## 5. Ticket & PNR
- **UC-22:** Nhận vé điện tử
- **UC-23:** Nhận mã PNR
- **UC-24:** Xem chi tiết booking

## 6. Manage Booking
- **UC-25:** Xem booking
- **UC-26:** Đổi ghế
- **UC-27:** Thêm dịch vụ
- **UC-28:** Đổi chuyến (optional)
- **UC-29:** Hủy booking
- **UC-30:** Hoàn tiền

## 7. Check-in & Boarding
- **UC-31:** Check-in online 🔥
- **UC-32:** Xác nhận ghế khi check-in
- **UC-33:** Nhận Boarding Pass
- **UC-34:** Boarding (scan QR)

## 8. History
- **UC-35:** Xem lịch sử đặt vé

## 9. System
- **UC-37:** Auto expire booking 🔥
- **UC-38:** Release ghế hết hạn
- **UC-39:** Gửi email
- **UC-40:** Nhắc check-in
- **UC-41:** Update flight status

# MVP Scope

### 🔥 Core (bắt buộc)
- **UC-07:** Search flight
- **UC-11 → UC-14:** Booking flow
- **UC-15:** Seat selection
- **UC-19:** Payment
- **UC-22:** Ticket
- **UC-24:** View booking
- **UC-31:** Check-in
- **UC-37:** Auto expire

# Sequence Diagram (Core)

## UC-14: Create Booking (PNR)
