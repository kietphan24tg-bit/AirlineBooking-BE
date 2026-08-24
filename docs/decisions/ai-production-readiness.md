# AI Production Readiness Decision

Date: August 19, 2026

## Decision

Static RAG is approved to continue into Agent development. The current candidate passed live
quality evaluation, but production deployment still requires staging load evidence for the exact
candidate being deployed.

## Why

- Deterministic validation, resilience tests, and bounded observability are already in place.
- The backend now separates application readiness from AI dependency health.
- Metrics can be exposed for operators without making them public by default.
- Live quality evidence passed `135/135`, and language stability passed `36/36`, for candidate
  `release-candidate-20260819-2`.
- Deterministic provider failure and capacity behavior is available as the repeatable
  `:module-ai:ragResilienceGate` task.
- A local two-user load smoke on August 20, 2026 passed with 18 HTTP requests, zero HTTP errors,
  zero fallback, p95 10.95 seconds, a valid response contract, and no prompt-injection leakage.
  The JSON evidence is `build/reports/rag-load-summary-local-25s-20260820.json`.
- The same smoke with a 20-second provider timeout produced two chat timeouts and 12.5% fallback.
  The default timeout is therefore 25 seconds while the release p95 threshold remains 25 seconds.

## Required before final production sign-off

1. Run bounded HTTP load against the deployed staging candidate and keep its k6 summary.
2. Confirm actuator metrics exposure is private or admin-only in the deployed environment.
3. Rehearse application rollback once for the candidate build.

## Non-blocking improvements

- Dashboard panels and alerts for fallback spikes, provider failures, and sustained open circuit
- Trace spans/correlation through route, retrieve, generate, and tool execution
- Versioned knowledge-base rollout only if document update frequency or blast radius justifies it
