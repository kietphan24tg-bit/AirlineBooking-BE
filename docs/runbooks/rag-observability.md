# RAG Observability

The RAG runtime publishes bounded-cardinality Micrometer metrics. Raw questions, answers, PNRs,
tokens, and retrieved chunk content must not be used as metric tags.

| Metric | Tags | Meaning |
| --- | --- | --- |
| `app.ai.rag.requests` | `outcome`, `fallback_reason` | Grounded and fallback response counts |
| `app.ai.rag.duration` | none | End-to-end RAG latency |
| `app.ai.rag.retrieved.documents` | none | Retrieved document count distribution |
| `app.ai.provider.duration` | `provider` | Chat and retrieval provider latency |
| `app.ai.provider.failures` | `provider`, `reason` | Provider failures by normalized category |
| `app.ai.provider.circuit.state` | `provider` | Closed `0`, half-open `0.5`, open `1` |
| `app.ai.provider.executor.active` | none | Active provider worker threads |
| `app.ai.provider.executor.queue.size` | none | Queued provider operations |
| `app.ai.provider.executor.queue.remaining` | none | Remaining queue capacity |

Initial alerts should cover a sustained open circuit, fallback/provider-failure spikes, and a full
executor queue. Compare p95 provider duration with the configured timeout before changing pool
size.

Actuator exposure is environment-specific. Keep health public only; expose metrics through an
authenticated management endpoint or a private metrics exporter.

Current backend policy:

- `/actuator/health` and `/actuator/health/**` stay public for platform probes.
- `/actuator/metrics` and `/actuator/prometheus` are disabled from web exposure unless
  `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` explicitly contains them.
- Any non-health `/actuator/**` endpoint requires `ROLE_ADMIN`.

Recommended environment settings:

- Local debug:
  `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus`
- Staging/production with Prometheus scrape:
  `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,prometheus`
- Public internet ingress:
  do not expose `/actuator/prometheus`; route it through a private network or authenticated
  management ingress only.

Health groups:

- `/actuator/health/readiness`: application readiness only (`readinessState`, `db`)
- `/actuator/health/ai`: AI dependency health (`aiProvider`)

This split prevents a temporary AI circuit-open event from marking the whole booking backend
unready while the chatbot can still return a safe fallback.
