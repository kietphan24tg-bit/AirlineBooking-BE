---
phase: 1
title: "Reproducible Quality Gate"
status: in_progress
priority: P1
effort: "2-3 days"
dependencies: []
---

# Phase 01: Reproducible Quality Gate

## Overview

Biến bộ validation hiện tại thành release evidence có thể chạy lại, tiếp tục sau quota failure và không bị report targeted ghi đè report full.

## Requirements

- Functional: tách report theo run ID; lưu draft generation trước judge; hỗ trợ resume/judge-only; lưu full report ngoài `build/`.
- Non-functional: report không chứa secret/PII; kết quả gắn commit, model, prompt hash, docs hash, top-k và timestamp.

## Architecture

```text
Checklist -> retrieval/generation -> draft artifact -> judge -> immutable report
                                   -> resume --------^
```

Mỗi execution có deterministic ID. Generation và judging là hai stage riêng để quota judge không làm mất 135 câu trả lời đã tạo.

## Related Code Files

- Modify: `module-ai/src/test/java/com/example/moduleai/validation/RagLiveValidationTest.java`
- Modify: `module-ai/src/test/java/com/example/moduleai/validation/RagValidationReportWriter.java`
- Modify: `module-ai/src/test/java/com/example/moduleai/validation/RagValidationJudge.java`
- Modify: `module-ai/build.gradle`
- Modify: `docs/specs/rag/README.md`
- Create: `docs/reports/rag-validation/README.md`

## Implementation Steps

1. Thêm `run-id`, `report-dir`, `generation-only`, `judge-only` và `resume-from` properties.
2. Ghi draft JSON atomically sau generation, trước judge.
3. Tạo report names không ghi đè: `{timestamp}-{commit}-{scope}.json|md`.
4. Copy/publish report release candidate vào `docs/reports/rag-validation/`; giữ raw verbose artifacts ngoài Git nếu quá lớn.
5. Thêm metadata: model IDs, embedding dimensions, prompt hash, docs hash, DB target đã redacted, repeats, variants và delay.
6. Phân loại infrastructure failure riêng với quality failure; CI exit code vẫn fail cho cả hai nhưng report phải nêu nguyên nhân.
7. Thêm CI job offline cho mọi PR và scheduled/manual live job cho staging.
8. Chạy full 135 và language stability 36, lưu evidence cuối.

## Success Criteria

- [ ] Judge quota failure có thể resume mà không gọi lại generation đã hoàn tất.
- [ ] Targeted run không ghi đè full report.
- [ ] Report không chứa API key, DB password, booking verification data hoặc raw PII.
- [ ] Full gate đạt 135/135 và language stability đạt 36/36 trên cùng candidate config.
- [ ] Offline tests chạy trên PR; live tests chạy manual/scheduled với secret store.

## Implementation Progress

- [x] Run-scoped draft/report artifacts and atomic draft writes.
- [x] Generation-only, judge-only, resume-missing-executions and fingerprint checks.
- [x] Commit/model/top-k/prompt/docs metadata and manual GitHub staging workflow.
- [x] Offline artifact writer/resume tests run in the regular PR test suite.
- [ ] Final 135/135 and language stability evidence on one release candidate.

## Risk Assessment

Rủi ro lớn nhất là artifact chứa dữ liệu nhạy cảm. Mitigation: validation chỉ dùng synthetic IDs, redact config, scan secrets trước khi publish và giới hạn retention raw output.
