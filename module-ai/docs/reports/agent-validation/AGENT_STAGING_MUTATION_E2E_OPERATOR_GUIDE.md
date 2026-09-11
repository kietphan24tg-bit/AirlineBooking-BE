# Agent Staging Mutation E2E Validation Harness - Operator Guide

## Overview

The `AgentStagingMutationE2ETest` suite validates end-to-end Agent mutations (check-in, cancellation, seat change, service change, booking payment) against an isolated staging environment. It exercises the full pipeline from natural language prompt to tool proposal creation, concurrent confirmation, replay refusal, and database state reconciliation.

---

## Environment Variable Configuration

Environment variable **names** are public and non-secret. Environment variable **values** (such as DB passwords and tokens) are sensitive secrets. Do not commit secret values or `.env` files to source control; supply environment values dynamically using CI secrets or Coolify environment configuration.

| Variable Name | Required | Description | Sensitive Value |
| :--- | :--- | :--- | :--- |
| `AGENT_STAGING_MUTATION_ENABLED` | **Yes** | Opt-in toggle to enable staging mutation E2E tests (`true` to enable). | No |
| `STAGING_BASE_URL` | **Yes** | Base URL of the staging backend server (e.g., `https://staging-api.example.com`). | No |
| `STAGING_DB_URL` | **Yes** | PostgreSQL JDBC URL for the staging database. | Yes |
| `STAGING_DB_USER` | **Yes** | Database username. | Yes |
| `STAGING_DB_PASSWORD` | **Yes** | Database password. | Yes |

> [!CAUTION]
> This harness is destructive to fixture-owned database rows. It creates synthetic users and bookings, performs live tool mutations, and executes strict ledger cleanup. **Run strictly against isolated staging environments, never against production databases.**

---

## Fixture Isolation & Dynamic Provisioning

1. **Synthetic Test User**:
   - Each test run generates a unique email `agent-e2e-<runId>@example.test` where `<runId>` is an 8-character random UUID substring.
   - Provisioned via public auth API `/api/auth/register` and authenticated via `/api/auth/login`.

2. **Isolated Booking Fixtures**:
   - Reference data (active flights, flight fares, fare classes, seats, services) is discovered dynamically relative to current DB time.
   - Independent fixture bookings with distinct `pnr_code` values are inserted per mutation flow via JDBC.
   - Cancellation uses a zero-refund snapshot, seat change uses equal-surcharge seats, and service change uses quantities whose source and target totals are equal. These flows never call a real payment/refund gateway.
   - Payment flow creates checkout/payment rows without completing real charges.

3. **Strict Table Allowlist Ledger & Cleanup**:
   - All created database primary keys and API-generated entities are recorded in a strict table allowlist ledger (`ALLOWED_CLEANUP_TABLES`).
   - In `@AfterEach`, fixture rows are discovered again and deleted in an explicit foreign-key-safe table order using parameterized SQL.
   - Original seat states (`status`, `booking_id`, `reserved_until`) touched during testing are restored to their exact original values.

---

## Test Assertions & Reconciliations

For each of the five flows (check-in, cancellation, seat change, service change, booking payment):
1. **Natural-Language Chat Prompt**:
   - Sends English and Vietnamese prompts with `UUID` clientMessageId to `/api/chatbot/messages`.
   - Parses response envelope to extract the generated proposal ID.
2. **Concurrent Confirm & Exactly-Once Execution**:
   - Launches two concurrent HTTP `POST /api/chatbot/proposals/{proposalId}/confirm` requests using a barrier latch.
   - Asserts **exactly 1 winner** with proposal status `EXECUTED` and **1 loser** (HTTP status `409` or `429`). Asserts no `5xx` server errors.
3. **Replay Refusal**:
   - Sends a 3rd confirm request on the same proposal ID and requires `409 Conflict`; a rate-limit response does not count as replay protection.
4. **Database State Reconciliation**:
   - Queries database tables to verify exact state mutation and cardinality = 1.

---

## Exact Command to Run

To execute the opt-in staging mutation E2E harness, export the required environment variable names with your secret staging environment values:

```bash
AGENT_STAGING_MUTATION_ENABLED=true \
STAGING_BASE_URL=$STAGING_BASE_URL \
STAGING_DB_URL=$STAGING_DB_URL \
STAGING_DB_USER=$STAGING_DB_USER \
STAGING_DB_PASSWORD=$STAGING_DB_PASSWORD \
./gradlew :module-ai:agentStagingMutationE2E
```

Or on Windows PowerShell:

```powershell
$env:AGENT_STAGING_MUTATION_ENABLED="true"
$env:STAGING_BASE_URL=$env:STAGING_BASE_URL
$env:STAGING_DB_URL=$env:STAGING_DB_URL
$env:STAGING_DB_USER=$env:STAGING_DB_USER
$env:STAGING_DB_PASSWORD=$env:STAGING_DB_PASSWORD
.\gradlew.bat :module-ai:agentStagingMutationE2E
```
