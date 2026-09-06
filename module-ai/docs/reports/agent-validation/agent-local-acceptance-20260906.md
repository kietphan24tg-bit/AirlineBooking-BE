# Agent Local Acceptance - 2026-09-06

## Scope

Local production-acceptance evidence for the user-controlled Agent tool loop, trusted context, proposal confirmation, rollout filters, and PostgreSQL exactly-once claim behavior.

## Results

| Gate | Result | Evidence |
|---|---|---|
| Deterministic module tests | PASS | 204 tests, 0 failures |
| Spotless | PASS | `:module-ai:spotlessCheck` |
| Live Gemini Agent loop | PASS | 3 tests, 0 failures |
| PostgreSQL confirmation | PASS | 5 tests, 0 failures |

Live Gemini scenarios covered Vietnamese read-tool selection, an English prompt-injection attempt that still used the authoritative read tool, and a write request that created a proposal without invoking the mutation callback.

PostgreSQL scenarios covered concurrent confirmation with one successful atomic claim and one rejected contender, replay rejection without a second mutation, wrong-owner protection, expiry rejection, and HMAC tamper rejection.

## Defects Found and Fixed

- Tool calls outside the route allowlist were not rejected immediately and could end as a misleading tool-budget error. They now fail closed with `TOOL_NOT_ALLOWED`.
- LLM arguments could contain trusted-context-looking keys. Mutation and read paths now reject them with `UNTRUSTED_TOOL_ARGUMENT` before proposal creation or callback execution.
- Agent tool options replaced the configured Gemini model with the Spring AI builder default. The orchestrator now preserves `spring.ai.google.genai.chat.options.model` when attaching tool callbacks.
- Raw tool arguments and stack traces were written to stderr on processing failures. Those unsafe logs were removed and replaced by bounded reason-code metrics.
- Rollout tests and documentation incorrectly treated canary as an enum mode. Canary is now documented and tested as `LIVE` plus `canaryUserIds`.

## Remaining External Acceptance

This report proves local code and configured provider/database behavior. Production readiness still requires deploying this exact revision to staging, running authenticated API scenarios, observing metrics under load, and completing shadow then canary rollout without unacceptable errors or `EXECUTION_UNKNOWN` growth.
