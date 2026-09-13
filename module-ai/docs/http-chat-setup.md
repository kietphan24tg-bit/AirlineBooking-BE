# HTTP chat setup

HTTP is supported. The chat widget uses UUID v4 IDs even when
`crypto.randomUUID()` is unavailable on an HTTP IP address.

## Verified local frontend

The current frontend runs at `http://localhost:5173` and proxies `/api` to
`http://20.196.153.39:8080` through Vite. Its local `.env` has been updated
to use this backend instead of the old Render target.

Chromium retained the backend's `Secure; HttpOnly; SameSite=None` guest cookie
through localhost. No backend cookie change or `http` profile activation is
required for this verified localhost setup. This localhost behavior must not
be generalized to an HTTP IP or non-localhost hostname.

The actual frontend issue was a response mismatch: the backend returns
`{ success, data, message }`, while chat service methods expected unwrapped DTOs.
All chat service DTO responses now unwrap successful envelopes and still accept
direct payloads. This restores answers, conversation IDs, history and proposals.

The opt-in `scripts/test-local-chat-live.mjs` passed with the real backend:
cookie retention, sending chat, refresh/history, follow-up staying in the same
conversation, foreign guest read/delete denial, and New Chat isolation. All
three answers had type `ANSWER` and no fallback. This verifies conversation
continuity, not the semantic correctness of every follow-up answer. The final
run deleted its two test-created conversations. The first failed diagnostic
run did not capture an enveloped conversation ID and could not identify it for
cleanup; no broad deletion was attempted. Later runs capture IDs before asserting.

```powershell
$env:CHAT_LOCAL_LIVE_ENABLED='true'
node scripts/test-local-chat-live.mjs
```

The live test uses model quota but does not mutate bookings or payments.

## Backend cookies

Activate the opt-in `http` profile **after** the existing environment profile:

```dotenv
SPRING_PROFILES_ACTIVE=staging,http
APP_AI_CHAT_GUEST_COOKIE_SECURE=false
APP_AI_CHAT_GUEST_COOKIE_SAME_SITE=Lax
APP_AUTH_REFRESH_COOKIE_SECURE=false
APP_AUTH_REFRESH_COOKIE_SAME_SITE=Lax
```

Keep the existing environment profile (`local`, `staging`, or `prod`); `http`
only overrides cookie transport settings. Explicit cookie environment variables
take priority over profile defaults. Cookies remain HttpOnly.

## Frontend

For local Vite development, configure the existing `BE_URL` or `VITE_API_URL`
with the backend address and restart Vite. `/api` is already proxied by Vite.
The localhost browser will receive the backend's cookie through that proxy.

For deployment, access frontend and backend on the same HTTP site (for example
the same IP with different ports), with the frontend origin allowed by CORS.
Alternatively, serve `/api/` through a frontend reverse proxy and build the
frontend without an absolute API base URL. The current frontend nginx config
serves static files only; it does not already include an API proxy.
`SameSite=Lax` cannot make cross-site fetch requests carry guest cookies.

## Verification

Run `npm run test:http-chat` in the frontend after installing Playwright Chromium
with `npx playwright install chromium`. This checks an actual insecure HTTP
browser context, message UUID format, one API submission, and rendering without
page errors. API responses are fixtures, not deployed lifecycle evidence.

After backend redeploy, use a fresh browser guest: open chat, send a message,
refresh, reopen chat, and check history. Confirm follow-up stays in the same
conversation and New Chat starts a new one. Check `chat_guest` exists in browser
storage and has HttpOnly, SameSite=Lax, and no Secure flag. Do not publish cookie
values. No server settings are changed just by adding the profile file.

## Evidence on 2026-09-13

- Chromium on `http://20.196.153.39:8080/actuator/health/readiness` reported
  `isSecureContext=false` and `typeof crypto.randomUUID === 'undefined'`.
- A fresh public guest request returned HTTP 204 and a cookie with `Secure;
  HttpOnly; SameSite=None`. The deployed backend has not yet enabled HTTP cookie
  settings. Cookie values were redacted from diagnostic output.
- Frontend typecheck, production build, and targeted ESLint passed.
- The HTTP browser regression passed: one submission with UUID v4, answer
  rendered, no uncaught page errors. A screenshot was inspected.
- `ChatGuestTokenServiceTest`: 2 tests passed.
- `HttpCookieProfileTest`: 2 tests passed (profile binding and explicit setting
  precedence); no database or model required.
- App-bootstrap Spotless Java and Checkstyle test checks passed using JDK 21.
- Antigravity reviewed the code read-only with Gemini 3.6 Flash (High). Its
  findings about cookie flags and UUID compatibility were checked directly.
  Cross-origin requests are not automatically cross-site: the same HTTP IP on
  different ports can use Lax cookies with credentialed requests and CORS.

Local browser lifecycle acceptance passed as described above. Browser acceptance
for a frontend hosted on a non-localhost HTTP origin remains separate and has
not been verified. The `http` profile described above is optional for those deployments.

## Deployed chat acceptance

Backend revision `b879071` passed the staging verification suite, PostgreSQL
integration tests, image publication and deploy workflow
([run 34744559278](https://github.com/kietphan24tg-bit/AirlineBooking-BE/actions/runs/34744559278)).

Semantic memory acceptance recalled the child's age and destination after a
refresh. A prompt injection returned `FALLBACK/PROMPT_INJECTION_ATTEMPT` before
routing; the legitimate memory remained intact. The captured test conversation
was deleted.

The initial eight-guest burst exposed HTTP 500 responses near the database
connection wait timeout. Admission control now bounds chat turns per instance
before conversation persistence. The default is two concurrent turns, reserving
capacity in the three-connection local/staging pool. Configure
`APP_AI_AGENT_MAX_CONCURRENT_CHAT_TURNS` when changing deployment capacity.
This is a per-instance bound, alongside the existing distributed owner lock.

The final eight-guest burst returned two normal answers and six controlled HTTP
503 `AGENT_CHAT_CAPACITY_EXCEEDED` responses: zero server errors, zero transport
timeouts, median 1.641 seconds and maximum 4.640 seconds. Both captured test
conversations were deleted. This verifies overload handling, not sustained
throughput or capacity for all eight guests to receive answers simultaneously.

Natural service-change acceptance initially failed three times because the
router recognized `replace service option` but missed `replace 1 of the service
option named ...`. Such requests reached RAG without Agent tools. The router now
recognizes the action and named service option independently, with a regression
case containing a quantity. Revision `65b4731` passed the full staging
verification and deploy workflow
([run 34745812494](https://github.com/kietphan24tg-bit/AirlineBooking-BE/actions/runs/34745812494)).

The service-change workflow legitimately needs five distinct tool operations
and may need additional reads to resolve names. The original five-call budget
blocked this allowed flow before its single mutation. The default tool-call
budget is now eight; the existing rule that permits at most one mutation per
turn remains enforced.

Revision `6d3b316` also raises the model-round budget to eight. The focused
natural service-change run
([34751042858](https://github.com/kietphan24tg-bit/AirlineBooking-BE/actions/runs/34751042858))
passed one of three repetitions; two returned `MODEL_BUDGET_EXCEEDED`. This
workflow is not yet accepted as reliable. A successful repetition verified
proposal preparation, foreign confirmation denial, expiry rejection, and no
settlement before confirmation. Further diagnosis must identify the repeated
tool step instead of simply raising execution budgets again.

Agent read-tool diagnostics log only round, allowlisted tool name, and bounded
outcome code. The staging mutation workflow extracts only those fields from
Coolify logs on failure, without exporting arguments, booking data, credentials,
or raw application logs.

A schema regression reproduced that all four backend-bound confirmation fields
were incorrectly required: `previewToken`, `expectedAdditionalAmount`,
`expectedRefundAmount`, and `expectedNetAmount`. They now use
`@ToolParam(required = false)`; `bookingId` and `passengers` remain required.
The regression passed after the fix. Service catalog inputs are also optional
as a pair. Tool descriptions and the service-change policy direct the model to
obtain the matching `flightId`/`fareId` from `get_manage_options` and reuse a
successful preview when preparing the proposal. Backend fare validation,
token redaction, and confirmation checks remain enforced.

Antigravity reviewed this flow read-only using Gemini 3.6 Flash (High), conversation
`02c7669f-cc52-44f3-ae29-49aeb11966ec`. Its missing-fare-ID observation was
checked against the code. Its proposed sequence of failing live tool calls was
an inference, not a captured trace; tool-step diagnostics will verify it.
