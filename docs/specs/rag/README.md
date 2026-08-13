# RAG Policy Knowledge Base

Thư mục này chứa bộ tài liệu chính sách đã được viết lại để dùng cho chatbot AirlineBooking. Không nên nạp nguyên toàn bộ thư mục vào production index, vì trong thư mục có cả tài liệu policy, tài liệu điều phối agent và checklist kiểm thử.

## Nguồn ưu tiên khi có mâu thuẫn

1. Code nghiệp vụ hiện tại.
2. Database schema, migration và seed data.
3. Tài liệu spec trong repo.
4. Google Docs và ghi chú trao đổi.

Không nạp trực tiếp Google Docs, sprint notes, TODO, log hoặc tài liệu thiết kế thô vào production RAG. Các nguồn đó có thể chứa ghi chú kỹ thuật, phương án đang thảo luận hoặc nội dung chưa chốt.

## File nên nạp vào production RAG

Các file dưới đây là knowledge docs cho câu hỏi chính sách của người dùng:

- `booking-and-payment-policy.md`: giữ chỗ, thanh toán, PayOS QR, Stripe, Pay Later, giới hạn thời gian và trạng thái thanh toán.
- `booking-status-glossary.md`: ý nghĩa trạng thái booking, payment, ticket, seat và settlement.
- `fare-passenger-and-baggage-policy.md`: hạng vé, loại hành khách, hành lý, quyền lợi theo fare và tiền tệ.
- `seat-selection-policy.md`: chọn ghế, đổi ghế qua Manage Booking, phụ thu ghế và giới hạn VND hiện tại.
- `ancillary-services-policy.md`: hành lý mua thêm, suất ăn, bảo hiểm, ưu tiên lên máy bay và phòng chờ.
- `checkin-and-boarding-policy.md`: điều kiện check-in, cửa sổ check-in và boarding pass.
- `change-cancellation-and-refund-policy.md`: đổi chuyến, hủy vé, hoàn tiền và các khoản chênh lệch.
- `travel-documents-policy.md`: giấy tờ hành khách, loại giấy tờ được hỗ trợ và lưu ý visa/nhập cảnh.
- `pricing-and-promotions-policy.md`: cách hiểu giá vé, khuyến mãi, tiền tệ và dữ liệu giá động.

## File nạp có điều kiện

- `airport-operations-policy.md`: chỉ nên nạp vào production khi business đã chấp nhận các chính sách vận hành sân bay như giờ đến sân bay, gate, boarding và no-show. Nếu chưa chốt, chỉ dùng phần đã xác minh hoặc loại file này khỏi production index.

## File không nạp vào production RAG

Các file dưới đây không phải knowledge trả lời trực tiếp cho khách:

- `rag-scope-and-fallback.md`: dùng làm system/runtime instruction cho chatbot hoặc agent.
- `chatbot-api-intent-map.md`: dùng để router hoặc agent quyết định khi nào trả lời bằng RAG, khi nào gọi API/tool.
- `rag-validation-checklist.md`: dùng để kiểm thử regression trước khi release.
- `README.md`: mục lục và hướng dẫn vận hành bộ tài liệu.

## Định hướng chunking

- Chunk theo heading cấp 2 hoặc cấp 3.
- Mỗi chunk nên giữ đủ tiêu đề cha để không mất ngữ cảnh.
- Không cắt giữa bảng giá, bảng trạng thái hoặc danh sách điều kiện.
- Với file dài, ưu tiên chunk theo từng chủ đề nghiệp vụ thay vì chỉ cắt theo số token.
- Metadata tối thiểu: `source_file`, `policy_area`, `document_type`, `production_embed`, `last_verified_against_code`.

## Nguyên tắc trả lời production

- Câu hỏi chính sách tĩnh có thể trả lời bằng RAG.
- Câu hỏi về booking, payment, flight, seat, promotion, refund, ticket hoặc boarding pass cụ thể phải tra cứu dữ liệu động trước khi trả lời.
- Không tự tính giá, hoàn tiền, khuyến mãi, tình trạng ghế hoặc trạng thái booking nếu chưa có dữ liệu từ hệ thống.
- Không tự đổi tiền tệ. Nếu dữ liệu đang là VND thì trả lời bằng VND, trừ khi hệ thống có tỷ giá/chính sách chuyển đổi chính thức.
- Nếu tài liệu không có thông tin chính thức hoặc chính sách chưa chốt, chatbot phải nói rõ hệ thống chưa có thông tin chính thức.

## Checklist trước production

- Chỉ nạp các file thuộc nhóm production hoặc nhóm đã được chấp nhận có điều kiện.
- Loại khỏi index các file router, fallback, checklist và README.
- Chạy bộ test trong `rag-validation-checklist.md`.
- Kiểm tra lại các rule có dữ liệu động bằng API/tool thay vì chỉ dùng tài liệu tĩnh.
- Re-embed sau mỗi lần sửa policy docs hoặc thay đổi code nghiệp vụ liên quan.
