# Chính sách check-in và boarding

Tài liệu này mô tả các điều kiện check-in online và thông tin boarding pass để trả lời các câu hỏi chung của hành khách.

## Điều kiện check-in

Hành khách có thể check-in online khi booking và vé đã đủ điều kiện:

- Booking đã được xác nhận.
- Vé đã được xuất.
- Hành khách thuộc booking và vé còn hợp lệ.
- Hành khách chưa check-in trước đó cho vé/chặng bay đó.
- Hành khách đã có ghế hợp lệ.
- Chuyến bay đang trong cửa sổ check-in online.

Nếu hành khách chưa có ghế, hệ thống có thể yêu cầu chọn ghế trước khi hoàn tất check-in.

Với booking cụ thể, hệ thống phải gọi API check-in để kiểm tra đồng thời trạng thái vé, ghế, chuyến bay và cửa sổ thời gian; không được kết luận chỉ từ chính sách chung.

## Cửa sổ check-in online

### Chuyến bay nội địa

- Mở check-in online: 24 giờ trước giờ khởi hành.
- Đóng check-in online: 40 phút trước giờ khởi hành.

### Chuyến bay quốc tế

- Mở check-in online: 48 giờ trước giờ khởi hành.
- Đóng check-in online: 60 phút trước giờ khởi hành.

## Khi không được check-in

Hành khách không thể check-in online trong các trường hợp sau:

- Booking chưa được xác nhận.
- Vé chưa được xuất.
- Booking hoặc vé đã bị hủy.
- Đã qua cửa sổ check-in.
- Chưa đến giờ mở check-in.
- Hành khách chưa có ghế hợp lệ.
- Hành khách đã check-in hoặc đã có boarding pass cho chặng bay đó.

## Boarding pass

Khi check-in thành công, hệ thống tạo boarding pass cho hành khách trên chặng bay tương ứng. Boarding pass có thể gồm:

- Mã boarding pass.
- QR code.
- Cổng lên máy bay nếu đã có dữ liệu.
- Số thứ tự lên máy bay nếu có.
- Số ghế.
- Thông tin chuyến bay.

Nếu hành khách hỏi boarding pass cụ thể của mình, cần kiểm tra booking hoặc trạng thái check-in thực tế.

## Boarding gate

Quy định vận hành tại gate nằm trong `airport-operations-policy.md`.

File này chỉ mô tả boarding pass được tạo sau check-in thành công. Cổng lên máy bay cụ thể có thể thay đổi theo vận hành sân bay và cần được kiểm tra từ boarding pass, thông báo sân bay hoặc dữ liệu chuyến bay mới nhất nếu hệ thống có hỗ trợ.

## Khi nào cần kiểm tra booking thực tế

Các câu hỏi sau không nên trả lời chỉ bằng chính sách chung vì phụ thuộc dữ liệu booking/chuyến bay thực tế:

- Tôi check-in được chưa?
- Boarding pass của tôi đâu?
- Tôi chưa có ghế thì check-in thế nào?
- Chuyến bay của tôi là nội địa hay quốc tế?

Với các câu hỏi này, cần tra cứu booking, vé, ghế, thời gian chuyến bay và trạng thái check-in hiện tại trước khi trả lời chắc chắn.
