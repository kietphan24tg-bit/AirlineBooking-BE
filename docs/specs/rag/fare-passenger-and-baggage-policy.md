# Chính sách hạng vé, hành khách và hành lý

Tài liệu này mô tả quyền lợi theo hạng vé, loại hành khách và hạn mức hành lý để trả lời các câu hỏi chung của hành khách.

## Hạng vé và quyền lợi chính

Quyền lợi của vé phụ thuộc hạng vé được chọn ở thời điểm tìm chuyến, đặt vé hoặc quản lý booking. Nếu hành khách hỏi quyền lợi của một vé cụ thể, cần kiểm tra booking hoặc hạng vé hiện tại trước khi trả lời chắc chắn.

Các hạng vé đang được hệ thống hỗ trợ:

| Hạng vé | Khoang | Hành lý ký gửi | Hành lý xách tay | Đổi vé | Hoàn vé | Chọn ghế | Quyền lợi nổi bật |
| --- | --- | ---: | ---: | --- | --- | --- | --- |
| Economy | Economy | 0 kg | 7 kg | Có phí | Không hoàn theo fare mặc định | Có thể chọn ghế trả phí | Suất ăn tiêu chuẩn nếu được áp dụng theo chuyến |
| Premium | Premium Economy | 46 kg | 10 kg | Có phí thấp hơn Economy | Có hoàn theo fare rule | Bao gồm ghế tiêu chuẩn | Suất ăn premium, wifi |
| Business | Business | 90 kg | 14 kg | Miễn phí đổi theo fare rule | Có hoàn theo fare rule | Bao gồm ghế extra legroom | Ưu tiên check-in/boarding, phòng chờ, suất ăn gourmet, wifi, fast track |
| First | First | Hạn mức đặc biệt của hạng First | 18 kg | Miễn phí đổi theo fare rule | Có hoàn theo fare rule | Bao gồm ghế extra legroom | Ưu tiên check-in/boarding, phòng chờ, suất ăn gourmet, wifi, fast track |

Giá vé, số ghế còn lại và giá khuyến mãi là dữ liệu thay đổi theo chuyến bay, ngày bay, thị trường, tiền tệ và khuyến mãi. Với các câu hỏi về giá cụ thể, cần tra cứu chuyến bay hoặc revalidate fare.

## Chọn ghế theo hạng vé

Quyền chọn ghế phụ thuộc hạng vé:

- Economy: có thể chọn ghế trước và có thể phát sinh phụ thu.
- Premium: được bao gồm ghế tiêu chuẩn.
- Business và First: được bao gồm ghế extra legroom.

Một số ghế vẫn có thể không khả dụng do đã được giữ, đã bán, không phù hợp với fare hoặc bị khóa bởi cấu hình chuyến bay. Chi tiết loại ghế và phụ thu nằm trong `seat-selection-policy.md`.

## Quy định đổi vé và hoàn vé theo hạng vé

Không phải mọi vé đều có cùng quyền đổi/hủy/hoàn:

- Vé có quyền đổi vẫn phải thỏa điều kiện thời gian, trạng thái booking, trạng thái vé và tình trạng check-in.
- Vé có quyền hoàn vẫn có thể bị trừ phí hoàn hoặc phụ thuộc settlement thực tế của booking.
- Economy trong cấu hình hiện tại không hoàn theo fare mặc định, nhưng vẫn có thể cần kiểm tra trường hợp đặc biệt nếu có chính sách khác hoặc lỗi vận hành từ phía hãng.
- Business và First có phí đổi bằng 0 trong fare mặc định, nhưng vẫn phải tạo quote/preview để kiểm tra điều kiện thực tế.

Phí tham chiếu theo cấu hình VND hiện tại:

| Hạng vé | Phí đổi chuyến | Điều kiện hoàn | Phí hoàn tham chiếu |
| --- | ---: | --- | ---: |
| Economy | 300,000 VND | Không hoàn theo fare mặc định | Không áp dụng |
| Premium | 150,000 VND | Có thể hoàn theo fare rule | 350,000 VND |
| Business | 0 VND | Có thể hoàn theo fare rule | 150,000 VND |
| First | 0 VND | Có thể hoàn theo fare rule | 0 VND |

Các mức phí trên là mốc tham chiếu cho thị trường VND hiện tại, không phải mức phí mặc định cho mọi thị trường. Với thị trường hoặc booking dùng tiền tệ khác, phí phải lấy theo fare/booking thực tế từ hệ thống.

Không tự quy đổi VND sang tiền tệ khác nếu chưa có dữ liệu chính thức từ kết quả tìm chuyến, fare, quote hoặc preview.

Số tiền cuối cùng khi đổi chuyến hoặc hủy vé còn phụ thuộc vé cụ thể, chênh lệch fare, dịch vụ/ghế đi kèm, trạng thái thanh toán và thời điểm thao tác. Với booking cụ thể, cần xem quote hoặc preview trước khi kết luận số tiền phải trả thêm hoặc được hoàn.

Chi tiết đổi chuyến, hủy vé và hoàn tiền nằm trong `change-cancellation-and-refund-policy.md`.

## Loại hành khách

Hệ thống hỗ trợ ba loại hành khách.

Alias nghiệp vụ thường gặp trong API, code và chatbot:

| Alias | Tên hiển thị | Quy tắc giá |
| --- | --- | --- |
| ADULT | Người lớn | 100% giá vé người lớn cùng fare |
| CHILD | Trẻ em | 75% giá vé người lớn cùng fare |
| INFANT | Em bé | 10% giá vé người lớn cùng fare |

### Người lớn

Người lớn (`ADULT`) là hành khách từ 12 tuổi trở lên tính theo ngày bay. Giá vé người lớn là 100% giá cơ sở của fare. Người lớn có ghế riêng.

### Trẻ em

Trẻ em (`CHILD`) là hành khách từ 2 tuổi đến dưới 12 tuổi tính theo ngày bay. Giá vé trẻ em bằng 75% giá vé người lớn cùng fare. Trẻ em có ghế riêng và có thể chọn ghế nếu hạng vé cho phép.

### Em bé

Em bé (`INFANT`) là hành khách dưới 2 tuổi tính theo ngày bay. Giá vé em bé bằng 10% giá vé người lớn cùng fare. Em bé không có ghế riêng và không được chọn ghế riêng.

Số em bé trong booking không được vượt quá số người lớn. Khi đổi chuyến, em bé phải được đổi cùng ít nhất một người lớn trong cùng yêu cầu thay đổi.

## Điều kiện số lượng hành khách

Mỗi booking phải có ít nhất một hành khách có ghế, tức là người lớn hoặc trẻ em. Hệ thống không cho tạo booking chỉ có em bé vì em bé không có ghế riêng.

Số lượng hành khách khai báo phải khớp với danh sách hành khách thực tế trong booking. Nếu hành khách chọn sai loại tuổi, hệ thống có thể từ chối vì loại hành khách phải phù hợp với ngày sinh tại ngày bay.

## Hành lý miễn phí

Hạn mức hành lý miễn phí phụ thuộc hạng vé:

- Hành lý xách tay là phần hành lý mang lên cabin theo hạn mức của fare.
- Hành lý ký gửi là phần hành lý gửi tại quầy theo hạn mức của fare.
- Nếu hành khách cần nhiều hành lý ký gửi hơn hạn mức miễn phí, có thể mua thêm gói hành lý trong dịch vụ bổ sung.
- Với hạng First, hệ thống đang dùng hạn mức ký gửi đặc biệt rất cao. Khi trả lời cho booking cụ thể, nên ưu tiên hạn mức hiển thị từ fare/booking thực tế thay vì suy đoán theo tên hạng vé.

Các gói hành lý mua thêm được mô tả trong `ancillary-services-policy.md`.

## Giấy tờ hành khách

Thông tin giấy tờ phụ thuộc hành trình nội địa/quốc tế, quốc tịch, độ tuổi và yêu cầu tại điểm đến. Quy định chi tiết về giấy tờ nằm trong `travel-documents-policy.md`.

Với câu hỏi về visa, nhập cảnh, hộ chiếu còn hạn bao lâu hoặc giấy tờ cho trẻ em/em bé ở một quốc gia cụ thể, cần kiểm tra nguồn chính thức hoặc kênh hỗ trợ phù hợp. Không nên trả lời chắc chắn nếu hệ thống chưa có policy chính thức cho trường hợp đó.

## Khi nào cần kiểm tra dữ liệu thực tế

Các câu hỏi sau cần dữ liệu chuyến bay hoặc booking thực tế:

- Vé của tôi được mang bao nhiêu kg hành lý?
- Hạng vé của tôi có được hoàn tiền không?
- Tôi đổi chuyến có mất phí không?
- Chuyến bay này còn ghế cho hạng vé đó không?
- Giá vé trẻ em hoặc em bé cho chuyến này là bao nhiêu?
- Tôi có được chọn ghế miễn phí không?

Với các câu hỏi này, cần tra cứu booking, fare hoặc kết quả search/revalidate mới nhất trước khi trả lời chắc chắn.
