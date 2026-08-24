# Chính sách chọn ghế

Tài liệu này dùng để giải thích cho khách hàng về việc chọn ghế trước chuyến bay, đổi ghế và phí chọn ghế. Tình trạng ghế trống, ghế đã chọn và phí chính xác của một booking cụ thể phải được tra cứu từ hệ thống tại thời điểm khách hỏi.

## Khi nào có thể chọn ghế

Khách có thể chọn ghế khi booking còn hợp lệ để chỉnh sửa:

- Booking đang chờ thanh toán và vẫn còn trong thời hạn giữ chỗ.
- Booking đã được xác nhận.

Nếu booking đã bắt đầu thanh toán nhưng chưa hoàn tất, hệ thống có thể tạm chặn đổi ghế để tránh lệch tổng tiền. Khách nên hoàn tất hoặc hủy luồng thanh toán hiện tại trước khi chỉnh ghế.

Nếu booking đã hết thời hạn giữ chỗ, đã hủy, đã hết hạn hoặc không còn hợp lệ, khách không thể chọn hoặc đổi ghế cho booking đó.

## Ai được chọn ghế

Người lớn và trẻ em có ghế riêng nên có thể chọn ghế theo điều kiện của hạng vé.

Em bé đi kèm người lớn không có ghế riêng, nên không được chọn ghế riêng trong hệ thống.

Mỗi hành khách có ghế riêng chỉ được giữ một ghế trên mỗi chặng bay. Một ghế cũng chỉ được gán cho một hành khách tại cùng thời điểm.

## Quyền chọn ghế theo hạng vé

Quyền chọn ghế phụ thuộc vào hạng vé khách đã mua:

| Hạng vé | Chính sách chọn ghế |
| --- | --- |
| Economy | Được chọn ghế trước, có thể phát sinh phí tùy loại ghế. |
| Premium | Được chọn trước; ghế tiêu chuẩn được bao gồm trong hạng vé. Ghế cao hơn có thể phát sinh phí. |
| Business | Được chọn trước; các nhóm ghế đến Extra Legroom được bao gồm trong hạng vé nếu còn chỗ. |
| First | Được chọn trước; các nhóm ghế đến Extra Legroom được bao gồm trong hạng vé nếu còn chỗ. |

Một số hạng vé hoặc cấu hình đặc biệt có thể không cho chọn ghế trước, hoặc chỉ cho chọn ghế khi làm check-in. Khi khách hỏi về booking cụ thể, cần kiểm tra quyền chọn ghế thực tế của booking đó.

Alias quyền chọn ghế của fare:

| Alias | Ý nghĩa |
| --- | --- |
| `CHECKIN_ONLY` | Chỉ được chọn ghế trong luồng check-in, không được chọn ghế trước luồng này. |
| `NOT_ALLOWED` | Không được chọn ghế trước. |

## Điều kiện của ghế được chọn

Một ghế chỉ có thể chọn nếu:

- Ghế thuộc đúng chuyến bay trong booking.
- Ghế phù hợp với hạng vé của khách.
- Ghế còn trống tại thời điểm chọn.
- Ghế chưa bị giữ hoặc gán cho hành khách khác.
- Mỗi thao tác chọn nhiều ghế chỉ áp dụng cho cùng một chuyến bay.

Nếu khách hỏi "ghế nào còn trống", "tôi có thể chọn ghế 12A không", hoặc "đổi sang ghế cửa sổ được không", phải tra cứu sơ đồ ghế/chặng bay thực tế trước khi trả lời chắc chắn.

## Phí chọn ghế

Phí chọn ghế phụ thuộc vào nhóm ghế, loại ghế và quyền lợi đi kèm hạng vé.

Các mức phí tham chiếu dưới đây đang áp dụng theo VND. Với booking dùng loại tiền tệ khác, không được tự quy đổi phí ghế sang ngoại tệ; cần tra cứu hệ thống để biết booking đó có hỗ trợ phụ phí ghế hay không.

Alias nghiệp vụ cho nhóm ghế:

| Alias | Tên hiển thị | Phí tham chiếu |
| --- | --- | ---: |
| STANDARD | Tiêu chuẩn | 0 VND |
| PREFERRED | Ưu tiên | 40,000 VND |
| EXIT_ROW | Hàng thoát hiểm | 120,000 VND |
| EXTRA_LEGROOM | Rộng chân | 180,000 VND |

Nhóm ghế tham chiếu theo cấu hình hiện tại:

| Nhóm ghế | Phí tham chiếu |
| --- | ---: |
| Tiêu chuẩn | 0 VND |
| Ưu tiên | 40,000 VND |
| Hàng thoát hiểm | 120,000 VND |
| Rộng chân | 180,000 VND |

Alias nghiệp vụ cho loại ghế:

| Alias | Tên hiển thị | Phí tham chiếu |
| --- | --- | ---: |
| MIDDLE | Ghế giữa | 0 VND |
| WINDOW | Ghế cửa sổ | 20,000 VND |
| AISLE | Ghế lối đi | 20,000 VND |

Loại ghế tham chiếu theo cấu hình hiện tại:

| Loại ghế | Phí tham chiếu |
| --- | ---: |
| Ghế giữa | 0 VND |
| Ghế cửa sổ | 20,000 VND |
| Ghế lối đi | 20,000 VND |

Phí thực tế không nên tự cộng thủ công cho booking cụ thể. Hệ thống sẽ tính dựa trên ghế khách chọn, hạng vé, quyền lợi miễn phí và loại tiền tệ của booking.

Hiện tại, phụ phí chọn ghế được hỗ trợ chắc chắn cho booking VND. Nếu booking không dùng VND và ghế mới có phát sinh phụ phí, hệ thống có thể từ chối thao tác thay vì tự đổi tiền.

## Sau khi booking đã xác nhận

Với booking đã xác nhận, khách vẫn có thể đổi ghế nếu ghế mới hợp lệ và không phát sinh thêm phí.

Nếu đổi sang ghế có phụ phí sau khi booking đã xác nhận, hệ thống hiện chưa hỗ trợ thu thêm phí chọn ghế trong bước này. Cần báo khách chọn ghế miễn phí theo quyền lợi hiện có hoặc liên hệ hỗ trợ nếu cần ghế có phụ phí.

## Chọn ghế trong Manage Booking

Trong Manage Booking, khách có thể xem ghế đang chọn và đổi sang ghế khác nếu booking còn được phép chỉnh sửa.

Khi khách đổi ghế trong Manage Booking:

- Nếu ghế mới có cùng phí hoặc thấp hơn ghế cũ, hệ thống có thể lưu thay đổi và hiển thị phần chênh lệch nếu có.
- Nếu ghế mới phát sinh thêm phí, khách cần được báo trước số tiền chênh lệch và chỉ nên hoàn tất sau khi có bước xác nhận/thanh toán phù hợp.
- Nếu booking đã xác nhận mà hệ thống chưa hỗ trợ thu thêm phụ phí chọn ghế, thao tác đổi sang ghế đắt hơn sẽ bị từ chối.
- Nếu khách đang có thanh toán hoặc thay đổi khác chưa hoàn tất, hệ thống có thể chặn đổi ghế cho tới khi luồng đó kết thúc.

Không nên nói khách chắc chắn đổi được sang ghế có phí trong Manage Booking nếu chưa kiểm tra booking và luồng thanh toán hỗ trợ tại thời điểm đó.

## Khi cần tra cứu hệ thống

Cần tra cứu dữ liệu thực tế khi khách hỏi:

- Ghế nào còn trống trên chuyến bay của tôi?
- Tôi đang ngồi ghế nào?
- Tôi có đổi sang ghế cửa sổ/lối đi được không?
- Ghế này có mất phí không?
- Hạng vé của tôi có được chọn ghế miễn phí không?
- Vì sao tôi không đổi được ghế khi đang thanh toán?
- Vì sao tôi không đổi được sang ghế có phí trong Manage Booking?

Nếu chưa tra cứu được, chỉ nên giải thích chính sách chung và yêu cầu khách cung cấp mã booking hoặc thông tin chuyến bay.

## Không nên trả lời

Không nên:

- Cam kết một ghế còn trống nếu chưa kiểm tra sơ đồ ghế hiện tại.
- Tự xác nhận đã đổi ghế khi chưa có kết quả từ hệ thống.
- Tự tính phí cuối cùng cho booking cụ thể.
- Gán ghế cho em bé không có ghế riêng.
- Nói mọi hạng vé đều được chọn mọi loại ghế miễn phí.
