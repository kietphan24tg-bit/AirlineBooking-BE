# Chính sách dịch vụ bổ sung

Tài liệu này mô tả các dịch vụ bổ sung mà hành khách có thể mua thêm ngoài quyền lợi đã có trong hạng vé. Khi khách hỏi về dịch vụ đã mua, giá hiện tại, dịch vụ còn bán hay không, hoặc tổng tiền của một booking cụ thể, hệ thống cần tra cứu dữ liệu booking và catalog hiện tại trước khi trả lời.

## Nhóm dịch vụ bổ sung

Hệ thống hiện có các nhóm dịch vụ bổ sung sau:

- Hành lý ký gửi mua thêm.
- Suất ăn đặt trước.
- Bảo hiểm du lịch.
- Ưu tiên lên máy bay.
- Quyền sử dụng phòng chờ sân bay.

Alias nghiệp vụ thường gặp:

| Alias | Tên hiển thị |
| --- | --- |
| BAGGAGE | Hành lý ký gửi mua thêm |
| MEAL | Suất ăn đặt trước |
| INSURANCE | Bảo hiểm du lịch |
| PRIORITY_BOARDING | Ưu tiên lên máy bay |
| LOUNGE | Quyền sử dụng phòng chờ sân bay |

Danh sách gói dịch vụ, giá bán và trạng thái còn bán có thể thay đổi theo catalog hiện tại. Tài liệu này chỉ mô tả chính sách sử dụng; giá cụ thể cần ưu tiên dữ liệu đang có trong hệ thống.

## Quan hệ giữa hạng vé và dịch vụ bổ sung

Một số hạng vé có thể đã bao gồm sẵn quyền lợi như hành lý ký gửi, hành lý xách tay, suất ăn, ưu tiên lên máy bay, phòng chờ hoặc quyền chọn ghế.

Dịch vụ bổ sung là phần khách mua thêm ngoài quyền lợi sẵn có của hạng vé. Vì vậy, trước khi tư vấn khách có cần mua thêm hay không, cần kiểm tra hạng vé của khách đã bao gồm quyền lợi nào.

Nếu hạng vé đã bao gồm ưu tiên lên máy bay, khách không cần mua thêm dịch vụ ưu tiên lên máy bay.

Nếu hạng vé đã bao gồm phòng chờ sân bay, khách không cần mua thêm dịch vụ phòng chờ.

Với hai quyền lợi này, backend có thể chặn mua trùng khi fare đã bao gồm sẵn quyền lợi. Vì vậy cần nói rõ khách không cần mua thêm và không được cam kết hệ thống sẽ cho phép mua trùng.

Hành lý mua thêm là phần cộng thêm ngoài hạn mức hành lý miễn phí của hạng vé.

Suất ăn đặt trước là dịch vụ mua thêm. Một hạng vé có thể đã có quyền lợi suất ăn, nhưng khách vẫn có thể cần tra cứu catalog hiện tại để biết có thể đặt thêm suất ăn nào.

Bảo hiểm du lịch là dịch vụ mua thêm riêng, không được xem là quyền lợi mặc định của hạng vé.

## Khi nào có thể chọn hoặc sửa dịch vụ

Trong quá trình đặt vé ban đầu, khách có thể chọn hoặc sửa dịch vụ bổ sung khi booking vẫn còn trong thời gian giữ chỗ và chưa bước vào phiên thanh toán đang chờ xử lý.

Khi booking đang có phiên thanh toán chờ xử lý, hệ thống có thể không cho sửa dịch vụ để tránh lệch tổng tiền giữa booking và phiên thanh toán.

Sau khi booking đã được xác nhận, việc thêm hoặc đổi dịch vụ cần thực hiện qua Manage Booking. Thay đổi sau xác nhận có thể phát sinh khoản thanh toán bổ sung hoặc phần chênh lệch cần xử lý riêng.

## Quy tắc chọn dịch vụ theo hành khách

Dịch vụ được áp dụng theo từng hành khách trong booking, không mặc định áp dụng cho toàn bộ booking nếu khách chỉ chọn cho một người.

Với suất ăn, khách có thể chọn nhiều lựa chọn nếu catalog và giao diện hiện tại cho phép.

Với các nhóm dịch vụ còn lại, mỗi hành khách thường chỉ chọn một gói trong cùng nhóm dịch vụ tại một thời điểm. Ví dụ, một hành khách không nên chọn đồng thời hai gói bảo hiểm khác nhau trong cùng một lần xử lý.

Trẻ sơ sinh có thể không được áp dụng đầy đủ mọi dịch vụ như người lớn hoặc trẻ em. Khi câu hỏi liên quan đến một booking cụ thể, cần kiểm tra dữ liệu hành khách và dịch vụ hiện tại trước khi khẳng định.

## Hành lý ký gửi mua thêm

Hành lý ký gửi mua thêm dùng để tăng hạn mức hành lý ngoài phần miễn phí của hạng vé.

Catalog tham chiếu hiện tại tương ứng các gói `Extra 15kg`, `Extra 20kg`, `Extra 25kg`, `Extra 30kg`, `Extra 40kg`.
Nói ngắn gọn, các gói hành lý mua thêm hiện có là Extra 15kg, Extra 20kg, Extra 25kg, Extra 30kg và Extra 40kg.
Alias rút gọn thường dùng khi tra cứu là `Extra 15/20/25/30/40kg`; alias này chỉ cùng danh sách năm gói ở trên, không phải một gói riêng.

Ví dụ catalog tham chiếu hiện tại bằng VND:

| Gói hành lý | Giá tham chiếu |
| --- | ---: |
| Thêm 15 kg | 180,000 VND |
| Thêm 20 kg | 250,000 VND |
| Thêm 25 kg | 320,000 VND |
| Thêm 30 kg | 390,000 VND |
| Thêm 40 kg | 520,000 VND |

Giá tham chiếu không thay thế dữ liệu catalog đang hoạt động. Khi khách hỏi giá để mua thật, phải dùng giá hiện tại từ hệ thống.

## Suất ăn đặt trước

Suất ăn đặt trước là dịch vụ mua thêm để khách chọn món trước chuyến bay.

Ví dụ catalog tham chiếu hiện tại bằng VND:

| Suất ăn | Giá tham chiếu |
| --- | ---: |
| Phở bò | 90,000 VND |
| Cơm gà | 95,000 VND |
| Món chay | 95,000 VND |
| Suất gà Halal | 110,000 VND |
| Mì Ý trẻ em | 85,000 VND |
| Combo cao cấp | 150,000 VND |

Tình trạng còn bán của từng suất ăn cần kiểm tra theo catalog hiện tại.

## Bảo hiểm du lịch

Bảo hiểm du lịch là dịch vụ mua thêm cho hành trình của khách.

Ví dụ catalog tham chiếu hiện tại bằng VND:

| Gói bảo hiểm | Giá tham chiếu |
| --- | ---: |
| Nội địa cơ bản | 65,000 VND |
| Nội địa cao cấp | 99,000 VND |
| Quốc tế cơ bản | 160,000 VND |
| Quốc tế cao cấp | 245,000 VND |

Phạm vi bảo hiểm cụ thể cần dựa trên mô tả gói đang bán trong hệ thống hoặc tài liệu bảo hiểm chính thức nếu có.

## Ưu tiên lên máy bay

Ưu tiên lên máy bay giúp khách được xếp nhóm lên máy bay sớm hơn theo chính sách vận hành hiện tại.

Ví dụ catalog tham chiếu hiện tại bằng VND:

| Gói ưu tiên | Giá tham chiếu |
| --- | ---: |
| Ưu tiên tiêu chuẩn | 80,000 VND |
| Ưu tiên mở rộng | 120,000 VND |

Nếu hạng vé đã bao gồm quyền ưu tiên lên máy bay, khách không cần mua thêm dịch vụ này.

Backend có thể chặn mua trùng dịch vụ ưu tiên lên máy bay khi fare đã có sẵn quyền lợi này.

## Phòng chờ sân bay

Dịch vụ phòng chờ sân bay cho phép khách sử dụng phòng chờ nếu sân bay và hành trình hỗ trợ.

Ví dụ catalog tham chiếu hiện tại bằng VND:

| Gói phòng chờ | Giá tham chiếu |
| --- | ---: |
| Phòng chờ nội địa | 350,000 VND |
| Phòng chờ quốc tế | 550,000 VND |

Nếu hạng vé đã bao gồm quyền sử dụng phòng chờ, khách không cần mua thêm dịch vụ này.

Backend có thể chặn mua trùng dịch vụ phòng chờ khi fare đã có sẵn quyền lợi này.
Khi tư vấn trường hợp fare đã có lounge, cần nêu đủ cả hai ý: khách không cần mua thêm và backend có thể chặn giao dịch mua trùng quyền lợi.

## Thông tin cần tra cứu từ hệ thống

Các thông tin sau không nên trả lời chỉ dựa vào tài liệu tĩnh:

- Booking của khách đã mua những dịch vụ nào.
- Hạng vé cụ thể đã bao gồm quyền lợi nào.
- Dịch vụ hoặc gói dịch vụ còn bán hay đã ngừng bán.
- Giá hiện tại của từng gói dịch vụ.
- Tổng tiền dịch vụ bổ sung của một booking cụ thể.
- Việc thêm hoặc đổi dịch vụ sau khi booking đã xác nhận có cần thanh toán thêm hay không.

Nếu không tra cứu được dữ liệu hiện tại, chỉ nên trả lời chính sách chung và nói rõ rằng giá hoặc trạng thái dịch vụ cần kiểm tra lại trong hệ thống.
