# Admin API Reference

**Audience:** operators and dashboards. Every endpoint below lives on the
**admin HTTP port (default 8081)**, served by `UniAdminServer` — a separate
server so management traffic never competes with data traffic.

---

## Authentication: fail-closed bearer guard

Every endpoint **except `/admin/health`** requires:

```
Authorization: Bearer <value of -Deventmesh.admin.token>
```

| Situation | Result |
| --- | --- |
| Valid bearer | Request proceeds |
| Missing / wrong bearer | `401 {"error":"unauthorized", …}` |
| **No token configured at all** | **`503 {"error":"admin_locked", …}`** — the admin API is fail-closed by default; set `-Deventmesh.admin.token=<secret>` to enable |

The comparison is constant-time. `/admin/health` is exempt so liveness
probes work unauthenticated.

## Endpoints

### Health & metrics

| Endpoint | Method | Returns |
| --- | --- | --- |
| `/admin/health` | GET | `{"status":"UP", …}` + pending deliveries + partitions — liveness probe |
| `/admin/metrics` | GET | JSON counters: `publishCount`, `publishFailed`, `rateLimited`, `eventsDispatched`, `ackCount`, `redeliveries`, `dlqCount`, `pendingDeliveries` |
| `/metrics` | GET | **Prometheus text exposition** (`eventmesh_publish_count`, `eventmesh_publish_failed_count`, `eventmesh_rate_limited_count`, `eventmesh_dispatched_count`, `eventmesh_ack_count`, `eventmesh_redeliveries_count`, `eventmesh_dlq_count`, gauge `eventmesh_pending_deliveries`) |

### Introspection

| Endpoint | Method | Query | Returns |
| --- | --- | --- | --- |
| `/admin/subscriptions` | GET | `?topic=` | Active subscriptions `{subscriptionId, clientId, topic, mode}` |
| `/admin/offsets` | GET | `?topic=` | Distribution offsets / lag |
| `/admin/clients` | GET | `?topic=` | Online clients + pending |
| `/admin/dlq/browse` | GET | `?topic=&max=` | Dead-lettered events |

### Operations

| Endpoint | Method | Query | Effect |
| --- | --- | --- | --- |
| `/admin/client/reject` | POST | `?clientId=` | Evict a client |
| `/admin/dlq/replay` | POST | `?topic=&max=` | Replay dead-lettered events |
| `/admin/ratelimit` | GET/POST | — | Inspect / adjust per-topic rate limits |

### Connectors

| Endpoint | Method | Effect |
| --- | --- | --- |
| `/admin/connectors` | GET/POST/DELETE | Connector definition CRUD |
| `/admin/connector-workers` | GET/POST | Worker registry / heartbeats |
| `/connector/offset` | GET | Connector offset state |

Connector operations go through the runtime's `ConnectorScheduler`; a
misconfigured or unauthorized connector definition is rejected before any
JAR is loaded.

## Where the code lives

- `eventmesh-runtime/.../admin/UniAdminServer.java` — routes + token guard
- `eventmesh-runtime/.../admin/UniAdminService.java` — the cluster view
- Related guides: [Observability](observability.md) (metrics semantics,
  SLOs, alerts), [Deployment](deployment.md) (runbooks).
