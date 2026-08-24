# AI Release Checklist

Use this checklist before declaring the current RAG candidate production-ready.

## Required gates

1. Run immutable live validation on the exact release candidate:
   `./gradlew :module-ai:ragLiveValidation -PragValidationRunId=<release-id>`
2. Confirm the generated report shows:
   - `135/135` validation cases PASS
   - `36/36` language stability PASS
   - the expected commit, prompt hash, docs hash, chat model, embedding model, and top-k
3. Run bounded fault/load tests on staging and record:
   - p50/p95/p99 RAG latency
   - fallback rate
   - provider failure counts
   - circuit-open and executor-queue saturation behavior
   - deterministic fault gate: `./gradlew :module-ai:ragResilienceGate`
   - HTTP load gate: `k6 run scripts/load/rag-api-load-test.js`
4. Verify security gates:
   - prompt-injection test
   - PII leakage/log scan
   - cross-account booking access test
   - secret scan
5. Verify operations gates:
   - `/actuator/health/readiness` remains `UP` when AI is degraded
   - `/actuator/health/ai` reports the AI dependency state correctly
   - metrics or Prometheus endpoint is reachable only through approved admin/private access
6. Rehearse application rollback for the deployed candidate.

The manual `.github/workflows/rag-production-gates.yml` workflow runs the deterministic fault,
actuator security, prompt-injection smoke, and bounded HTTP load gates. Keep virtual users within
the approved provider quota; the staging default is two concurrent users for two minutes.

The provider timeout is 25 seconds while the load gate still requires p95 below 25 seconds. This
allows an occasional slow provider response to complete without turning the latency SLO into a
pass; sustained slow responses still fail the release gate.

## Blocking conditions

Do not release when any of these are true:

- live validation or language stability differs from the candidate fingerprint
- fallback rate is materially above expected baseline
- circuit opens under normal expected load
- metrics expose raw prompt, answer, PNR, payment ID, passport, or token data
- actuator metrics are publicly reachable
- open P0/P1 security findings remain

## Release evidence

Keep these artifacts with the release:

- `build/reports/rag-validation/<run-id>/`
- `build/reports/rag-load-summary.json`
- staging fault/load test notes
- screenshots or exported panels for health/metrics checks
- rollback rehearsal notes
