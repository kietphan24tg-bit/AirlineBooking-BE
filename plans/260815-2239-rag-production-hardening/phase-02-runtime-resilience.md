---
phase: 2
title: "Runtime Resilience and Safe Fallback"
status: completed
priority: P1
effort: "3-4 days"
dependencies: [1]
---

# Phase 02: Runtime Resilience and Safe Fallback

## Overview

Giữ endpoint phản hồi an toàn khi Gemini, embedding hoặc database chậm/lỗi. LLM tự nhận diện ngôn ngữ trong luồng thành công; lỗi hạ tầng dùng fallback tiếng Anh thống nhất.

## Requirements

- Functional: safe English fallback; timeout; rate limiter; audit retry; circuit breaker; readiness phản ánh dependency.
- Non-functional: không retry storm, không retry mutation tool, không expose stack trace/provider details.

## Architecture

```text
Request -> language -> rate limiter -> retrieve -> chat provider
             |              |             |
             +--------- safe fallback <----+
                            ^
                  timeout/circuit breaker
```

Audit cho thấy Spring AI 2.0 mặc định retry tối đa 10 lần. Cấu hình production đặt `max-attempts=1`, không thêm app-level retry và để circuit breaker trả fallback ngay. Chỉ bổ sung bounded retry sau khi metric staging chứng minh lợi ích lớn hơn quota/latency cost.

## Related Code Files

- Modify: `module-ai/src/main/java/com/example/moduleai/chat/ChatService.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/chat/ChatPromptFactory.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/chat/ChatResponseMapper.java`
- Create: `module-ai/src/main/java/com/example/moduleai/chat/ResponseLanguageResolver.java`
- Create: `module-ai/src/main/java/com/example/moduleai/resilience/AiProviderGuard.java`
- Modify: `app-bootstrap/src/main/resources/application.yml`
- Modify: `module-ai/build.gradle`

## Implementation Steps

1. Để LLM tự nhận diện ngôn ngữ câu hỏi trong cùng request trả lời; không gọi language detector riêng.
2. Tạo fallback catalog tiếng Anh cho no-context, provider-error, empty-response và unsafe policy.
3. Thiết lập overall request timeout, cancellation và bounded executor/bulkhead. Google GenAI Spring properties hiện không expose connect/read timeout riêng.
4. Audit Google SDK/Spring AI retry; tránh retry lồng nhau.
5. Thêm rate limiter theo provider/project/model; cấu hình qua environment.
6. Thêm circuit breaker và bulkhead để request lỗi không chiếm hết worker threads.
7. Không retry ở tầng ứng dụng; không retry safety rejection, invalid request hoặc quality mismatch.
8. Thêm unit/integration tests cho 429, 500, 503, timeout, empty response, DB unavailable và circuit-open.
9. Kiểm tra fallback language bằng deterministic tests, không cần LLM judge runtime.

## Success Criteria

- [x] Provider/retrieval failure trả fallback tiếng Anh thống nhất.
- [x] 429 burst không tạo thêm request vượt retry budget.
- [x] Circuit breaker mở/half-open/close đúng và có metric.
- [x] Request timeout trả fallback trong latency budget; executor, queue và concurrent calls được giới hạn.
- [x] Không có app-level retry cho language mismatch khi stability vẫn đạt gate.

## Implementation Notes

- System prompt yêu cầu LLM tự nhận diện và trả lời cùng ngôn ngữ; backend không duy trì danh sách ngôn ngữ.
- `ChatFallbackCatalog` tách fallback tiếng Anh cho no-context, retrieval failure, provider failure và empty response.
- `AiProviderGuard` có rate limiter và circuit độc lập cho retrieval/chat, dùng chung timeout, bulkhead và bounded executor.
- Metric `app.ai.provider.calls` và `app.ai.provider.circuit.state` được đăng ký khi có `MeterRegistry`.
- Readiness `aiProvider` chuyển `OUT_OF_SERVICE` khi circuit retrieval hoặc chat mở.
- Unit tests dùng fake operation, không tiêu Gemini quota; kiểm tra fallback tiếng Anh, 429, 503, timeout, rate limit, circuit open/half-open và không retry.
- Full `AppBootstrapApplicationTests` pass với dummy Gemini key và `initialize-schema=false` trong test profile; production vẫn dùng PostgreSQL/PGVector thật.

## Risk Assessment

Retry chồng với SDK có thể nhân traffic lúc provider quá tải. Mitigation: audit call stack, một retry owner duy nhất, cap attempt và test bằng fake provider có request counter.
