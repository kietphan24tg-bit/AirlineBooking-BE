# Agent Staging Acceptance Runbook

This runbook defines the exact commands, required environment variables, API validation scenarios, rollout configurations, metrics, and pass/fail criteria for staging acceptance of the AI Agent module.

## Required Environment Variables

| Variable | Description | Recommended Staging Value |
|---|---|---|
| `SPRING_AI_MODEL_CHAT` | Chat model provider | `google-genai` or `openai` |
| `GEMINI_KEY` / `GEMINI_API_KEY` / `GOOGLE_GENAI_API_KEY` | Gemini API Key for google-genai provider | `<staging-gemini-api-key>` |
| `GEMINI_MODEL` | Gemini Model selection | `gemini-3.1-flash-lite` |
| `GROQ_API_KEY` | Groq API Key for OpenAI-compatible provider | `<staging-groq-api-key>` |
| `GROQ_MODEL` | Groq Model selection | `qwen/qwen3.8-27b` |
| `APP_AI_AGENT_ACTIONS_ENABLED` | Global kill switch for Agent actions | `true` |
| `APP_AI_AGENT_CONFIRMATION_HMAC_SECRET` | HMAC signature secret (min 32 chars) | `<secure-32-byte-secret>` |
| `APP_AI_AGENT_ROLLOUT_MODE` | Rollout mode (`OFF`, `SHADOW`, `LIVE`) | `SHADOW`, then `LIVE` with a canary allowlist |
| `APP_AI_AGENT_ROLLOUT_CANARY_USER_IDS` | Authenticated users allowed during canary | Comma-separated staging user IDs |
| `APP_AI_AGENT_ROLLOUT_ENABLED_TOOLS` | Mutation tools allowed during rollout | Start with one low-risk tool |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | `jdbc:postgresql://<staging-db-host>:5432/airline_booking` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `airline_user` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `<staging-db-password>` |

## Exact Local & Staging Validation Commands

### 1. Deterministic Unit & Integration Test Gate
Executes deterministic unit, orchestrator, allowlist, and guardrail tests. Live and PostgreSQL tags are deliberately excluded from the normal `test` task:
```bash
./gradlew :module-ai:test
```

Run PostgreSQL concurrency and integrity tests separately. The default uses Testcontainers. To use an explicitly configured PostgreSQL datasource, set `AGENT_TEST_USE_EXTERNAL_DATABASE=true`; tests use random proposal IDs and scoped cleanup:
```bash
./gradlew :module-ai:postgresIntegrationTest
```

### 2. Full Module-AI Gate & Spotless Check
```bash
./gradlew :module-ai:test
./gradlew :module-ai:spotlessCheck
```

### 3. Opt-in Live Model Validation (Gemini / Groq)
Run live E2E tool selection loop using configured provider (only consumes API key when run explicitly):
```bash
# Using Gemini (google-genai) with repository key GEMINI_KEY:
SPRING_AI_MODEL_CHAT=google-genai GEMINI_KEY="<key>" ./gradlew :module-ai:agentLiveValidation

# Using Groq (openai-compatible):
SPRING_AI_MODEL_CHAT=openai GROQ_API_KEY="<key>" ./gradlew :module-ai:agentLiveValidation
```

## API Validation Scenarios

### Scenario 1: Static Policy Question (RAG Route)
- **Request**: `POST /api/chatbot/messages` with `"Quy dinh hanh ly xach tay la gi?"`
- **Expected**: Route `STATIC_POLICY_RAG`, returns grounded RAG answer, zero tool calls, answerType `ANSWER`.

### Scenario 2: Dynamic Read Request (Allowed Tool)
- **Request**: `POST /api/chatbot/messages` (Authenticated user 77) with `"Cho toi xem danh sach chuyen bay sap toi of account"`
- **Expected**: Route `DYNAMIC_API_REQUIRED`, resolves allowlist `list_my_trips`, executes read tool with authenticated `userId=77`, returns natural final answer, answerType `TOOL_RESULT`.

### Scenario 3: Dynamic Write Request (Proposal Creation)
- **Request**: `POST /api/chatbot/messages` (Authenticated user 77) with `"Doi ghe sang 14C cho booking ABC123"`
- **Expected**: Route `DYNAMIC_API_REQUIRED`, resolves allowlist `confirm_seat_change`, validates arguments, creates `PENDING` proposal, returns answerType `CONFIRMATION_REQUIRED` with proposal ID, summary, snapshot data, expiration timestamp, and confirm/reject action links. **HMAC signature is calculated and persisted server-side only and is NEVER exposed to the client or returned in API responses.** Domain mutation service is NOT invoked.

### Scenario 4: Proposal Confirmation (Exactly Once Execution)
- **Request**: `POST /api/chatbot/proposals/{proposalId}/confirm` (Authenticated user 77)
- **Expected**: Verifies HMAC, revalidates domain state, atomically transitions status from `PENDING` to `EXECUTING`, executes tool callback with trusted context, sets status to `EXECUTED`.

### Scenario 5: Confirmation Replay Prevention
- **Request**: `POST /api/chatbot/proposals/{proposalId}/confirm` (Repeated second call on EXECUTED proposal)
- **Expected**: Fails with HTTP `409` (`PROPOSAL_ALREADY_CLAIMED`), records `recordConfirmationReplay` metric, 0 additional tool invocations.

### Scenario 6: HMAC Tamper Prevention (Isolated Integration Test Only)
- **Action**: Run the PostgreSQL integration test against Testcontainers. Do not edit the shared staging database.
- **Expected**: A modified signature fails integrity verification, transitions the isolated proposal to `FAILED`, and invokes no tool.

### Scenario 7: Wrong Owner Protection
- **Request**: User 99 calls `POST /api/chatbot/proposals/{proposalId}/confirm` for user 77's proposal
- **Expected**: Returns HTTP `404` (`PROPOSAL_NOT_FOUND`), zero tool invocations.

## Rollout Modes & Fail-Closed Controls

1. **Kill Switch (`actions-enabled=false`)**:
   - `createProposal()` throws 503 (`AGENT_ACTIONS_DISABLED`).
   - `confirm()` throws 503 (`AGENT_ACTIONS_DISABLED`).
   - Allowlist for write/financial tools resolves to empty set.

2. **Shadow Mode (`rollout.mode=SHADOW`)**:
   - Model creates proposals in `PENDING` status.
   - `confirm()` endpoint rejects confirmation with 409 (`AGENT_SHADOW_MODE`).

3. **Canary Rollout (`rollout.mode=LIVE`, `canaryUserIds=[100]`)**:
   - User 100 receives mutation tools in allowlist.
   - User 77 receives empty allowlist for mutation tools.

4. **Per-Tool Enablement (`rollout.enabledTools=[confirm_seat_change]`)**:
   - Unlisted mutation tools (`confirm_cancellation`) are denied by allowlist.

5. **Incomplete Tool Defense**:
   - Tools without complete presenter/revalidator in `ToolConfirmationRegistry` are blocked.

6. **Execution Reconciliation**:
   - `reconcileUnknownExecutions()` queries authoritative payment/booking state for stale `EXECUTING` or `EXECUTION_UNKNOWN` proposals. Never retries mutation callbacks.

## Metrics & Observability Criteria

Verify these exact Micrometer meter names emit (Prometheus exposes them with dots converted to underscores and counter suffixes):
- `app.ai.agent.routes`: counter tagged by route, tool, and outcome.
- `app.ai.agent.guardrail.blocks`: counter tagged by reason code (`UNTRUSTED_TOOL_ARGUMENT`, `TOOL_NOT_ALLOWED`, `TOOL_CALL_BUDGET_EXCEEDED`, etc.).
- `app.ai.agent.allowlist.denied`: counter tagged by intent.
- `app.ai.tool.proposals`: counter tagged by tool, operation, and status.
- `app.ai.tool.execution.total`: counter tagged by tool, operation, and status.
- `app.ai.tool.execution.duration`: timer for callback execution.
- `app.ai.tool.confirmation.duration`: timer for confirmation execution.
- `app.ai.tool.integrity.failures`: counter for HMAC integrity failures.
- `app.ai.tool.confirmation.replays`: counter for replay attempts.

## Acceptance Status & Evidence Requirements

- **Local Deterministic Tests**: MUST pass 100% clean with 0 failures before deployment.
- **Staging Production Status**: DO NOT mark staging production ready until live staging environment execution logs and metrics artifacts are gathered and archived in `module-ai/docs/reports/`.
