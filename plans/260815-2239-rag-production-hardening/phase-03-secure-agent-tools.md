---
phase: 3
title: "Secure Agent Execution, HITL and Guardrails"
status: in-progress
priority: P1
effort: "10-15 days"
dependencies: [1, 2]
---

# Phase 03: Secure Agent Execution, HITL and Guardrails

## Overview

Hoàn thiện Agent từ catalog 44 tool đã có thành luồng chạy production có kiểm soát. `READ/PREVIEW` được thực thi ngay trong allowlist; `WRITE/FINANCIAL` chỉ tạo proposal và chỉ chạy sau khi user đăng nhập xác nhận. Toàn bộ input, tool selection, tool execution, tool result và response đi qua guardrail tập trung.

Phase này không sửa contract REST nghiệp vụ hiện tại và không cho LLM tự tạo endpoint, user identity, booking ownership, confirmation hay idempotency key.

## Current Baseline

Đã có:

- 44 method tool theo 10 domain class, dùng `@Tool` và `@AgentToolPolicy`.
- `ToolCatalog` và `ToolDescriptor` giữ callback, policy, method, input type và schema.
- `ToolExecutionGuard`, `AgentToolContext` và proposal verifier kiểm validation, access, confirmation và idempotency.
- Proposal persistence, HMAC, audit entity, confirm/reject API và maintenance job.
- `actions-enabled` kill switch và metrics proposal/execution cơ bản.
- Payment Agent đã persist `request_key`, dùng Stripe idempotency key, chặn payment mới khi kết quả cũ còn `UNKNOWN`, và reconciliation tra payment theo key.

Còn thiếu hoặc chưa hoàn chỉnh:

- `AgentOrchestrator` chưa chạy user-controlled tool loop và chưa tạo proposal từ tool call của LLM.
- Chưa có allowlist động theo route, auth, role, booking context và rollout.
- Snapshot/revalidation còn generic; chưa đọc authoritative domain data cho từng mutation.
- Chưa có sanitizer tập trung cho tool result, response, log và audit.
- Chưa có query/confirm rate limit và per-user concurrency production-grade.
- Exception sau atomic claim có thể để proposal ở `EXECUTING` đến khi maintenance job xử lý.
- Reconciliation payment đã tra record theo `request_key`; booking/check-in và provider lookup cho payment `UNKNOWN` vẫn cần xác minh trạng thái authoritative đầy đủ.
- Chưa có integration/security/load test đầy đủ cho proposal và Agent flow.

## Scope

### In Scope

- User-controlled tool execution bằng Spring AI.
- Dynamic tool allowlist và execution budget.
- HITL proposal lifecycle hoàn chỉnh.
- Input, prompt-injection, tool-call và output guardrails.
- PII/secret redaction và bounded payload.
- Query/confirm rate limit và concurrency control.
- Audit, metrics, feature flags và staged rollout.
- Unit, PostgreSQL integration, security, fault và load tests.

### Out of Scope

- Frontend Confirm/Reject UI.
- Natural-language “đồng ý” để confirm proposal.
- Admin/import/maintenance/webhook/refund-gateway tool.
- Tự động retry mutation hoặc tự chạy lại `EXECUTION_UNKNOWN`.
- Cho LLM quyết định ownership, amount, payment status hoặc business invariants.

## Target Architecture

```text
POST /api/chatbot/messages
  -> Request limits + input guard
  -> IntentRouter
  -> ToolAllowlistResolver(route, auth, role, rollout)
  -> LLM with user-controlled tool execution
       -> no tool: normal answer
       -> READ/PREVIEW: validate -> execute -> sanitize result -> return to LLM
       -> WRITE/FINANCIAL: validate -> authoritative snapshot -> persist PENDING
                            -> return CONFIRMATION_REQUIRED

POST /api/chatbot/proposals/{id}/confirm
  -> auth + rate limit
  -> atomic PENDING -> EXECUTING
  -> HMAC + ownership + policy + arguments verification
  -> authoritative domain revalidation
  -> invoke stored ToolDescriptor with trusted ToolContext
  -> sanitize result -> EXECUTED / FAILED / EXECUTION_UNKNOWN
  -> audit + metrics
```

## Workstream 1: Stabilize Existing HITL Foundation

### Related Files

- Modify: `module-ai/src/main/java/com/example/moduleai/confirmation/ToolConfirmationService.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/confirmation/ToolProposalConfirmationVerifier.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/confirmation/ToolConfirmationMaintenanceJob.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/tool/catalog/ToolCatalog.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/tool/catalog/ToolDescriptor.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/tool/core/ToolExecutionGuard.java`
- Modify: `database/src/main/resources/db/migration/V64__create_ai_tool_proposals.sql` only if migration has not been applied; otherwise add a new Flyway migration.

### Implementation Steps

1. Compile the current tool/HITL baseline and fix constructor, Spring bean, repository and schema validation issues before adding orchestration.
2. Make `@AgentToolPolicy` the only runtime policy source; remove or deprecate legacy `execute(ToolPolicy, ...)` after callers migrate.
3. Catch integrity, deserialization and callback exceptions after atomic claim; persist a deterministic terminal or unknown state and audit event.
4. Clear canonical arguments and sensitive snapshot details after terminal state according to retention policy.
5. Ensure reject does not set execution-only timestamps; define state transition rules centrally.
6. Validate HMAC using constant-time comparison.
7. Fail startup when actions are enabled without a valid HMAC secret or required proposal storage.

### Gate

- Existing 44-tool catalog starts successfully.
- No mutation service is called without an `EXECUTING` proposal.
- Every claimed proposal leaves `EXECUTING` deterministically or is recoverable by maintenance.

## Workstream 2: Agent Orchestration and Tool Selection

### New Components

- Create: `module-ai/src/main/java/com/example/moduleai/agent/AgentToolOrchestrator.java`
- Create: `module-ai/src/main/java/com/example/moduleai/guardrail/ToolAllowlistResolver.java`
- Create: `module-ai/src/main/java/com/example/moduleai/guardrail/AgentExecutionBudget.java`
- Create: `module-ai/src/main/java/com/example/moduleai/agent/AgentRequestContextFactory.java`

### Modified Components

- Modify: `module-ai/src/main/java/com/example/moduleai/agent/AgentOrchestrator.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/agent/IntentRouter.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/chat/ChatResponseMapper.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/dto/RagChatResponse.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/controller/ChatbotController.java`

### Implementation Steps

1. Keep `STATIC_POLICY_RAG` on the existing RAG path.
2. Route `DYNAMIC_API_REQUIRED` to a user-controlled Spring AI tool loop with automatic tool execution disabled.
3. Build trusted request context from `Authentication`; never accept userId, role, proposalId, confirmed, verifiedBookingId or idempotency key from LLM arguments.
4. Resolve the callback allowlist using route category, authentication, role, verified booking context, tool policy and rollout state.
5. Execute `READ/PREVIEW` through `ToolExecutionGuard`; sanitize structured result before returning it to the model.
6. For `WRITE/FINANCIAL`, validate arguments and create one proposal; stop the loop and return `CONFIRMATION_REQUIRED`.
7. Reject multiple mutation calls in one model response. Limit each turn to a configured number of total tool calls and model round trips.
8. Keep unknown tool resolution disabled. A model-requested tool outside the allowlist returns a safe tool-not-allowed result and audit event.

### Gate

- Static RAG behavior has no regression.
- Dynamic read request calls only an allowed tool.
- Dynamic mutation request creates a proposal and does not call the domain mutation service.
- A single turn cannot create more than one mutation proposal.

## Workstream 3: Input and Prompt-Injection Guardrails

### New Components

- Create: `module-ai/src/main/java/com/example/moduleai/config/AiAgentGuardrailProperties.java`
- Create: `module-ai/src/main/java/com/example/moduleai/guardrail/AgentInputGuard.java`
- Create: `module-ai/src/main/java/com/example/moduleai/guardrail/GuardrailDecision.java`

### Implementation Steps

1. Enforce maximum message length, session identifier length, conversation turns and request JSON size at the API boundary.
2. Reject control characters, invalid Unicode and malformed structured input; normalize whitespace without changing booking identifiers.
3. Add system policy stating that user text, RAG documents and tool output are untrusted data and cannot redefine system/tool/security rules.
4. Detect explicit attempts to reveal system prompts, credentials, tokens or bypass auth/confirmation. Return a bounded safe response and record only a reason code.
5. Do not use keyword detection as the sole security boundary. Tool allowlist, ownership checks and HITL remain authoritative.
6. Configure separate limits for anonymous RAG requests and authenticated Agent requests.

### Gate

- Prompt text cannot enable a hidden/disabled tool.
- Prompt text cannot supply trusted context fields.
- Oversized and malformed requests fail before model/provider invocation.

## Workstream 4: Domain-Safe Confirmation and Output Sanitization

### New Components

- Create: `module-ai/src/main/java/com/example/moduleai/guardrail/AgentPayloadSanitizer.java`
- Create: `module-ai/src/main/java/com/example/moduleai/confirmation/ToolConfirmationPresenter.java`
- Create: `module-ai/src/main/java/com/example/moduleai/confirmation/ToolConfirmationPresenterRegistry.java`
- Create domain presenters for booking, seat, ancillary, payment, cancellation, check-in, flight change and account.

### Implementation Steps

1. Port the useful Smart Campus safety pattern: recursively mask sensitive keys, cap string length/list items and fit JSON to a maximum byte size.
2. Apply sanitizer to tool result before LLM, proposal view before frontend, safe result/error before persistence, and structured log fields.
3. Keep domain DTO allowlists. Do not return JPA entities, provider payloads, stack traces, JWT, payment credentials or document numbers.
4. Replace generic proposal summaries with authoritative presenters that query domain services and verify ownership.
5. Revalidate at confirm time:
   - booking creation: session, fare version and expiry;
   - seat/service/flight change/cancellation: fresh preview, expected amount/token and availability;
   - payment: payable state, method and existing idempotent request;
   - check-in: window, passenger eligibility and existing boarding pass;
   - profile: proposal user equals current user.
6. On changed business data, mark proposal `FAILED` with a conflict code and require a new proposal; never silently execute revised terms.

### Gate

- Tool result and proposal response contain no configured sensitive fields.
- Lists, strings and JSON are bounded without changing financial values.
- Price, seat or booking-state changes require a new confirmation.

## Workstream 5: Rate Limits, Concurrency, Recovery and Rollout

### New Components

- Create: `module-ai/src/main/java/com/example/moduleai/guardrail/AgentRequestLimiter.java`
- Create: `module-ai/src/main/java/com/example/moduleai/confirmation/ToolExecutionReconciler.java`
- Extend: `module-ai/src/main/java/com/example/moduleai/config/AiAgentProperties.java`

### Implementation Steps

1. Add per-user and per-IP query rate limits; add stricter per-user confirm limits.
2. Add per-user concurrency slots so one account cannot occupy the full AI executor.
3. Use Redis for distributed production limits. Query may degrade fail-open only if explicitly configured; confirm must fail-closed in production.
4. Preserve atomic proposal claim and idempotency. Mutation callbacks never auto-retry.
5. Implement reconcilers that query booking/payment/check-in state for stale or unknown execution without rerunning the action.
6. Add global action kill switch, per-tool enablement, shadow-proposal mode and account allowlist canary.
7. Make unavailable storage, Redis or domain dependency produce explicit safe statuses and HTTP contracts.

### Gate

- Concurrent confirm calls execute the service once.
- Replay returns `409`; expired returns `410`; unavailable limiter/storage follows configured fail policy.
- `EXECUTION_UNKNOWN` reconciliation never creates a second mutation.

## Workstream 6: Audit, Metrics and Operational Visibility

### Related Files

- Modify: `module-ai/src/main/java/com/example/moduleai/observability/AiObservability.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/confirmation/ToolAuditEventEntity.java`
- Modify: `module-ai/src/main/java/com/example/moduleai/confirmation/ToolConfirmationService.java`
- Modify: `docs/runbooks/rag-observability.md`
- Create: `docs/runbooks/agent-tool-incident.md`

### Implementation Steps

1. Record route decision, allowlist denial, guardrail block, proposal lifecycle, confirmation replay, integrity failure, tool duration and unknown execution.
2. Use bounded tags only: tool, operation, status, route and reason code. Never tag userId, bookingId, proposalId or raw message.
3. Carry request/correlation ID through controller, model calls, tool execution and audit events.
4. Define retention jobs for proposal, safe result and audit metadata.
5. Document triage for HMAC failure, replay spike, tool failure spike, unknown execution and accidental PII exposure.

### Gate

- Operations can distinguish model, routing, guardrail, confirmation and domain-tool failures.
- Automated scans find no JWT, secret, raw canonical arguments or PII in logs/metrics/audit events.

## Test Plan

### Unit Tests

- Catalog: exactly 44 unique tools, valid descriptions/schema/policy, hidden tools excluded from LLM callbacks.
- Allowlist: route/auth/role/booking/rollout combinations.
- Guard: validation, authentication, booking verification, confirmation and idempotency.
- Sanitizer: nested maps/lists, Unicode, oversized payloads and sensitive-key masking.
- Presenter: authoritative amounts/status and PII masking.
- Execution budget: tool-call and mutation limits.

### PostgreSQL Integration Tests

- Proposal create/get/reject/confirm lifecycle.
- Atomic claim with two concurrent transactions.
- Ownership concealment: another user always receives `404`.
- HMAC tamper, arguments tamper, expiry and replay.
- Terminal cleanup and retention.
- Stale `EXECUTING` and `EXECUTION_UNKNOWN` reconciliation.

### Agent Integration Tests

- RAG question never receives mutation tools.
- Dynamic read request executes one correct read/preview tool.
- Mutation request creates proposal only.
- Confirm executes stored arguments without another LLM call.
- Model requests unknown, disabled or unauthorized tool.
- Model emits multiple tool calls or malformed arguments.

### Security and Fault Tests

- Prompt injection requesting system prompt, JWT, hidden tool or auth bypass.
- Cross-account booking/payment access.
- PII and secret leakage through input, tool result, error, logs and audit.
- DB/Redis/provider/domain timeout before and after atomic claim.
- Confirmation replay and idempotency under concurrency.

### Load Tests

- Query rate/concurrency limit under expected and burst traffic.
- Confirm endpoint burst for one user and many users.
- Executor saturation without thread starvation or unbounded queues.
- Metrics cardinality remains bounded.

## Execution Order

1. Stabilize current HITL and make the module compile/test.
2. Implement Agent orchestration with read-only tools enabled.
3. Add dynamic allowlist and execution budgets.
4. Add centralized payload sanitization and input guards.
5. Complete domain presenters and confirm-time revalidation.
6. Add distributed limits, reconciliation, audit and metrics.
7. Run security/fault/load gates.
8. Roll out shadow proposals, read tools, low-risk writes, booking mutations, then financial tools.

## Success Criteria

- [ ] Catalog has 44 unique tools; only route/auth/rollout-eligible callbacks reach the LLM.
- [ ] RAG remains unchanged for static policy questions.
- [ ] `READ/PREVIEW` executes only after validation and allowlist checks.
- [ ] `WRITE/FINANCIAL` never executes before authenticated explicit confirmation.
- [ ] Every confirmed mutation verifies user, tool, arguments, HMAC, state and idempotency.
- [ ] Two concurrent confirms cause one domain mutation.
- [ ] Cross-account access, prompt injection and trusted-context spoofing are blocked.
- [ ] Tool results, responses, logs, metrics and audit contain no PII/secrets and stay within configured bounds.
- [ ] Unknown execution is reconciled without rerunning mutation.
- [ ] Unit, module integration, PostgreSQL concurrency, security, fault and load tests pass.
- [ ] Staging shadow and canary evidence exists before financial tools are enabled.

## Risk Assessment

- Highest risk: payment, cancellation and booking changes after ambiguous timeout. Mitigation: no mutation retries, mandatory idempotency, unknown state and domain reconciliation.
- Prompt-injection detection can produce false positives. Mitigation: detection only shapes response; hard security remains allowlist, auth, ownership and HITL.
- Sanitization can remove useful data or corrupt amounts. Mitigation: typed output allowlists and truncation at collection/string boundaries, never rewriting scalar financial values.
- Distributed rate limiting adds Redis dependency. Mitigation: explicit fail-open/fail-closed policy by endpoint and environment.
- Reflection invocation can hide compile-time errors. Mitigation: startup descriptor validation plus direct integration tests for all 44 callbacks.

## Rollout

1. `actions-enabled=false`; run unit and integration tests.
2. Staging shadow proposals; confirmation endpoint does not execute.
3. Enable authenticated read/preview tools.
4. Enable low-risk writes such as profile update and check-in.
5. Enable seat/service/booking changes for allowlisted accounts.
6. Enable payment/cancellation/financial tools only after concurrency and reconciliation gates pass.
7. Canary production, monitor guardrail blocks, replay, integrity failures and unknown execution before general availability.
