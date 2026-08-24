---
title: "RAG Production Hardening"
status: in_progress
created: 2026-08-15
priority: P1
scope: project
blockedBy: []
blocks: []
---

# RAG Production Hardening

## Overview

Đưa module AI từ baseline chất lượng đã kiểm chứng lên mức có thể vận hành production. Baseline hiện tại gồm full Vietnamese checklist 123/123 PASS, language stability 36/36 PASS, PGVector chứa policy production và IntentRouter đã phân loại nhóm must-call-API.

Plan không coi kết quả evaluation là đủ cho production. Phạm vi bổ sung gồm khả năng tái lập quality gate, resilience khi provider lỗi/quota, fallback an toàn, Agent gọi API thật có kiểm soát, observability, security và release gate.

## Decisions

- Không chạy LLM judge trên mỗi request production; judge chỉ dùng trong test/evaluation.
- Không thêm retry để sửa sai ngôn ngữ khi stability gate đang đạt 36/36.
- Phase 02 không thêm app-level retry; Spring AI `max-attempts` được đặt về 1 để tránh retry lồng và retry storm. Chỉ xem xét retry lại khi có số liệu staging chứng minh cần.
- LLM tự nhận diện và trả lời cùng ngôn ngữ câu hỏi; lỗi trước hoặc trong provider dùng fallback tiếng Anh thống nhất.
- Static policy đi qua RAG; dữ liệu booking/payment/flight/seat/refund cụ thể phải đi qua Agent tool/API.
- Không tự động re-embed mỗi lần application startup.

## Proposed SLOs

- Availability của chatbot endpoint: >= 99.5% theo tháng, không tính downtime bảo trì đã thông báo.
- RAG response p95: <= 8 giây trong điều kiện provider bình thường.
- API/tool answer p95: <= 10 giây, chưa tính thao tác confirmation của người dùng.
- Fallback response khi circuit mở: <= 2 giây.
- Không rò rỉ PII/secret trong prompt, log, metric hoặc report.
- Quality gate release: 135/135 PASS và language stability 36/36 PASS trên cấu hình production candidate.

## Phases

| Phase | Title | Priority | Effort | Depends on |
| --- | --- | --- | --- | --- |
| 01 | Reproducible quality gate | P1 | 2-3 days | - |
| 02 | Runtime resilience and safe fallback | P1 | 3-4 days | 01 |
| 03 | Secure Agent execution, HITL and guardrails | P1 | 10-15 days | 01, 02 |
| 04 | Observability and operations | P1 | 3-4 days | 02, 03 |
| 05 | Production release gate | P1 | 3-5 days | 01-04 |

## Dependencies

- Gemini/embedding project có quota đủ cho CI hoặc scheduled evaluation.
- Database production/staging hỗ trợ PGVector và migration có kiểm soát.
- Booking, payment, flight, seat, check-in API có contract và auth rõ ràng.
- Môi trường staging tách biệt dữ liệu và credential với production.

## Global Success Criteria

- [ ] Một full report 135/135 PASS được lưu ngoài `build/` và gắn với commit/config/model version.
- [ ] Language stability 36/36 PASS với pacing phù hợp quota.
- [x] Fallback kỹ thuật bằng tiếng Anh và không lộ lỗi nội bộ.
- [x] Provider 429/5xx/timeout không tạo retry storm.
- [ ] Catalog 44 tool chạy đúng allowlist; must-call-API intents gọi đúng tool hoặc yêu cầu đúng context/confirmation.
- [ ] Mọi tool bảo vệ auth, ownership, PII và idempotency.
- [ ] Dashboard/alert thể hiện latency, error, fallback, retrieval và tool outcomes.
- [ ] Load, resilience, security và application rollback rehearsal đều đạt trước production deploy.

## Non-Goals

- Hỗ trợ mọi ngôn ngữ tự nhiên ngay trong release đầu tiên.
- Cho LLM tự tạo endpoint hoặc tham số API ngoài tool schema.
- Dùng RAG để trả lời trạng thái cá nhân/dữ liệu động.
- Fine-tune model trước khi có evidence rằng prompt/RAG/tool contract không đủ.

## Rollout

1. Dev: hoàn tất phase và automated tests.
2. Staging shadow: ghi nhận route/tool proposal nhưng không thực hiện mutation.
3. Staging enabled: read tools trước, mutation tools sau confirmation.
4. Canary production: giới hạn traffic/account, theo dõi SLO và fallback.
5. General availability sau khi canary không có P1/P0 incident.
