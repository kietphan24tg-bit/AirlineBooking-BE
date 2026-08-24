---
phase: 4
title: "Observability and Operations"
status: in_progress
priority: P1
effort: "3-4 days"
dependencies: [2, 3]
---

# Phase 04: Observability and Operations

## Overview

Tạo đủ telemetry và runbook để phát hiện retrieval degradation, provider failure, fallback spike và tool errors mà không ghi PII.

## Requirements

- Functional: metrics, structured logs, traces, dashboards, alerts, health/readiness và operational runbooks.
- Non-functional: redaction by default, bounded label cardinality, correlation ID xuyên RAG/tool flow.

## Architecture

Tận dụng Spring Boot Actuator/Micrometer đã có ở app bootstrap. Mọi request tạo correlation ID; span tách route, embed, retrieve, generation và tool execution. Log chỉ ghi case category/source IDs đã sanitize, không ghi raw booking data.

## Related Code Files

- Modify: `app-bootstrap/build.gradle`
- Modify: `app-bootstrap/src/main/resources/application.yml`
- Create: `module-ai/src/main/java/com/example/moduleai/observability/AiMetrics.java`
- Create: `module-ai/src/main/java/com/example/moduleai/observability/AiRequestObservation.java`
- Create: `docs/runbooks/ai-provider-degradation.md`
- Create: `docs/runbooks/agent-tool-incident.md`

## Implementation Steps

1. Định nghĩa metric contract: request count, latency, route category, retrieval count/score band, fallback reason, provider error, tool outcome, circuit state.
2. Thêm trace spans và correlation ID qua controller, router, retriever, model và tool.
3. Redact message, PNR, payment ID, passport và token khỏi log/metric labels.
4. Thêm health indicators cho DB/PGVector và provider readiness; liveness không phụ thuộc external provider.
5. Tạo dashboard SLO và alert cho fallback spike, 429/5xx, latency, no-context và tool failure.
6. Viết runbooks với diagnosis, mitigation, rollback và escalation owner.
7. Thiết lập retention/access policy cho logs và validation artifacts.

## Success Criteria

- [ ] Có thể phân biệt lỗi embedding, retrieval, chat, judge-test và domain tool trong dashboard.
- [ ] Alert staging được fire và acknowledge qua fault injection.
- [ ] Security scan log không tìm thấy PII/secret từ test fixtures.
- [ ] On-call có runbook cho provider outage và mutation incident.

## Implementation Progress

- [x] RAG outcome/fallback, latency and retrieved-document metrics.
- [x] Provider latency/failure, circuit and executor saturation metrics.
- [x] Bounded metric labels and initial RAG observability runbook.
- [ ] Dashboard, staging alerts, trace spans and alert fault-injection remain.

## Risk Assessment

Raw prompt logging giúp debug nhưng tạo rủi ro PII nghiêm trọng. Mitigation: không log raw prompt mặc định; chỉ bật sampled encrypted debug trong môi trường được phê duyệt và có retention ngắn.
