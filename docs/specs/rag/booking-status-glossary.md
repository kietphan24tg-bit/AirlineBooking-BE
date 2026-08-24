# Từ Điển Trạng Thái Booking

Tài liệu này chuẩn hóa cách hiểu các trạng thái booking, payment, ticket, seat và settlement.

## Booking status

### `PENDING_PAYMENT`

Booking đã được tạo nhưng chưa thanh toán thành công. Trong trạng thái này booking còn bị ràng buộc bởi thời hạn giữ chỗ.

Booking `PENDING_PAYMENT` có thể:

- Chọn ghế trước nếu booking chưa hết hạn và không có payment đang pending.
- Chọn dịch vụ bổ sung nếu booking chưa hết hạn và không có payment đang pending.
- Tạo payment nếu booking còn trong cửa sổ thanh toán.

Booking `PENDING_PAYMENT` không được check-in, đổi chuyến, hủy/hoàn theo flow sau thanh toán.

### `CONFIRMED`

Booking đã thanh toán hoặc được xác nhận hợp lệ. Đây là trạng thái cần có để thực hiện các flow sau:

- Xuất ticket.
- Check-in nếu ticket đã `ISSUED`.
- Quản lý chuyến đi.
- Đổi chuyến bay nếu thỏa điều kiện fare và thời gian.
- Hủy vé/hoàn tiền nếu ticket và payment thỏa điều kiện.

### `CHECKED_IN_LOCKED`

Booking đã có hành khách hoàn tất check-in hoặc đã bước vào giai đoạn bị khóa theo nghiệp vụ check-in.

Booking `CHECKED_IN_LOCKED` thường không được hủy vé, hoàn tiền hoặc đổi chuyến online. Nếu khách hỏi về trường hợp này, cần nói booking đã bị khóa do check-in và cần kiểm tra chính sách hỗ trợ thủ công nếu có.

### `CANCELLED`

Booking đã bị hủy. Nếu tất cả ticket active trong booking bị hủy, booking chuyển sang `CANCELLED`.

Booking đã `CANCELLED` không được check-in, đổi chuyến, chọn ghế, mua dịch vụ, hoặc tạo payment mới.

Nếu chỉ hủy một hành khách hoặc một phần ticket trong booking nhiều người, booking có thể vẫn tiếp tục hiệu lực thay vì chuyển toàn bộ sang `CANCELLED`. Phải gọi API tra cứu booking cụ thể trước khi xác nhận trạng thái cuối cùng.

### `EXPIRED`

Booking hết hạn giữ chỗ do quá `pnrExpiresAt` mà chưa thanh toán hợp lệ. Ghế và fare hold được giải phóng cho khách khác.

## Ticket status

### `ISSUED`

Ticket đã được xuất cho booking `CONFIRMED`. Ticket `ISSUED` là điều kiện cần cho check-in, đổi chuyến, hủy/hoàn.

Ticket `ISSUED` không có nghĩa là chắc chắn đổi/hủy được. Việc đổi hoặc hủy còn phụ thuộc fare rule, thời gian trước giờ bay, check-in lock và trạng thái payment.

### `CANCELLED`

Ticket đã bị hủy. Ticket này không còn được check-in hoặc đổi chuyến.

## Payment status

### `PENDING`

Payment session đã được tạo nhưng chưa thanh toán thành công. Khi có payment `PENDING`, một số thao tác sửa booking như đổi ghế hoặc dịch vụ có thể bị chặn để tránh lệch tổng tiền.

### `SUCCESS`

Payment thành công. Booking có thể được xác nhận và ticket có thể được xuất.

### `FAILED`

Payment thất bại. User có thể tạo hoặc chọn phương thức thanh toán khác nếu booking còn hạn.

### `CANCELLED`

Payment bị hủy. Payment này không còn dùng để xác nhận booking.

### `EXPIRED`

Payment đã hết hạn. User cần tạo payment mới nếu booking vẫn còn đủ điều kiện thanh toán.

### `SUSPENDED`

Payment/session tạm ngưng, thường gặp khi user đổi method hoặc payment cũ được giữ để resume theo rule riêng.

### `REFUNDED`

Payment đã được hoàn tiền toàn bộ.

### `PARTIALLY_REFUNDED`

Payment đã được hoàn một phần. Trạng thái này có thể xuất hiện khi chỉ hủy một phần ticket trong booking.

## Fare seat-selection mode

### `CHECKIN_ONLY`

Fare chỉ cho hành khách chọn ghế trong luồng check-in, không được chọn ghế trước luồng này.

### `NOT_ALLOWED`

Fare không cho hành khách chọn ghế trước.

## Seat assignment status

### `RESERVED`

Ghế đang được giữ cho passenger trong booking chưa hoàn tất hoặc đang trong flow cần xác nhận.

### `BOOKED`

Ghế đã gán hợp lệ với passenger/ticket sau khi booking được xác nhận.

### `AVAILABLE`

Ghế còn trống và có thể chọn nếu fare policy cho phép.

### `UNAVAILABLE`

Ghế không khả dụng do cấu hình, khóa hệ thống, đã bị giữ, đã bán, hoặc không hợp với fare/passenger.

## Settlement type

Settlement type mô tả kết quả tiền của một thay đổi sau khi booking đã được xác nhận, ví dụ đổi ghế, đổi dịch vụ hoặc đổi chuyến.

- `COLLECT_PAYMENT`: user cần thanh toán thêm.
- `REFUND`: user được hoàn tiền.
- `NO_CHANGE`: không phát sinh chênh lệch tiền.

## Settlement status

Settlement status mô tả tiến trình xử lý khoản chênh lệch sau thay đổi.

### `PAYMENT_REQUIRED`

Thay đổi đã được xác nhận ở mức yêu cầu thanh toán thêm, nhưng user chưa tạo hoặc chưa hoàn tất payment cho khoản chênh lệch.

### `PAYMENT_PENDING`

Payment cho khoản chênh lệch đã được tạo và đang chờ xử lý. Trong trạng thái này, booking thường không nên tạo thêm thay đổi khác làm phát sinh khoản thanh toán mới.

### `REFUND_PENDING`

Thay đổi tạo ra khoản cần hoàn tiền và hệ thống đang xử lý hoàn tiền. Không nên nói refund đã hoàn tất nếu chưa có trạng thái thành công từ hệ thống.

### `COMPLETED`

Settlement đã hoàn tất. Nếu là khoản phải thu thêm, payment đã thành công và thay đổi đã được áp dụng. Nếu là khoản hoàn tiền, refund đã xử lý thành công theo hệ thống.

### `PAYMENT_FAILED`

Payment hoặc refund cho settlement thất bại. User cần xem thông báo lỗi và có thể cần thao tác lại nếu hệ thống cho phép.

### `CANCELLED`

Settlement hoặc payment liên quan đã bị hủy. Payment/link cũ không còn dùng để hoàn tất khoản này.

### `EXPIRED`

Settlement hoặc payment liên quan đã hết hạn. User có thể cần tạo lại quote/preview/thanh toán nếu booking vẫn còn đủ điều kiện.
