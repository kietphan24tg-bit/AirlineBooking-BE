# AirlineBooking Documentation

## Mục đích

Thư mục này lưu tài liệu nghiệp vụ, API, kiến trúc và tài liệu AI/RAG của backend AirlineBooking.

## Cấu trúc

```text
docs/
├─ api/                 # Tài liệu endpoint và hợp đồng API
├─ dev/                 # Kiến trúc, schema, quy ước Git, tài liệu cho developer
├─ specs/               # Đặc tả nghiệp vụ và luồng hệ thống
│  ├─ ai/               # Định hướng chatbot, RAG, agent và AI nâng cao
│  ├─ payment/          # Luồng thanh toán, PayOS/Stripe, kiểm thử payment UI
│  ├─ rag/              # Policy production dùng cho RAG
│  └─ ui/               # Wireframe, screen flow, đặc tả UI
└─ archive/             # Tài liệu cũ hoặc tài liệu tạm, không dùng làm nguồn chính
```

## Tài liệu chính

### API

- `api/endpoints.md`: danh sách endpoint theo module.
- `api/seat-selection-and-ancillaries.md`: API chọn ghế và dịch vụ phụ trợ.

### Developer

- `dev/ARCHITECTURE.md`: kiến trúc backend hiện tại.
- `dev/schema.dbml`: schema database dạng DBML.
- `dev/airline-booking-class-diagram-detailed.drawio`: class diagram.
- `dev/version-control-git.txt`: quy ước Git/version control.

### Specs

- `specs/overview.md`: tổng quan hệ thống.
- `specs/business_rule.md`: business rules.
- `specs/payments.md`: đặc tả payment.
- `specs/state_machine_design.md`: state machine.
- `specs/use_case.md`: use cases.
- `specs/user_story.md`: user stories.

### AI

- `specs/ai/chatbot-specification.md`: định hướng chatbot, RAG và agent.
- `specs/ai/advanced-features-specification.md`: OCR, dự đoán giá, gợi ý dịch vụ.
- `specs/rag/`: bộ policy production để chunk, embed và retrieve cho chatbot RAG.

## Quy tắc sử dụng

- Tài liệu RAG production chỉ lấy từ `docs/specs/rag`.
- Tài liệu trong `docs/archive` chỉ để tham khảo lịch sử, không dùng làm nguồn chính.
- Khi có thay đổi nghiệp vụ trong code, cập nhật lại tài liệu tương ứng trong `docs/specs`.
- Không đưa log, cache, file tạm hoặc dữ liệu nhạy cảm vào `docs`.
