# Chính Sách Đặt Chỗ Và Thanh Toán

Tài liệu này mô tả chính sách đặt chỗ và thanh toán theo cách người dùng cuối có thể hiểu. Các thuật ngữ trạng thái được để ở cuối tài liệu để hệ thống đối chiếu khi cần.

## Giữ chỗ sau khi đặt vé

Sau khi tạo booking nhưng chưa thanh toán, hệ thống chỉ giữ chỗ trong thời gian giới hạn.

- Booking mới được giữ chỗ mặc định khoảng 15 phút.
- Mốc mặc định này được lưu tại `pnrExpiresAt`. Khi người dùng chỉ hỏi booking chưa thanh toán được giữ bao lâu, trả lời trực tiếp là mặc định 15 phút; chỉ giải thích mốc tối đa 60 phút nếu họ hỏi về gia hạn hoặc đổi phương thức thanh toán.
- Trong thời gian còn hạn giữ chỗ, người dùng có thể tiếp tục thanh toán nếu phương thức thanh toán còn khả dụng.
- Nếu booking hết hạn, người dùng có thể không thanh toán tiếp được và cần tạo booking mới.
- Ghế, giá vé và các quyền lợi đang được giữ có thể được giải phóng sau khi booking hết hạn.

Thời gian giữ chỗ không phải là cam kết giữ ghế vô hạn. Người dùng nên hoàn tất thanh toán trước khi đồng hồ giữ chỗ kết thúc.

## Giới hạn giữ chỗ tối đa

Một booking không được giữ quá lâu bằng cách đổi qua lại nhiều phương thức thanh toán.

- Tổng thời gian giữ booking tối đa hiện tại là khoảng 60 phút từ lúc booking được tạo.
- PayOS QR, Pay Later và Stripe card đều phải nằm trong giới hạn này.
- Nếu người dùng đổi phương thức nhiều lần, hệ thống có thể tạo hoặc dùng lại phiên thanh toán, nhưng booking vẫn không được vượt quá giới hạn giữ chỗ tối đa.
- Khi đã quá giới hạn tối đa, hệ thống sẽ không cho tạo thêm phiên thanh toán mới cho booking đó.

Ví dụ: booking được tạo lúc 10:00 thì thời gian giữ chỗ tối đa là 11:00. Nếu người dùng chọn Pay Later lúc 10:50, booking vẫn chỉ được giữ tối đa đến 11:00, không phải 11:50.

## PayOS QR

PayOS QR là phương thức thanh toán bằng mã QR ngân hàng.

- Người dùng quét mã QR và thanh toán theo thông tin hiển thị.
- Mã QR có thời hạn riêng, thường ngắn hơn hạn giữ booking.
- Một phiên QR thường có thời lượng ngắn, khoảng 5 phút.
- Nếu QR hết hạn nhưng booking vẫn còn hạn, người dùng có thể tạo hoặc làm mới QR.
- Nếu người dùng đã chuyển sang phương thức thanh toán khác, QR cũ có thể không còn hợp lệ.
- Người dùng nên thanh toán bằng QR mới nhất đang hiển thị trên hệ thống.

Nếu QR hết hạn, điều đó không luôn có nghĩa booking đã hết hạn. Có thể chỉ là phiên QR hiện tại đã hết hạn và người dùng cần tạo QR mới nếu booking vẫn còn được thanh toán.

## Pay Later

Pay Later là phương thức giữ booking lâu hơn để người dùng quay lại thanh toán sau.

- Pay Later có thể kéo dài thời gian giữ booking so với mức mặc định 15 phút.
- Thời gian giữ theo Pay Later vẫn bị giới hạn bởi mốc giữ booking tối đa.
- Pay Later không phải là giữ chỗ vô hạn.
- Nếu người dùng chuyển sang phương thức khác rồi quay lại Pay Later, hệ thống có thể dùng lại phiên Pay Later cũ nếu phiên đó còn hợp lệ.
- Việc quay lại Pay Later nhiều lần không làm thời gian giữ chỗ được reset vô hạn.

Pay Later phù hợp khi người dùng chưa muốn thanh toán ngay nhưng vẫn muốn giữ booking trong giới hạn thời gian cho phép.

## Stripe Card

Stripe card là phương thức thanh toán bằng thẻ qua trang checkout.

- Người dùng có thể được chuyển sang trang checkout để nhập thông tin thẻ.
- Thanh toán thẻ có thể cần thêm thời gian để xác thực, ví dụ xác thực 3DS hoặc chuyển hướng sau thanh toán.
- Stripe chỉ khả dụng khi booking còn đủ thời gian để hoàn tất checkout.
- Hiện tại Stripe cần booking còn khoảng 30 phút khả dụng để tạo phiên thanh toán.
- Nếu booking còn quá ít thời gian, phương thức Stripe có thể không hiển thị hoặc không tạo được phiên thanh toán.
- Stripe cũng không được vượt quá giới hạn giữ booking tối đa.

Nếu người dùng không thấy phương thức Stripe, nguyên nhân có thể là booking không còn đủ thời gian, tiền tệ không hỗ trợ, hoặc cấu hình thanh toán chưa khả dụng.

## Đổi phương thức thanh toán

Người dùng có thể đổi phương thức thanh toán nếu booking còn hợp lệ và phương thức mới còn khả dụng.

- Khi đổi sang phương thức mới, phiên thanh toán cũ có thể bị hủy hoặc tạm dừng.
- QR hoặc link checkout cũ có thể không còn dùng được sau khi đổi phương thức.
- Người dùng nên dùng đúng QR hoặc link checkout mới nhất đang hiển thị.
- Việc đổi phương thức không làm booking được giữ chỗ mãi mãi.
- Nếu phương thức cũ còn phiên hợp lệ, hệ thống có thể dùng lại phiên đó khi người dùng quay lại.
- Nếu phiên cũ không còn hợp lệ, hệ thống có thể tạo phiên mới nếu booking vẫn còn trong giới hạn thời gian cho phép.

Quy tắc dễ hiểu: đổi phương thức là đổi cách thanh toán, không phải cách kéo dài thời gian giữ chỗ vô hạn.

## Khi đang có thanh toán chờ xử lý

Khi booking đang có một payment chờ xử lý, hệ thống có thể hạn chế các thao tác làm thay đổi tổng tiền.

Muốn xác nhận một booking đang ở trạng thái `PENDING` có đổi được dịch vụ hay không, phải gọi API tra cứu booking/payment. Thông thường khách cần hoàn tất, hủy hoặc chờ payment hiện tại hết hạn trước khi thử thay đổi.

Các thao tác thường không được thực hiện trong lúc payment đang chờ xử lý gồm:

- Đổi hoặc chọn lại ghế.
- Thêm, xóa hoặc thay đổi dịch vụ bổ sung.
- Đổi chuyến bay.
- Tạo một thay đổi khác làm phát sinh khoản thanh toán mới.

Lý do là các thao tác này có thể làm thay đổi tổng tiền, trong khi người dùng đã có một phiên thanh toán đang mở. Nếu vừa thay đổi booking vừa giữ payment cũ, số tiền trên QR hoặc link checkout có thể không còn khớp với booking mới.

Trong trường hợp này, người dùng thường cần:

- Hoàn tất payment hiện tại nếu vẫn muốn giữ lựa chọn đang có.
- Hủy payment hiện tại nếu muốn quay lại chỉnh ghế, dịch vụ hoặc phương án chuyến bay.
- Đợi phiên payment hiện tại hết hạn rồi tạo lại payment mới nếu booking vẫn còn đủ điều kiện.

Các thao tác chỉ xem thông tin, xem trạng thái booking, xem trạng thái payment hoặc kiểm tra thời gian còn lại thường không làm đổi tổng tiền và có thể tiếp tục được hỗ trợ.

Nếu người dùng chỉ đổi cách thanh toán cho cùng một khoản đang chờ xử lý, hệ thống có thể hủy phiên thanh toán cũ và tạo phiên mới cho cùng khoản đó. Đây không được xem là thay đổi nội dung booking.

## Kết quả thanh toán

### Thanh toán thành công

Khi thanh toán thành công:

- Booking có thể chuyển sang trạng thái đã xác nhận.
- Vé có thể được phát hành.
- Người dùng có thể xem vé, quản lý booking, check-in hoặc thực hiện thay đổi nếu booking còn đủ điều kiện.

### Thanh toán thất bại

Khi thanh toán thất bại:

- Booking chưa được xác nhận thanh toán.
- Người dùng có thể thử lại hoặc chọn phương thức khác nếu booking vẫn còn hạn.
- Nếu booking đã hết hạn, người dùng cần tạo booking mới.

### Thanh toán bị hủy

Khi thanh toán bị hủy:

- Phiên thanh toán hiện tại không còn được dùng để hoàn tất giao dịch.
- Người dùng có thể tạo phiên thanh toán mới nếu booking vẫn còn hạn và hệ thống còn cho phép.

### Thanh toán bị tạm dừng

Một số phiên thanh toán có thể bị tạm dừng khi người dùng chuyển phương thức. Việc có thể tiếp tục phiên đó hay không phụ thuộc vào trạng thái phiên thanh toán và hạn còn lại tại thời điểm kiểm tra.

## Thanh toán cho thay đổi sau khi đặt vé

Sau khi booking đã được xác nhận, một số thay đổi có thể làm phát sinh tiền cần thanh toán thêm hoặc tiền cần hoàn lại.

Các trường hợp thường gặp:

- Đổi sang ghế có phí cao hơn.
- Thêm dịch vụ bổ sung sau khi đã đặt vé.
- Đổi chuyến bay có chênh lệch giá hoặc phí.

Không phải mọi thay đổi đều cần trả thêm tiền. Kết quả chính xác phụ thuộc vào chính sách giá vé, trạng thái vé, dịch vụ đã chọn và kết quả tính toán của hệ thống.

## Hoàn tiền

Hoàn tiền chỉ áp dụng khi vé và booking còn đủ điều kiện hủy theo hệ thống. Không phải mọi vé đã mua đều mặc định được hoàn tiền.

Người dùng có thể được hoàn tiền khi:

- Booking đã được thanh toán và xác nhận.
- Vé chưa bị khóa do đã check-in hoặc do trạng thái không còn cho phép hủy online.
- Vé thuộc loại hoặc điều kiện cho phép hoàn tiền.
- Hệ thống còn tìm được giao dịch thanh toán hợp lệ để hoàn tiền về đúng kênh thanh toán.

Số tiền hoàn có thể bao gồm nhiều phần:

- Phần tiền vé còn được hoàn.
- Phần phí ghế được hoàn nếu ghế đã mua đủ điều kiện hoàn.
- Phần dịch vụ bổ sung được hoàn nếu dịch vụ đó đủ điều kiện hoàn.
- Phí hủy hoặc phí hoàn tiền bị trừ, nếu chính sách vé có áp dụng.

Vì số tiền hoàn phụ thuộc vào từng ticket, từng dịch vụ, từng ghế và giao dịch thanh toán trước đó, hệ thống không nên tự ước lượng số tiền hoàn cho một booking cụ thể. Khi người dùng hỏi "tôi hủy vé được hoàn bao nhiêu", cần kiểm tra booking bằng chức năng preview hủy vé rồi mới trả lời theo kết quả hệ thống.

Khi hủy vé thành công:

- Vé đã hủy không còn dùng để bay hoặc check-in.
- Ghế và dịch vụ gắn với hành khách bị hủy có thể được giải phóng hoặc gỡ khỏi booking.
- Nếu hủy toàn bộ vé còn hiệu lực trong booking, booking được xem là đã hủy.
- Nếu chỉ hủy một phần vé, booking vẫn có thể còn hiệu lực cho các hành khách hoặc chặng còn lại.

Thời gian tiền về phụ thuộc vào kênh thanh toán:

- Với thanh toán thẻ qua Stripe, tiền thường về trong khoảng 5 đến 10 ngày làm việc; thời gian thực tế phụ thuộc ngân hàng phát hành thẻ.
- Với PayOS, yêu cầu hoàn tiền được ghi nhận và xử lý 24/7; thời gian tiền về thực tế phụ thuộc ngân hàng nhận.
- Nếu hệ thống không xác định rõ provider, chỉ nên nói yêu cầu hoàn tiền đã được ghi nhận và thời gian thực tế phụ thuộc bên thanh toán/ngân hàng.

Nếu yêu cầu hoàn tiền thất bại, cần thông báo theo trạng thái lỗi từ hệ thống và không nói rằng vé hoặc booking đã hủy thành công.

## Thông tin cần kiểm tra theo từng booking

Các thông tin sau thay đổi theo từng booking và cần lấy từ hệ thống tại thời điểm người dùng hỏi:

- Booking còn hạn giữ chỗ hay đã hết hạn.
- Booking đã thanh toán thành công hay chưa.
- Payment hiện tại đang chờ xử lý, thành công, thất bại, bị hủy hay đã hoàn tiền.
- QR còn bao lâu.
- Có thể đổi sang PayOS QR, Pay Later hoặc Stripe card hay không.
- Link checkout hoặc QR mới nhất là gì.
- Tổng tiền cần thanh toán.
- Số tiền chênh lệch khi đổi ghế, thêm dịch vụ hoặc đổi chuyến.
- Số tiền hoàn dự kiến khi hủy vé.

## Đối chiếu kỹ thuật

Phần này chỉ dùng để đối chiếu trạng thái nghiệp vụ, không phải nội dung cần nói trực tiếp với người dùng nếu không cần thiết.

- `pnrExpiresAt`: hạn giữ booking.
- `sessionExpiresAt`: hạn của phiên thanh toán cụ thể.
- `payment.expireAt`: thời hạn payment dùng để hiển thị countdown.
- `booking.createdAt + 60 phút`: mốc giữ booking tối đa cho payment flow.
- `PAY_QR` hoặc `PAYOS`: thanh toán QR qua PayOS.
- `PAY_LATER`: phương thức giữ booking lâu hơn để thanh toán sau.
- `STRIPE_CARD`: thanh toán thẻ qua Stripe checkout.
- `PENDING`: payment đang chờ xử lý.
- `SUCCESS`: payment thành công.
- `FAILED`: payment thất bại.
- `CANCELLED`: payment đã bị hủy.
- `SUSPENDED`: payment bị tạm dừng.
- `REFUNDED`: payment đã hoàn tiền toàn bộ.
- `PARTIALLY_REFUNDED`: payment đã hoàn tiền một phần.
