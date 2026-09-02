# Apache EventMesh Features

> **Status:** Living document. Reflects the post-#5296 architecture-review state
> of the `develop` branch. The **capability status table** in the project README
> is the single source of truth for GA / Beta / Experimental / Legacy tags; this
> page describes *what each feature does* and *where the code lives*.
>
> See [docs/eventmesh-architecture.md](eventmesh-architecture.md) for the
> structural view (control / data / agent planes) and
> [docs/eventmesh-configuration.md](eventmesh-configuration.md) for the keys
> that turn each feature on.

This page is organized by **user intent**: a user who wants to publish events,
who wants to subscribe, who wants to wire a multi-agent system, who wants to
add a new storage backend, who wants to deploy at scale, and who wants to
operate the cluster. Each section points at the exact code locations you can
read to go deeper.

---

## 1. CloudEvents-native publish / subscribe

**What it is.** The primary user path. A Java client uses
`CloudEventsClient` to `publish` and `subscribe` over plain HTTP, with the
event body in [CloudEvents 1.0](https://cloudevents.io) format. The runtime
treats the broker as a pure write-ahead log; **all subscription semantics
live in the runtime**, not the broker.

**Why it matters.** Vendor-neutral events; no client-side knowledge of the MQ;
horizontal scale-out is a config change; reliability is owned by the runtime
through self-managed offsets + explicit ACK.

**Where the code lives.**

* Client: `eventmesh-sdks/.../cloudevents/CloudEventsClient.java`
* Walkthrough: [docs/eventmesh-client-guide.md](eventmesh-client-guide.md)
* Runtime HTTP entry: `eventmesh-runtime/.../http/UniHttpServer.java`
* Ingress: `eventmesh-runtime/.../ingress/UniIngressService.java`
* Producer (MQ-as-WAL writer):
  `eventmesh-runtime/.../protocol/producer/Producer.java`
* Subscribe: `eventmesh-runtime/.../protocol/subscribe/SubscribeProcessor.java`

**Configuration highlights.**

```
eventmesh.runtime.http.port = 10106
eventmesh.runtime.http.tls.enabled = true
eventmesh.runtime.subscriptions.maxPerClient = 1000
```

See [docs/eventmesh-configuration.md](eventmesh-configuration.md#publishsubscribe)
for the full list.

---

## 2. Multiple subscriber transports

**What it is.** A subscriber picks one of three delivery transports at
`POST /events/subscribe` time:

| Transport | Endpoint | Use case |
| --- | --- | --- |
| HTTP long-polling | `POST /events/subscribe` (default) | Best fit for batch backends, scheduled jobs, serverless |
| Server-Sent Events | `POST /events/subscribeSse` | Browser / mobile push, one-way streaming |
| WebSocket | `POST /events/subscribeWs` | Bi-directional push, low-latency interactive clients |
| Request-reply | `POST /events/request` + `POST /events/reply` | RPC-style synchronous call (built on top of the same MQ-as-WAL pipe) |

**Why it matters.** Different consumers have different latency / connection
profiles. EventMesh does not force a one-size-fits-all transport; the runtime
fan-out is shared so reliability and quota are uniform.

**Where the code lives.**

* Long-poll: `eventmesh-runtime/.../protocol/subscribe/SubscribeProcessor.java`
* SSE: `eventmesh-runtime/.../protocol/subscribe/SseProcessor.java`
* WebSocket: `eventmesh-runtime/.../http/UniWsServer.java`
* Request-reply: `eventmesh-runtime/.../protocol/subscribe/RequestReplyProcessor.java`

**Configuration highlights.**

```
eventmesh.runtime.sse.heartbeatIntervalMs = 15000
eventmesh.runtime.ws.maxFrameSize = 65536
eventmesh.runtime.request.reply.timeoutMs = 30000
```

---

## 3. Reliable delivery: at-least-once, retries, dead-letter

**What it is.** Every delivery is tracked by a `DeliveryStateStore`. On ACK
the runtime advances the offset; on failure it retries with backoff; on
terminal failure the event is moved to a `DeadLetterStore`. Operators can
inspect and replay from the dead-letter store.

**Why it matters.** When the broker is just a WAL, *the runtime* must own
delivery. The storage SPI does not know what is consumed; the runtime
decides when to advance, when to retry, and when to give up.

**Where the code lives.**

* State: `eventmesh-runtime/.../state/DeliveryStateStore.java`
* Dead-letter: `eventmesh-runtime/.../state/DeadLetterStore.java`
* Reliable dispatcher: `eventmesh-runtime/.../protocol/producer/ReliableDispatcher.java`
* Retry policy: `eventmesh-common/.../retry/RetryPolicy.java`
* Tests: `eventmesh-runtime/.../state/DeliveryStateStoreTest.java`

**Configuration highlights.**

```
eventmesh.runtime.delivery.maxRetries = 5
eventmesh.runtime.delivery.backoff.initialMs = 500
eventmesh.runtime.delivery.backoff.maxMs = 30000
eventmesh.runtime.deadletter.topic = eventmesh-deadletter
```

---

## 4. Unified state control plane (issue #5301)

**What it is.** Before #5301 the runtime had half a dozen overlapping state
APIs (`PartitionOwnership`, `MetaBackedOffsetStore`, `ClusterCoordinator`,
in-memory `TaskRegistry` for A2A). #5301 consolidated them into a small,
federated set of *capability-typed* stores:

| Level | Interface | Default backend | Purpose |
| --- | --- | --- | --- |
| L1 — local-only | `OffsetStore` | RocksDB or local KV | per-instance delivery hints |
| L2 — cluster-shared | `SubscriptionStore`, `SessionStore` | Meta store (Nacos / Consul / ETCD / ZK) | subscription / session / agent-card registry |
| L3 — durable-egress | `DeadLetterStore`, `TaskStore` | Meta store (CAS + epoch) | dead-letter, A2A task records |

**Why it matters.** One contract per concern, one TCK per contract, and the
fencing token on the L2/L3 stores makes split-brain impossible to corrupt
state. Adding a new meta backend (e.g. Eureka) is now a single
`MeshStoragePlugin` implementation that passes the TCK.

**Where the code lives.**

* Interfaces: `eventmesh-storage-plugin/eventmesh-storage-api/.../storage/MeshStoragePlugin.java`
* TCK: `eventmesh-storage-plugin/eventmesh-storage-api/.../storage/tck/MeshStoragePluginTCK.java`
* Capability flags: `eventmesh-storage-plugin/eventmesh-storage-api/.../storage/StorageCapabilities.java`
* Meta-side adapters: `eventmesh-runtime/.../cluster/ClusterSubscriptionStore.java`,
  `eventmesh-runtime/.../session/SessionRegistry.java`,
  `eventmesh-runtime/.../a2a/A2AGatewayService.java`

**Configuration highlights.**

```
eventmesh.storage.meta.backend = nacos
eventmesh.storage.meta.nacos.serverAddr = nacos:8848
eventmesh.storage.local.backend = rocksdb
eventmesh.storage.local.rocksdb.path = /var/lib/eventmesh/offset
```

---

## 5. Unified security gate (issue #5304)

**What it is.** An opt-in gate that runs on every ingress point
(`UniHttpServer`, `A2AGatewayHttpHandler`, `ConnectorScheduler`). The gate
composes the existing `FilterChain` (auth + ACL) with a per-tenant
`QuotaManager` and an `AuditSink`.

**Order of checks.** `FilterChain.invoke` → (on allow) `QuotaManager.acquire` →
`AuditSink.emit`. Rejecting at any step short-circuits downstream and the request
never touches the storage SPI.

**Why it matters.** A single, composable policy point is the only way to
guarantee that quota / audit / ACL are not forgotten in a new endpoint. The
gate is opt-in so legacy deployments keep working, but new endpoints are
expected to install it.

**Where the code lives.**

* Package: `eventmesh-runtime/.../security/gate/`
* Core: `SecurityGate.java`, `RequestContext.java`, `GateDecision.java`
* Quota: `QuotaManager.java`, `UnlimitedQuotaManager.java`, `TenantQuotaManager.java`
* Audit: `AuditSink.java`, `LoggingAuditSink.java`, `DisabledAuditSink.java`
* Wiring:
  * `eventmesh-runtime/.../http/UniHttpServer.java#withSecurityGate`
  * `eventmesh-runtime/.../a2a/A2AGatewayHttpHandler.java#withSecurityGate`
  * `eventmesh-runtime/.../connector/ConnectorScheduler.java#withSecurityGate`
* Tests: `eventmesh-runtime/.../security/gate/SecurityGateTest.java` (10 tests)

**Configuration highlights.**

```
eventmesh.security.gate.enabled = true
eventmesh.security.gate.quota.connections.perTenant = 10000
eventmesh.security.gate.quota.subscriptions.perTenant = 1000
eventmesh.security.gate.quota.throughput.bytesPerSec = 1048576
eventmesh.security.gate.audit.sink = logging
```

For the wiring contract and the full list of `RequestContext` fields, see
[docs/eventmesh-architecture.md §4](eventmesh-architecture.md#4-security-gate-issue-5304).

---

## 6. Agent-to-Agent (A2A) protocol

**What it is.** A2A is the [Agent-to-Agent](eventmesh-a2a-protocol.md) contract — a
task lifecycle (`submitted → working → completed | failed | canceled`) on
top of CloudEvents, with **durable** task records in the meta store. EventMesh
acts as the gateway: it accepts `tasks/send`, dispatches to the target agent
via a topic, and streams replies back over `/a2a/tasks/{taskId}/stream`.

**Why it matters.** A2A turns EventMesh into an **agent bus**. Synchronous
MCP / JSON-RPC 2.0 tool calls and asynchronous pub/sub share the same
storage, the same quota, the same audit, and the same reliability layer. The
A2A `taskEpoch` field is the cross-agent idempotency key.

**Where the code lives.**

* Wire spec: [docs/eventmesh-a2a-protocol.md](eventmesh-a2a-protocol.md)
* Service: `eventmesh-runtime/.../a2a/A2AGatewayService.java`
* Topic convention: `eventmesh-protocol-plugin/eventmesh-protocol-a2a/.../A2ATopicFactory.java`
  (`agentInbox(agentId)`, `gatewayResponseTopic(ns, gw, taskId)`)
* Runtime bridge: `eventmesh-runtime/.../a2a/EventMeshA2ATransport.java`
* HTTP handler: `eventmesh-runtime/.../a2a/A2AGatewayHttpHandler.java`
* Server (Netty): `eventmesh-runtime/.../a2a/A2AGatewayServer.java`
* Agent cards: `eventmesh-runtime/.../a2a/AgentCardRegistry.java` +
  `InMemoryAgentCardRegistry.java`
* Example agent: `eventmesh-agent/.../Agent.java`

**Status.** **Experimental.** The wire format and the task lifecycle are
stable; the reaper and the Meta-backed `AgentCardRegistry` are pending (D2 of
issue `#5302`).

---

## 7. Pluggable storage backends

**What it is.** Every state store (`OffsetStore`, `SubscriptionStore`, …) is
an SPI. Backends currently shipping:

* **RocketMQ** — `eventmesh-storage-plugin/eventmesh-storage-rocketmq/`
* **Kafka** — `eventmesh-storage-plugin/eventmesh-storage-kafka/`
* **Pulsar** — `eventmesh-storage-plugin/eventmesh-storage-pulsar/`
* **RabbitMQ** — `eventmesh-storage-plugin/eventmesh-storage-rabbitmq/`
* **Redis** — `eventmesh-storage-plugin/eventmesh-storage-redis/`
* **RocksDB** (local) — `eventmesh-storage-plugin/eventmesh-storage-rocksdb/`
  (L1, no meta store required)

**Why it matters.** A new backend (Pravega, AutoMQ, …) is one module that
implements `MeshStoragePlugin` and passes `MeshStoragePluginTCK`. There is no
fork; there is no runtime patch.

**Where the contract lives.**

* `eventmesh-storage-plugin/eventmesh-storage-api/.../storage/MeshStoragePlugin.java`
* `eventmesh-storage-plugin/eventmesh-storage-api/.../storage/StorageCapabilities.java`
  — capability flags (`OFFSET_TRACKING`, `PREFIX_WATCH`, `CAS`, …) so the
  runtime knows what a backend can do without a feature-detect probe.
* `eventmesh-storage-plugin/eventmesh-storage-api/.../storage/tck/MeshStoragePluginTCK.java`
  — the test every backend must pass before it can ship.

**Configuration highlights.**

```
eventmesh.storage.meta.backend = nacos
eventmesh.storage.local.backend = rocksdb
eventmesh.storage.rocketmq.namesrvAddr = rocketmq:9876
eventmesh.storage.kafka.bootstrapServers = kafka:9092
```

---

## 8. Connector ecosystem (24+ plugins)

**What it is.** Connectors copy events between EventMesh and an external
system. The runtime treats them as a first-class concern, but the connector
processes are **separate**: they run in `eventmesh-connector-runtime` and
talk to the data plane over HTTP+CloudEvents.

Plugins shipping today cover the common categories:

* **Messaging** — Kafka, RocketMQ, RabbitMQ, Pulsar, Redis
* **Database** — JDBC, MongoDB, MySQL CDC (via Canal)
* **Storage** — S3, file
* **HTTP / API** — HTTP, Knative, OpenFunction, Spring
* **ChatOps** — DingTalk, Slack, WeChat, WeCom, Lark
* **AI** — ChatGPT, MCP (Model Context Protocol)
* **Observability** — Prometheus

**Where the code lives.**

* SPI: `eventmesh-connector-plugin/eventmesh-connector-api/`
* Host: `eventmesh-connector-runtime/.../ConnectorManager.java`,
  `eventmesh-connector-runtime/.../ConnectorAdminServer.java`
* Per-connector source: `eventmesh-connector-plugin/eventmesh-connector-<name>/`
* Lifecycle: `eventmesh-runtime/.../connector/ConnectorScheduler.java`

**Configuration highlights.**

```
eventmesh.connector.runtime.workerThreads = 16
eventmesh.connector.<id>.class = org.apache.eventmesh.connector.file.FileConnector
eventmesh.connector.<id>.mode = source-sink
eventmesh.connector.<id>.topic = persistent://public/default/file-events
```

**Status.** **Beta** (capability status table). The 24 plugins are
production-usable; the new connector SPI migration (#5288) is still being
rolled out across the plugins.

---

## 9. Pluggable meta service

**What it is.** The L2/L3 state stores are backed by a meta service.
Backends currently supported: **Consul**, **Nacos**, **ETCD**, **Zookeeper**.
A new meta backend only needs to implement the `MeshStoragePlugin` SPI; the
runtime does not care.

**Why it matters.** Most enterprise environments already run one of these
for service discovery or config. EventMesh can reuse that cluster, so
operators do not run a new stateful tier just for EventMesh.

**Where the code lives.**

* `eventmesh-storage-plugin/eventmesh-storage-nacos/`
* `eventmesh-storage-plugin/eventmesh-storage-consul/`
* `eventmesh-storage-plugin/eventmesh-storage-etcd/`
* `eventmesh-storage-plugin/eventmesh-storage-zookeeper/`
* Watch: `eventmesh-runtime/.../cluster/DynamicConfigWatcher.java`

---

## 10. Filtering, transformation, schema

**What it is.** A subscriber can attach a **filter expression** at
`POST /events/subscribe` so the runtime only delivers events that match
(Cel-style expression evaluated server-side). A **transformer** can rewrite
the CloudEvent before delivery. Schemas are managed out-of-band by the
[EventMesh-catalog](https://github.com/apache/eventmesh-catalog) project
using AsyncAPI.

**Where the code lives.**

* Filter: `eventmesh-runtime/.../protocol/subscribe/filter/`
* Transformer: `eventmesh-runtime/.../protocol/subscribe/transform/`
* Schema (catalog): `eventmesh-catalog/`

---

## 11. Serverless workflow

**What it is.** EventMesh ships a workflow engine
([EventMesh-workflow](https://github.com/apache/eventmesh-workflow)) that
runs [Serverless Workflow](https://serverlessworkflow.io/) definitions over
events. A workflow can be triggered by an event, listen for follow-up
events, and call back into the runtime to publish more events — closing the
loop on event orchestration.

---

## 12. Observability and operations

**Metrics.** Prometheus exporter on
`eventmesh.runtime.metrics.port` (default 9090). Pre-built Grafana
dashboards in `eventmesh-examples/observability/`.

**Logs.** SLF4J; structured JSON layout optional
(`eventmesh.runtime.log.json = true`).

**Traces.** `RequestContext.traceContext` propagates W3C trace headers
through every plane. OpenTelemetry SDK integration is a single configuration
key.

**Health & admin.** `/health`, `/metrics`, `/admin/cluster`, and
`/admin/connectors` are exposed by the admin server. See
[docs/eventmesh-configuration.md](eventmesh-configuration.md#admin)
for the full surface.

**Runbooks.** [docs/production-readiness.md](production-readiness.md)
covers deployment topology, SLOs, and incident response.

---

## 13. Architecture-guard (issue #5305)

**What it is.** A separate Gradle module,
`eventmesh-architecture-guard`, that hosts ArchUnit rules enforcing the
layered architecture. The rules run **twice**:

1. locally on `./gradlew :eventmesh-architecture-guard:check` (30-second
   feedback loop), and
2. on every PR via `.github/workflows/architecture-guard.yml`.

A rule violation **fails the build** in both modes. The 30-second local
loop is the key value: it catches layering bugs in the same commit that
introduces them, not eight minutes later in CI.

**Where the code lives.**

* Rules: `eventmesh-architecture-guard/.../guard/ArchitectureRules.java`
* Tests: `eventmesh-architecture-guard/.../ArchitectureRulesTest.java`
* CI: `.github/workflows/architecture-guard.yml`

---

## 14. Legacy compatibility (TCP / gRPC / OpenMessaging)

**What it is.** The original EventMesh wire protocols (TCP + gRPC +
OpenMessaging) still work against the new runtime. The new
`EventMeshFrame` adaptor (`#5299`) wraps the legacy `MeshMessage` /
`OpenMessage` in the same frame type the HTTP path uses, so a single
`FilterChain` and a single `SecurityGate` cover both.

**Status.** **Legacy-compatible** (capability status table). Existing users
are not broken; new users should use the HTTP + CloudEvents path.

**Migration.** See
[docs/eventmesh-client-guide.md §1.2](eventmesh-client-guide.md)
for the drop-in replacement of the legacy `EventMeshClient` with
`CloudEventsClient`.

---

## 15. Internationalization

The runtime and the docs are English-first. The admin server accepts
`Accept-Language` for error messages. The README and most user-facing docs
have Chinese translations under `README.zh-CN.md` and module doc files
with `.zh-CN.md` suffix.

---

## Feature → module map (at a glance)

| Feature | Module | Key file |
| --- | --- | --- |
| Publish / subscribe | `eventmesh-runtime` | `http/UniHttpServer.java` |
| SSE | `eventmesh-runtime` | `protocol/subscribe/SseProcessor.java` |
| WebSocket | `eventmesh-runtime` | `http/UniWsServer.java` |
| Reliable delivery | `eventmesh-runtime` | `state/DeliveryStateStore.java` |
| State control plane | `eventmesh-runtime` + `eventmesh-storage-plugin` | `cluster/`, `state/`, `session/` |
| Security gate | `eventmesh-runtime` | `security/gate/SecurityGate.java` |
| A2A | `eventmesh-runtime` + `eventmesh-agent` | `a2a/A2AGatewayService.java` |
| Storage backends | `eventmesh-storage-plugin/*` | per-backend `MeshStoragePlugin` impl |
| Meta service | `eventmesh-storage-plugin/*` (Nacos / Consul / ETCD / ZK) | per-backend impl |
| Connectors | `eventmesh-connector-runtime` + `eventmesh-connector-plugin/*` | `ConnectorManager.java` |
| Workflow | `EventMesh-workflow` (separate repo) | — |
| Schema / catalog | `EventMesh-catalog` (separate repo) | — |
| Architecture rules | `eventmesh-architecture-guard` | `guard/ArchitectureRules.java` |
| Client SDKs | `eventmesh-sdks` | per-language package |
| Observability | `eventmesh-common` | `metrics/`, `trace/` |
| Legacy protocols | `eventmesh-runtime` | `protocol/meshmessage/`, `protocol/grpc/` |
