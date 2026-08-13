# Epic: AI Chatbot (Trợ lý ảo Hàng không)

## 1. Mục tiêu (Objective)
Xây dựng một trợ lý ảo thông minh (AI Chatbot) hỗ trợ khách hàng thao tác trên nền tảng AirlineBooking. Chatbot sử dụng mô hình **Hybrid UI** (Chat + Micro UI) để tăng trải nghiệm người dùng, kết hợp các công nghệ **RAG** và **AI Agent** nhằm đảm bảo tính chính xác và an toàn.

## 2. Các Tính năng Chính (Core Features)

### 2.1. Trợ lý Thông tin & Hỏi đáp (FAQ) - Kiến trúc RAG
- **Mô tả:** Trả lời tự động các câu hỏi về quy định hành lý, chính sách hoàn/hủy, giấy tờ bay.
- **Công nghệ:** Sử dụng RAG (Retrieval-Augmented Generation) kết hợp Vector Database (như pgvector, Pinecone).
- **Nguyên tắc:** AI chỉ được phép truy xuất và trả lời dựa trên file chính sách hãng bay đã được cung cấp. Báo "Không biết" nếu thông tin nằm ngoài tài liệu, tuyệt đối chống "Ảo giác AI" (Hallucination) để tránh rủi ro pháp lý.

### 2.2. Luồng Tìm kiếm & Đặt vé (Search & Booking) - Kiến trúc AI Agent + Hybrid UI
- **Mô tả:** Khách hàng gõ yêu cầu tìm kiếm bằng ngôn ngữ tự nhiên (VD: "Tìm vé đi Đà Lạt sáng mai"). Chatbot trả về danh sách chuyến bay dạng thẻ (Card/Carousel).
- **Công nghệ:** AI Agent sử dụng Function Calling (VD: `search_flight`) để phân tích ý định và lấy dữ liệu.
- **Nguyên tắc UX:** Khi khách hàng bấm chọn một chuyến bay, hệ thống phải mở ra một giao diện phụ (Modal/Popup) để khách thao tác chọn sơ đồ ghế (Seat map) và thanh toán. Tuyệt đối không bắt khách gõ chữ cho các tác vụ này.

### 2.3. Quản lý Đặt chỗ (Manage Booking Shortcut) - Kiến trúc AI Agent
- **Mô tả:** Khách hàng có thể thực hiện Check-in online, mua thêm hành lý, đổi/hủy chuyến trực tiếp thông qua việc chat.
- **Công nghệ:** AI Agent phân tích Intent và gọi API (`check_in`, `add_baggage`, `cancel_ticket`).
- **Nguyên tắc UX & Bảo mật:**
  - Check-in: Trả Boarding Pass (PDF/QR) trực tiếp vào khung chat.
  - Thanh toán/Hủy vé: Trả ra một **Redirect Link** dẫn tới trang thanh toán bảo mật, hoặc form xác nhận OTP. Không cho phép nhập thẻ tín dụng vào khung chat.

## 3. Các Tính năng Đưa ra khỏi Scope của Chatbot (Out of Scope)
- **Nhắc nhở chủ động (Push Notifications):** Tính năng chủ động nhắc nhở giờ bay, báo trễ chuyến, nhắc thời tiết sẽ **KHÔNG** thực hiện qua Web Chatbot (vì kém hiệu quả nếu khách không mở web). Tính năng này được đưa về hệ thống **Backend Notification Service** để gửi qua Email hoặc SMS.

## 4. Kịch bản Thoát hiểm (Fallback / Handoff)
- Khi AI không hiểu yêu cầu, hệ thống sẽ đưa ra các nút gợi ý thao tác nhanh (Quick Reply Buttons) hoặc điều hướng sang trang Contact (Live Agent) để tránh tạo vòng lặp AI báo lỗi liên tục.
