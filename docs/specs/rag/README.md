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

## Chạy quality gate

Kiểm tra deterministic, không gọi dịch vụ ngoài:

```powershell
./gradlew :module-ai:test --console=plain
```

Lệnh này kiểm tra parser, chunking, prompt, fallback và replay toàn bộ checklist trên policy docs. Báo cáo offline được ghi tại `module-ai/build/reports/rag-validation-offline-report.md`.

Kiểm tra live với PGVector, embedding và Gemini thật:

```powershell
./gradlew :module-ai:ragLiveValidation --console=plain
```

Task live đọc `GEMINI_KEY`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` và `SPRING_DATASOURCE_PASSWORD` từ file `.env`. Trước khi chạy checklist, task chỉ refresh vector của 10 file trong `RagPolicyDocumentCatalog`; checklist, README và intent map không được nạp vào index.

Nếu database remote tạm thời không truy cập được và máy đã có PostgreSQL + PGVector local theo cấu hình development mặc định, chạy:

```powershell
./gradlew :module-ai:ragLiveValidation -PragValidationUseLocalDatabase=true --console=plain
```

Báo cáo live được ghi tại:

- `module-ai/build/reports/rag-validation-live-report.md`
- `module-ai/build/reports/rag-validation-live-report.json`

Mỗi kết quả gồm câu hỏi, expected behavior, route category, retrieved chunks và score, source, câu trả lời thật, fallback reason, thời gian xử lý và verdict của judge.

Checklist hiện có 135 case, trong đó 12 case `LANG-*` kiểm tra câu hỏi tiếng Anh, tiếng Pháp, tiếng Việt không dấu và câu trộn ngôn ngữ. Chạy riêng language gate bằng:

```powershell
./gradlew :module-ai:ragLiveValidation `
  -PragValidationCaseIds=LANG-001,LANG-002,LANG-003,LANG-004,LANG-005,LANG-006,LANG-007,LANG-008,LANG-009,LANG-010,LANG-011,LANG-012 `
  -PragValidationIngest=false `
  --console=plain
```

Chạy stability gate với câu hỏi nguyên bản và biến thể bỏ dấu, lặp ba lần:

```powershell
./gradlew :module-ai:ragLiveValidation `
  -PragValidationVariants=true `
  -PragValidationRepeats=3 `
  -PragValidationDelayMillis=5000 `
  --console=plain
```

Mặc định `ragValidationDelayMillis` là 5000 để không vượt free-tier 15 generate requests/phút. Chỉ giảm giá trị này khi project có quota cao hơn; không tắt retry hoặc bỏ qua case lỗi.

Quality gate chỉ đạt khi:

- 49/49 câu Must-answer bằng RAG là `PASS`.
- 15/15 câu fallback không bịa thông tin.
- Tất cả edge case có kết luận đúng hoặc yêu cầu tra cứu dữ liệu động đúng lúc.
- 31/31 câu Must-call-API được phân loại `DYNAMIC_API_REQUIRED` và RAG không khẳng định dữ liệu động.
- 12/12 câu language consistency trả lời đúng nội dung và đúng ngôn ngữ yêu cầu.
- Không còn verdict `FAIL`, `NEEDS_REVIEW` hoặc `BLOCKED`.
