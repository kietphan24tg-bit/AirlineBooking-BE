---
phase: 5
title: "Production Release Gate"
status: pending
priority: P1
effort: "3-5 days"
dependencies: [1, 2, 3, 4]
---

# Phase 05: Production Release Gate

## Overview

Xác minh end-to-end trên staging, diễn tập provider failure và application rollback, sau đó chỉ deploy khi quality, security, performance và operations gates đều đạt.

## Requirements

- Functional: end-to-end validation cho static RAG, fallback, language, API tools và mutation confirmation.
- Non-functional: load, resilience, security, application rollback và release evidence.

## Architecture

Release pipeline chạy deterministic tests trước, sau đó staging integration/live evaluation, security tests và canary checks. Quality failure khác infrastructure failure nhưng cả hai đều chặn release.

## Related Code Files

- Modify: CI workflow files hiện có hoặc create workflow tương ứng.
- Create: `module-ai/src/test/...` integration tests cho provider/tool failure.
- Create: `docs/runbooks/ai-release-checklist.md`
- Create: `docs/decisions/ai-production-readiness.md`
- Create: `docs/reports/rag-validation/` release evidence.

## Implementation Steps

1. Chạy full 135/135 với final prompt/model/docs hash và lưu immutable report.
2. Chạy language stability 36/36 với delay/quota phù hợp.
3. Chạy 31 API intents end-to-end trên synthetic staging accounts.
4. Load test theo expected concurrency; đo p50/p95/p99, pool saturation và provider quota behavior.
5. Fault injection: DB unavailable, embedding timeout, chat 429/500/503, circuit open, tool timeout và partial domain failure.
6. Security tests: prompt injection, tool allowlist bypass, cross-account booking access, PII leakage, replay/idempotency và secret scan.
7. Canary deploy với feature flags cho Agent read tools và mutation tools.
8. Review SLO/alerts trong canary; rollback application nếu error budget hoặc security gate vi phạm.
9. Ghi ADR production readiness và sign-off owner.

## Success Criteria

- [ ] Full 135/135 và stability 36/36 PASS trên release candidate.
- [ ] 31/31 API flows đúng route/tool/context; mutation confirmation tests PASS.
- [ ] Proposed SLOs đạt dưới expected staging load.
- [ ] Không có P0/P1 security finding mở.
- [ ] Fault injection không gây retry storm, data corruption hoặc thread exhaustion.
- [ ] Application rollback được diễn tập thành công.
- [ ] Release evidence, dashboard và runbook được review bởi owner backend/AI/security.

## Risk Assessment

Staging traffic và quota có thể không đại diện production. Mitigation: quota model rõ ràng, synthetic load trong giới hạn provider, canary nhỏ và feature flags để vô hiệu hóa Agent mutation độc lập với static RAG. Versioned vector collection/reindex rollback là cải tiến tùy chọn khi quy mô hoặc tần suất cập nhật knowledge base yêu cầu.
