# Epic: AI Advanced Features (Các Tính năng AI Nâng cao)

## 1. Mục tiêu (Objective)
Áp dụng các công nghệ AI và Phân tích dữ liệu (Data Analysis) nhằm tối ưu hóa trải nghiệm người dùng (UX), giảm thiểu sai sót khi nhập liệu, và gia tăng doanh thu phụ trợ (Ancillary Revenue) cho hệ thống AirlineBooking.

## 2. Chi tiết 3 Tính năng Cốt lõi (Core Features)

### 2.1. Nhận diện Giấy tờ tùy thân (AI OCR Auto-fill)
- **Nỗi đau (Pain point):** Người dùng nhập sai thông tin cá nhân (Tên, Ngày sinh, Số giấy tờ) dẫn đến rủi ro không được bay hoặc tốn phí đổi tên.
- **Mô tả hoạt động:**
  - Tại bước "Điền thông tin hành khách", hệ thống cung cấp tính năng Tải ảnh chụp CCCD/Hộ chiếu.
  - Hệ thống sử dụng công nghệ OCR (Optical Character Recognition) quét hình ảnh.
  - Bóc tách và tự động điền (Auto-fill) chuẩn xác các thông tin vào Form.
- **Yêu cầu kỹ thuật:** Tích hợp API bóc tách ảnh (như Google Cloud Vision, AWS Textract hoặc thư viện mã nguồn mở). Đảm bảo che giấu (masking) hình ảnh để tuân thủ bảo mật thông tin (Data Privacy).

### 2.2. Biểu đồ Dự đoán Giá vé (Price Prediction & Insights)
- **Nỗi đau (Pain point):** Người dùng thường lưỡng lự "Nên mua luôn bây giờ hay chờ vài bữa nữa vé giảm?".
- **Mô tả hoạt động:**
  - Tại trang Kết quả Tìm kiếm, hệ thống hiển thị Biểu đồ xu hướng giá (Trend/Line chart) cho chặng bay đó.
  - AI phân tích và đưa ra lời khuyên tự động: *"Giá đang rẻ hơn 15% so với trung bình, khuyên bạn chốt ngay!"* hoặc *"Giá có xu hướng giảm nhẹ vào tuần tới"*.
- **Yêu cầu kỹ thuật:** 
  - Xây dựng kho dữ liệu lịch sử giá vé (Time-series data).
  - Áp dụng các thuật toán dự đoán (Forecasting) cơ bản.
  - **Lưu ý Pháp lý:** Bắt buộc hiển thị dòng chữ: *"Dự đoán chỉ mang tính chất tham khảo dựa trên thuật toán quá khứ"*.

### 2.3. Gợi ý Dịch vụ theo Ngữ cảnh (Contextual Smart Upselling)
- **Nỗi đau (Pain point):** Nhồi nhét hiển thị quá nhiều dịch vụ phụ trợ (hành lý, suất ăn, bảo hiểm) khiến giao diện rườm rà, người dùng bỏ qua.
- **Mô tả hoạt động:** Thay vì bán mọi thứ, AI phân tích dữ liệu Booking để đưa ra **Lời mời đúng lúc, đúng chỗ**:
  - **Ngữ cảnh Thời tiết/Mùa:** Khách bay đến vùng lạnh (vd: Hà Nội tháng 12) -> Nhắc nhẹ: *"Trời đang lạnh, mang nhiều áo ấm nhớ mua thêm 25kg hành lý ký gửi nhé"*.
  - **Ngữ cảnh Giờ bay:** Chuyến bay sáng sớm (5-6h sáng) -> Gợi ý: *"Nạp năng lượng với Bữa Sáng Nóng Hổi giá ưu đãi"*.
  - **Ngữ cảnh Tệp khách hàng:** Trong Booking có khai báo hành khách là "Trẻ em" -> Gợi ý: *"Tránh việc gia đình bị ngồi tách xa nhau, hãy mua gói Chọn Ghế Family"*.
- **Yêu cầu kỹ thuật:** Phối hợp dữ liệu Thời gian, Hồ sơ khách hàng và (có thể) API Thời tiết. Viết bộ Rule-Engine (Luật) để trigger (kích hoạt) popup Upsell tại màn hình "Add Extras".
