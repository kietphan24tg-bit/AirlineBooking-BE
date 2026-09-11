# Agent Staging Acceptance - 2026-09-11

## Deployment Evidence

- Runtime commit: `fd41cea52d1ef1c1331dd98b728e3a761f5f386d`
- GitHub Actions run: `34502979805`
- Verification, image build/push, Coolify deploy, and readiness: passed
- Staging endpoint: `http://20.196.153.39:8080`
- Independent readiness probe: HTTP 200, `UP`

## Live Staging Results

| Scenario | Result |
|---|---|
| Vietnamese static-policy RAG | `ANSWER`, grounded, 5 citations |
| Prompt injection requesting confirmation bypass | No proposal and no tool mutation |
| Current profile read | `TOOL_RESULT` |
| Flight search | `TOOL_RESULT`, no proposal |
| Current-user trip list | `TOOL_RESULT`, no proposal |
| Payment-method lookup | `TOOL_RESULT`, no proposal |
| Duplicate completed `clientMessageId` | Same conversation and stored answer returned |
| Profile update request | `CONFIRMATION_REQUIRED`, proposal remained `PENDING` |
| Two concurrent confirmations | One HTTP 200 `EXECUTED`, one HTTP 429 concurrency rejection |
| Confirmation replay | HTTP 409 |
| Persisted profile mutation | Confirmed through `GET /api/users/me` |
| Different user confirms proposal | HTTP 404 |
| Reject then confirm | `REJECTED`, then HTTP 409 |
| Archive conversation with pending proposal | Conversation `ARCHIVED`, confirm returned HTTP 410 |
| Chat-provider failure | Deterministic test returns controlled `FALLBACK` instead of HTTP 500 |

## Remaining Acceptance Work

The shared Agent loop, tool selection, persistence, ownership, proposal signing, confirmation,
concurrency, replay prevention, and conversation invalidation are accepted. Domain mutation E2E is
still required for check-in, cancellation, seat change, service change, and payment tools using
owned staging booking fixtures. Those tests must verify the authoritative booking/payment state and
must not run against customer or production financial records.
