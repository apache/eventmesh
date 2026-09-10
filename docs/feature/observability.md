# Observability

**Audience:** operators pointing monitoring at EventMesh. Covers the metric
surface, traces, health probes, suggested SLOs, and ready-to-paste alert
rules. Metric names below are read from the running code
(`UniMetrics` + `UniAdminServer#prometheusMetrics`).

---

## Metrics

Observability is **OpenTelemetry-first**: every metric is an OTel instrument
(`eventmesh-uni` meter) exported through whatever OTel exporter the
deployment configures (OTLP, Prometheus-via-OTel, …). For zero-dependency
scraping the admin server also mirrors the same counters:

- `GET /metrics` — Prometheus text exposition (port 8081)
- `GET /admin/metrics` — JSON snapshot

| Metric (Prometheus name) | Kind | Meaning |
| --- | --- | --- |
| `eventmesh_publish_count` | counter | Events accepted for publish |
| `eventmesh_publish_failed_count` | counter | Publish failures (backend/broker) |
| `eventmesh_rate_limited_count` | counter | Publishes rejected by the token bucket (429) |
| `eventmesh_dispatched_count` | counter | Deliveries dispatched to subscribers |
| `eventmesh_ack_count` | counter | Subscriber ACKs |
| `eventmesh_redeliveries_count` | counter | Re-deliveries (timeout / nack / recovery re-dispatch) |
| `eventmesh_dlq_count` | counter | Events dead-lettered |
| `eventmesh_pending_deliveries` | gauge | In-flight (pulled-but-unACKed) deliveries |

Additional operational counters live close to their component, e.g.
`RocksDBOffsetStore.getOffsetWriteFailures()` (offset persistence failures —
see [Reliable delivery](../feature/delivery-reliability.md)).

## Traces

`RequestContext.traceContext` propagates W3C trace headers through every
plane, and `UniTrace` starts/ends OTel spans on the publish → dispatch →
ACK path, so one `traceId` follows an event from SDK to backend and back
out to the subscriber. Point an OTel collector at the deployment to
materialize end-to-end traces (spans are already instrumented).

## Health

`GET /admin/health` (token-exempt) returns `{"status":"UP"}` plus pending
deliveries and partition state — wire liveness probes at it. The Docker
image's `HEALTHCHECK` uses this endpoint.

## Suggested SLOs

| SLO | Target | Alert threshold |
| --- | --- | --- |
| Publish availability | ≥ 99.9 % | failure rate > 0.1 % for 5 min |
| Dispatch latency P99 | ≤ 500 ms | P99 > 1 s for 5 min |
| End-to-end latency P99 | ≤ 2 s | P99 > 5 s for 5 min |
| DLQ rate | ≤ 0.01 % | dlq/dispatched > 0.1 % |
| Backlog | ≤ 1000 | `pending_deliveries` > 5000 for 5 min |
| Availability | ≥ 99.9 % | `/admin/health` not UP for 3 min |

## Alert rules (Prometheus / Alertmanager)

```yaml
- alert: PublishFailureRateHigh
  expr: rate(eventmesh_publish_failed_count[5m]) / rate(eventmesh_publish_count[5m]) > 0.001
  for: 5m
  labels: { severity: critical }
- alert: DlqRateHigh
  expr: rate(eventmesh_dlq_count[5m]) / rate(eventmesh_dispatched_count[5m]) > 0.001
  for: 5m
  labels: { severity: warning }
- alert: PendingDeliveriesHigh
  expr: eventmesh_pending_deliveries > 5000
  for: 5m
  labels: { severity: warning }
- alert: EventMeshDown
  expr: up{job="eventmesh"} == 0
  for: 3m
  labels: { severity: critical }
```

## Verified test coverage (for the skeptical operator)

The behaviors the metrics reflect are exercised by the runtime's 96 test
classes, including: multi-instance exactly-once-per-partition consumption
(`MultiInstanceRocketMqIntegrationTest`), ACK-timeout redelivery + DLQ
(`AckTimeoutRedeliveryIntegrationTest`), rate limiting 429s
(`RateLimitIntegrationTest`), TLS end-to-end (`TlsIntegrationTest`), and
throughput/loss runs against real multi-broker clusters
(`LoadThroughputIntegrationTest`). Full list:
[architecture review evidence](../architecture/review/evidence.md).
