# Chính Sách Đổi Chuyến, Hủy Vé Và Hoàn Tiền

Tài liệu này mô tả đổi chuyến bay, hủy vé và hoàn tiền.

## Đổi chuyến bay

Người dùng chỉ có thể đổi chuyến khi booking và vé còn đủ điều kiện. Các điều kiện chính gồm:

- Booking đã được xác nhận sau thanh toán.
- Booking chưa bị khóa do check-in.
- Vé cần đổi đã được xuất và vẫn còn hiệu lực.
- Hạng vé của vé đó cho phép đổi chuyến.
- Yêu cầu đổi chuyến được thực hiện trước thời điểm hệ thống đóng quyền đổi chuyến.
- Booking có giao dịch thanh toán hợp lệ để hệ thống đối chiếu giá trị vé ban đầu.

## Giới hạn thời gian đổi chuyến

Hệ thống chỉ cho đổi chuyến trước giờ khởi hành trong một khoảng thời gian đủ an toàn để xử lý lại vé, ghế, dịch vụ và thanh toán phát sinh.

- Hiện tại yêu cầu đổi chuyến online bị khóa trong vòng 180 phút trước giờ khởi hành.
- Báo giá đổi chuyến chỉ có hiệu lực trong 15 phút.
- Nếu báo giá hết hạn, người dùng cần tạo báo giá mới vì giá vé, ghế trống hoặc điều kiện chuyến bay có thể đã thay đổi.
- Với hành trình nối chuyến, chuyến mới vẫn phải đảm bảo thời gian nối chuyến tối thiểu 90 phút.

Các mốc này là giới hạn hệ thống hiện tại. Nếu cấu hình vận hành thay đổi, cần ưu tiên dữ liệu trả về tại thời điểm người dùng thao tác.

## Điều kiện flight mới

Flight mới phải hợp lệ:

- Khác flight hiện tại.
- Cùng sân bay đi và sân bay đến với segment đang đổi.
- Fare mới còn khả dụng.
- Đủ ghế cho các passenger có ghế riêng.
- Nếu là itinerary nối chuyến, phải đảm bảo thời gian nối chuyến tối thiểu 90 phút.

Infant phải đi cùng ít nhất một adult trong thay đổi.

## Kết quả tiền khi đổi chuyến

Sau khi người dùng chọn chuyến mới, hệ thống sẽ báo giá lại để so sánh với vé hiện tại.

Kết quả có thể là:

- Người dùng cần thanh toán thêm nếu chuyến hoặc hạng vé mới có chi phí cao hơn sau khi cộng phí đổi chuyến.
- Người dùng có thể được hoàn chênh lệch nếu chuyến hoặc hạng vé mới có chi phí thấp hơn và chính sách cho phép.
- Không phát sinh thêm tiền nếu tổng tiền sau khi tính lại không thay đổi.

Đổi chuyến cũng có thể ảnh hưởng đến ghế và dịch vụ đã chọn trước đó. Người dùng có thể cần chọn lại ghế hoặc kiểm tra lại dịch vụ nếu các lựa chọn cũ không còn phù hợp với chuyến mới.

## Hủy vé và hoàn tiền

Người dùng có thể yêu cầu hủy vé và hoàn tiền nếu booking còn đủ điều kiện. Các điều kiện chính gồm:

- Booking đã được xác nhận sau thanh toán.
- Booking chưa bị khóa do check-in.
- Vé cần hủy đã được xuất và chưa bị hủy trước đó.
- Vé thuộc điều kiện được hoàn tiền theo chính sách hạng vé.
- Hệ thống còn tìm được giao dịch thanh toán hợp lệ để thực hiện hoàn tiền.

Hệ thống có thể cho hủy một phần vé trong cùng booking. Nếu hủy toàn bộ vé còn hiệu lực, booking được xem là đã hủy. Nếu chỉ hủy một phần, booking vẫn có thể tiếp tục hiệu lực cho các hành khách hoặc chặng còn lại.

Nếu bên thanh toán trả lỗi hoặc hệ thống chưa hoàn tất xử lý refund, không được nói rằng vé hoặc booking đã hủy thành công. Cần trả lời theo trạng thái thực tế từ hệ thống.

## Thành phần tính refund

Số tiền hoàn không chỉ là giá vé ban đầu. Khi preview hủy vé, hệ thống có thể tách số tiền hoàn thành nhiều phần để người dùng hiểu vì sao số tiền cuối cùng thay đổi.

Các thành phần thường gặp gồm:

- Tiền vé còn được hoàn sau khi xét khuyến mãi đã dùng.
- Phần phí ghế có thể hoàn, nếu hành khách đã mua ghế và chính sách cho phép hoàn.
- Phần phí dịch vụ bổ sung có thể hoàn, nếu dịch vụ đó đủ điều kiện hoàn.
- Phí hủy hoặc phí hoàn tiền bị trừ theo điều kiện vé.
- Số tiền hoàn cuối cùng sau khi cộng các khoản được hoàn và trừ các khoản phí.

Không được tự tính số tiền hoàn cho booking cụ thể từ mô tả chính sách. Với câu hỏi như "tôi hủy vé được hoàn bao nhiêu", phải dùng chức năng preview hủy vé rồi diễn giải đúng số tiền hệ thống trả về.

## Ước tính thời gian hoàn tiền theo provider

Ước tính xử lý refund phụ thuộc provider:

- Stripe: thường 5-10 ngày làm việc, thời gian thực tế phụ thuộc ngân hàng phát hành thẻ.
- PayOS: yêu cầu hoàn tiền được ghi nhận và xử lý 24/7, thời gian tiền về thực tế phụ thuộc ngân hàng nhận.
- Provider khác hoặc không xác định: chỉ nói yêu cầu hoàn tiền đã được ghi nhận, thời gian thực tế phụ thuộc provider và ngân hàng xử lý.

## Khi nào phải tra cứu dữ liệu thực tế

Phải tra cứu dữ liệu thực tế nếu khách hỏi:

- Vé của tôi có đổi được không?
- Đổi sang chuyến này mất bao nhiêu?
- Tôi hủy vé được bao nhiêu tiền?
- Booking đã check-in rồi có hủy được không?
- Ticket nào trong booking còn active?
