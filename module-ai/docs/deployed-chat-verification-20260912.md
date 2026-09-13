# Deployed Chat Verification: 2026-09-12

## Observed Evidence

- Target: `http://20.196.153.39:8080`.
- `GET /actuator/health/readiness`: HTTP 200, `{"status":"UP"}`.
- Anonymous `GET /api/chatbot/conversations/active`: HTTP 204.
- Guest cookie attributes: `Path=/api/chatbot; Max-Age=604800; Secure; HttpOnly; SameSite=None`.
- No booking or payment mutations were performed during these probes.
- Focused local conversation tests: 14 passed, 0 failures/errors/skips via
  `:module-ai:test --tests 'com.example.moduleai.conversation.*'`.
  This excludes the opt-in PostgreSQL integration and live model suites.
- Subsequent `:module-ai:check`: successful; 238 tests, 0 failures/errors/skips,
  Checkstyle and Spotless passed. Live/deployed and PostgreSQL suites are separate.

## Blocking Browser Configuration

The public target uses plain HTTP, but its guest identity cookie requires HTTPS.
Browser guest-session continuity cannot be accepted on this target merely because
the REST endpoints are reachable. Do not disable secure cookies for production.

Preferred resolution: publish the backend through HTTPS and test from the actual
frontend with credentialed requests and an explicitly allowed CORS origin.
Cross-site cookies may also be restricted by browser privacy settings; a
same-site frontend/backend deployment or frontend API proxy avoids that dependency.

For temporary, same-site HTTP-only staging tests, the existing configuration can
be overridden explicitly (never use this as production configuration):

```dotenv
APP_AI_CHAT_GUEST_COOKIE_SECURE=false
APP_AI_CHAT_GUEST_COOKIE_SAME_SITE=Lax
```

This HTTP override does not solve cross-site frontend cookie delivery.

## Acceptance Still Required

An opt-in `deployedChatE2E` Gradle task now checks guest identity transport,
refresh, duplicate-turn history count, foreign guest read/delete denial, and
New Chat isolation. On this HTTP target, its first cookie gate failed before
any chat message was sent. Downstream assertions have NOT passed on deploy.
The Java HTTP client is an API acceptance harness, not browser/CORS validation.

For explicit HTTP API-only diagnostics, set `CHAT_DEPLOY_HTTP_API_ONLY=true`
alongside the opt-in flag and HTTP target. This changes cookie handling in the
test client only: it replays Secure cookies over HTTP. It does not change server
configuration and cannot count as browser-cookie or transport-security evidence.
Do not use real-user tokens or payment data in this mode.

The explicit HTTP API-only run subsequently passed (one lifecycle acceptance
scenario): guest active-session reuse, duplicate answer/history count, foreign
guest read/delete rejection, archive, and new conversation isolation. Cleanup
completed for both test-created conversations. This supersedes only the API
lifecycle unknowns, not the failed browser-cookie compatibility gate. Provider
fallbacks are not rejected by this lifecycle test, so it is not a semantic LLM
memory-quality test.

```powershell
$env:CHAT_DEPLOY_E2E_ENABLED='true'
$env:STAGING_BASE_URL='https://your-staging-backend'
.\gradlew.bat --no-daemon :module-ai:deployedChatE2E
```

The task is excluded from default unit tests. It creates no booking/payment,
and deletes only conversation IDs returned by its own chat requests. It may
consume model quota after the cookie gate succeeds. It does not establish
semantic follow-up correctness, proposal expiration, or prompt-injection
resistance; those require separate isolated acceptance cases.

- Browser retains guest identity across refresh and follow-up messages.
- A second guest cannot read or delete the first guest's history.
- New Chat archives the current conversation; old history remains owner-only.
- Duplicate client message IDs do not execute another turn.
- Ineligible fallback/injection content is excluded from model memory.
- Timeout behavior is covered using an injected Clock; deployed lifecycle needs
  isolated fixtures, not timestamp edits to real-user conversations.
- Single-instance RAM fallback does not establish distributed-lock safety or
  restart persistence for locks/rate limits.

The earlier five-domain staging mutation suite is separate evidence, not proof
that these browser/session acceptance criteria have passed.
