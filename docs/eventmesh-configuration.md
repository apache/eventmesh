# EventMesh Configuration Reference

Every configuration key for the EventMesh Runtime, in one place. Defaults live in
[`eventmesh-runtime/conf/eventmesh.properties`](../eventmesh-runtime/conf/eventmesh.properties);
precedence is **system property (`-D`) > environment variable > properties file**.

> Status of each capability (GA / Beta / Experimental / Legacy): see the
> [capability status table](../README.md#capability-status).

---

## 1. Storage backend selection

| Key | Env override | Values | Default |
|---|---|---|---|
| `eventmesh.storage.type` | `EVENTMESH_STORAGE_TYPE` | `rocketmq` / `rocketmq5` / `kafka` | `kafka` |

<a name="storage-backends"></a>

## 2. Storage backends

### RocketMQ 4.x (`rocketmq`)

| Key | Default | Description |
|---|---|---|
| `eventMesh.server.rocketmq.namesrvAddr` | `127.0.0.1:9876` | Name server address list |
| `eventMesh.server.rocketmq.cluster` | `DefaultCluster` | Cluster name for topic routing |

Direct remoting connections (no `rocketmq-client` dependency on the classpath).

### RocketMQ 5.x (`rocketmq5`)

| Key | Default | Description |
|---|---|---|
| `eventMesh.server.rocketmq5.namesrvAddr` | `127.0.0.1:9876` | Name server address list |
| `eventMesh.server.rocketmq5.cluster` | `DefaultCluster` | Cluster name |
| `eventmesh.rocketmq5.lite.checkpoint.interval.ms` | `5000` | Lite-topic pull-offset checkpoint interval; `<= 0` persists only on shutdown. Bounds crash replay to at most one interval of messages |

Enables 5.x **POP consumption** and **Lite Topic** (ordered per-lite-queue messaging; see the
client guide §lite-topics).

### Kafka (`kafka`)

| Key | Default | Description |
|---|---|---|
| `eventMesh.server.kafka.namesrvAddr` | `localhost:9092` | Bootstrap servers |
| `security.protocol` | — | `SASL_PLAINTEXT` etc. for secured clusters |
| `sasl.mechanism` | — | `PLAIN`, `SCRAM-SHA-512`, ... |
| `sasl.jaas.config` | — | Full JAAS login module line |
| `ssl.*` | — | Any kafka-clients SSL key (transparently forwarded) |

EventMesh uses `assign` + `seek` + `poll` — **no consumer groups**; offsets are managed by the
Runtime and committed to its own offset store. All standard `security.*` / `sasl.*` / `ssl.*`
kafka-clients keys are passed through (see `kafka-client.properties` in the runtime `conf/`).

## 3. Runtime ports & paths

Usually set via `-D` by `bin/start.sh`; override here if needed.

| Key | Default | Description |
|---|---|---|
| `eventmesh.http.port` | `8080` | Traffic HTTP (`/events/*`, `/agent/*`, `/session/*`) |
| `eventmesh.admin.port` | `8081` | Admin HTTP (`/admin/*`, `/metrics`) |
| `eventmesh.ws.port` | `-1` (disabled) | WebSocket push port; set e.g. `8082` to enable |
| `eventmesh.offset.path` | `./data/offset` | Local offset store directory |

## 4. Security

### TLS / mTLS (transport)

TLS terminates at the HTTP server. Configure via `UniHttpServer.withTls(SSLContext)` /
`withClientAuth(boolean)` when embedding the runtime, or via the standard JVM system
properties when running from `bin/`.

### Auth / ACL / quota / audit — the security gate (#5304)

The runtime ships a unified `SecurityGate` (see
`org.apache.eventmesh.runtime.security.gate`). It is **opt-in**: when no gate is installed,
behavior is unchanged. When installed via `UniHttpServer.withSecurityGate(...)` /
`A2AGatewayHttpHandler.withSecurityGate(...)` / `ConnectorScheduler.withSecurityGate(...)`,
every request flows through one `RequestContext` and is checked in order:

1. **Authentication + ACL** — the existing filter chain (`TokenAuthFilter`, `AclFilter`,
   `SignatureVerifierFilter`, ...) with rules hot-swappable from Meta
2. **Quota** — per-tenant `QuotaManager` (connections / subscriptions / throughput / backlog);
   `TenantQuotaManager` is the in-memory default, `QuotaManager.unlimited()` disables quota
3. **Audit** — `AuditSink` SPI; `LoggingAuditSink` (default) emits one structured line per
   authorized operation

Rejections map to HTTP `401` (unauthenticated), `403` (forbidden), `429` (quota exceeded).

## 5. Rate limiting (per-topic)

`UniIngressService` enforces an optional per-topic token bucket; configure limits
programmatically via `configureTopicRateLimit(topic, capacity, permitsPerSecond)`. Exhausted
buckets make publish fail with `RateLimitedException` → HTTP `429` on the publish endpoint.
Cluster-wide limits can be inspected and adjusted at `POST /admin/ratelimit`.

## 6. Admin endpoints (port 8081)

| Endpoint | Purpose |
|---|---|
| `GET /admin/health` | liveness (also `GET /admin/health` returns `{"status":"UP"}`) |
| `GET /admin/metrics` | JSON runtime metrics |
| `GET /metrics` | Prometheus exposition |
| `GET /admin/subscriptions` | active subscriptions |
| `GET /admin/offsets` | tracked offsets |
| `GET /admin/clients` | connected clients |
| `POST /admin/client/reject` | evict a client |
| `POST /admin/dlq/replay` | replay dead-letter events |
| `GET /admin/dlq/browse` | inspect dead-letter events |
| `GET/POST /admin/ratelimit` | inspect / adjust rate limits |
| `GET/POST /admin/connectors` | connector definitions (CRUD) |
| `GET /admin/connector-workers` | connector worker registry |

## 7. Traffic endpoints (port 8080)

Full request/response shapes: see the [client guide](eventmesh-cloudevents-client-guide.md).
Summary:

| Group | Endpoints |
|---|---|
| Core pub/sub | `/events/publish`, `/events/publish-batch`, `/events/subscribe`, `/events/unsubscribe`, `/events/ack`, `/events/poll` |
| Request-reply | `/events/request`, `/events/reply` |
| Streaming push | `/events/stream` (SSE) |
| Lite topics | `/events/lite/create`, `/events/lite/publish[-bytes]`, `/events/lite/poll[-bytes]` |
| Agent control | `/agent/register`, `/agent/ready`, `/agent/heartbeat`, `/agent/unregister` |
| Sessions | `/session/open`, `/session/recommend`, `/session/close`, `/session/stream`, `/session/publish`, `/session/subscribe` |
| Legacy bridge | `/eventmesh/publish`, `/eventmesh/subscribe`, `/eventmesh/unsubscribe` (old SDK compat) |
| A2A gateway | `/a2a/tasks` family — separate port, [A2A docs](a2a-protocol/README.md) (Experimental) |

## 8. Deployment checklist

- [ ] `EVENTMESH_STORAGE_TYPE` and the backend address set consistently on every instance
- [ ] `eventmesh.offset.path` points at persistent storage (survives restarts)
- [ ] Decide the WebSocket port (default disabled)
- [ ] Production: install a `SecurityGate` (auth tokens + ACL rules + quota + audit) — see §4
- [ ] Set per-topic rate limits for known hot topics
- [ ] Point monitoring at `/metrics` (Prometheus) and alerts at `/admin/health`
